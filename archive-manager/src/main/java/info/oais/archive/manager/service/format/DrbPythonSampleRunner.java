package info.oais.archive.manager.service.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs a generated drb-python driver (see {@link DrbGenerator#pythonDriverPackage})
 * against a sample data file, in a separate Python process, so RepInfo Tools
 * can show what drb-python itself decodes -- the drb-python counterpart of
 * {@link DfdlSampleRunner}.
 *
 * <p>Needs a Python 3 with drb-python installed ({@code pip install drb}) on
 * the machine running this app. The interpreter is
 * {@code archive.drb-python.executable} if set, otherwise the first of
 * {@code python3}/{@code python} that can import drb. The driver module is
 * loaded straight from a temporary file and applied to the sample through
 * drb's own file node -- nothing is pip-installed, and drb's auto-detection
 * is bypassed so another installed driver can't claim the sample instead.
 * The generated module holds user-entered text only as escaped string
 * literals (see {@link DrbGenerator#quotedLiteral}), and the helper script
 * run alongside it is fixed.
 */
@Component
public class DrbPythonSampleRunner {

    private static final long TIMEOUT_SECONDS = 60;
    private static final long PROBE_TIMEOUT_SECONDS = 20;
    /** How long a failed interpreter probe is remembered, so installing drb doesn't need an app restart. */
    private static final long NEGATIVE_CACHE_MILLIS = 60_000;

    private static final String RUNNER_SCRIPT = """
            import importlib.util, json, sys
            from drb.drivers.file.file import DrbFileFactory

            driver_path, sample_path, factory_name, max_rows = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
            rows = []

            def describe(value):
                if value is None:
                    return ""
                if isinstance(value, (bytes, bytearray)):
                    return str(len(value)) + " byte(s)"
                return str(value)

            def walk(node, depth):
                if len(rows) >= max_rows:
                    return False
                children = list(node)
                attrs = node.attributes if isinstance(node.attributes, dict) else {}
                offset, length = attrs.get(("offset", None)), attrs.get(("length", None))
                position = ""
                if isinstance(offset, int) and isinstance(length, int) and length > 0:
                    position = "bytes " + str(offset) + "\\u2013" + str(offset + length - 1)
                rows.append({"depth": depth, "name": node.name, "kind": "COMPOSITE" if children else "LEAF",
                             "value": "" if children else describe(node.value), "byteRange": position})
                return all(walk(child, depth + 1) for child in children)

            try:
                spec = importlib.util.spec_from_file_location("generated_drb_driver", driver_path)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                root = getattr(module, factory_name)().create(DrbFileFactory().create(sample_path))
                complete = walk(root, 0)
                print(json.dumps({"rows": rows, "truncated": not complete, "error": None}))
            except Exception as ex:
                print(json.dumps({"rows": [], "truncated": False, "error": type(ex).__name__ + ": " + str(ex)}))
            """;

    private final String configuredExecutable;
    private final ObjectMapper json = new ObjectMapper();
    private volatile String resolvedExecutable;
    private volatile String resolvedVersion;
    private volatile long lastFailedProbe;

    public DrbPythonSampleRunner(@Value("${archive.drb-python.executable:}") String configuredExecutable) {
        this.configuredExecutable = configuredExecutable == null ? "" : configuredExecutable.strip();
    }

    /** @return the installed drb-python version, if a usable interpreter was found. */
    public Optional<String> drbVersion() {
        if (resolvedExecutable != null) {
            return Optional.of(resolvedVersion);
        }
        if (System.currentTimeMillis() - lastFailedProbe < NEGATIVE_CACHE_MILLIS) {
            return Optional.empty();
        }
        List<String> candidates = configuredExecutable.isEmpty() ? List.of("python3", "python") : List.of(configuredExecutable);
        for (String candidate : candidates) {
            Optional<String> version = probe(candidate);
            if (version.isPresent()) {
                resolvedVersion = version.get();
                resolvedExecutable = candidate;
                return version;
            }
        }
        lastFailedProbe = System.currentTimeMillis();
        return Optional.empty();
    }

    /**
     * @param driverModule the generated driver module source ({@link DrbGenerator#generate} with PYTHON)
     * @param factoryName  the module's {@code DrbFactory} subclass to apply
     */
    public SampleDecodeResult run(String driverModule, String factoryName, byte[] sample) {
        return run(driverModule, factoryName, sample, null);
    }

    /** @param fileName the sample's original name, shown as the decoded tree's root; sanitized before use */
    public SampleDecodeResult run(String driverModule, String factoryName, byte[] sample, String fileName) {
        if (drbVersion().isEmpty()) {
            return SampleDecodeResult.failure(notAvailableMessage());
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("repinfo-tools-drb-");
            Path driver = Files.writeString(dir.resolve("generated_drb_driver.py"), driverModule, StandardCharsets.UTF_8);
            Path runner = Files.writeString(dir.resolve("run_sample.py"), RUNNER_SCRIPT, StandardCharsets.UTF_8);
            Path samplePath = Files.write(dir.resolve(safeSampleName(fileName)), sample);
            Path out = dir.resolve("out.json");
            Path err = dir.resolve("err.txt");
            Process process = new ProcessBuilder(resolvedExecutable, "-X", "utf8", "-W", "ignore", runner.toString(),
                    driver.toString(), samplePath.toString(), factoryName, String.valueOf(SampleDecodeResult.MAX_ROWS))
                    .directory(dir.toFile())
                    .redirectOutput(out.toFile())
                    .redirectError(err.toFile())
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return SampleDecodeResult.failure("drb-python did not finish within " + TIMEOUT_SECONDS + " seconds.");
            }
            String stdout = Files.readString(out, StandardCharsets.UTF_8).strip();
            if (stdout.isEmpty()) {
                return SampleDecodeResult.failure("drb-python exited with code " + process.exitValue() + ":\n"
                        + Files.readString(err, StandardCharsets.UTF_8).strip());
            }
            return toResult(json.readTree(stdout));
        } catch (IOException e) {
            return SampleDecodeResult.failure("Could not run drb-python: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SampleDecodeResult.failure("Interrupted while waiting for drb-python.");
        } finally {
            deleteQuietly(dir);
        }
    }

    public String notAvailableMessage() {
        return configuredExecutable.isEmpty()
                ? "No Python with drb-python was found (tried python3 and python). Install it with "
                        + "\"pip install drb\", or set archive.drb-python.executable to a Python that has it."
                : "The configured archive.drb-python.executable (" + configuredExecutable
                        + ") could not import drb-python. Install it there with \"pip install drb\".";
    }

    private SampleDecodeResult toResult(JsonNode node) {
        if (node.hasNonNull("error")) {
            return SampleDecodeResult.failure(node.get("error").asText());
        }
        List<SampleDecodeResult.TreeRow> rows = new ArrayList<>();
        for (JsonNode row : node.path("rows")) {
            rows.add(new SampleDecodeResult.TreeRow(row.path("depth").asInt(), row.path("name").asText(),
                    row.path("kind").asText(), row.path("value").asText(), row.path("byteRange").asText()));
        }
        return new SampleDecodeResult(rows, node.path("truncated").asBoolean(), null);
    }

    private Optional<String> probe(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "-c",
                    "import importlib.metadata as m, drb.drivers.file.file; print(m.version('drb'))")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return process.exitValue() == 0 && !output.isEmpty() ? Optional.of(output) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Keeps only characters that are safe in a file name on every OS; never a path. */
    static String safeSampleName(String fileName) {
        String base = fileName == null ? "" : fileName.replaceAll(".*[/\\\\]", "");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[.]+", "");
        if (base.length() > 80) {
            base = base.substring(base.length() - 80);
        }
        // Reserved by the runner's own files in the same temp directory.
        if (base.isEmpty() || base.equals("generated_drb_driver.py") || base.equals("run_sample.py")
                || base.equals("out.json") || base.equals("err.txt")) {
            return "sample.bin";
        }
        return base;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // A leftover temp directory isn't worth failing the request over.
        }
    }
}
