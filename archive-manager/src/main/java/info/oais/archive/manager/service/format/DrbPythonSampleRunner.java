package info.oais.archive.manager.service.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.oais.infomodel.structure.description.FormatDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>The driver is loaded as a package, so a hand-written add-in can sit next
 * to it as {@code addin.py}, as in the downloaded driver package. An add-in is
 * Python code written by a user of this app, which nothing here can make safe
 * to run, so it's only run when {@code archive.drb-python.run-hand-written-add-ins}
 * is true ({@link #runsAddIns()}); {@link #checkAddIn} only parses one, which
 * doesn't run it. After decoding, the run also calls the driver's
 * {@code metadata} and {@code checks} add-ons and reports what they return.</p>
 */
@Component
public class DrbPythonSampleRunner {

    private static final long TIMEOUT_SECONDS = 60;
    private static final long PROBE_TIMEOUT_SECONDS = 20;
    /** How long a failed interpreter probe is remembered, so installing drb doesn't need an app restart. */
    private static final long NEGATIVE_CACHE_MILLIS = 60_000;

    private static final String RUNNER_SCRIPT = """
            import importlib.util, json, os, sys
            from drb.drivers.file.file import DrbFileFactory

            import base64
            package_dir, sample_path, factory_name, max_rows = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
            addon_prefix = sys.argv[5] if len(sys.argv) > 5 else ""
            changes_path = sys.argv[6] if len(sys.argv) > 6 else ""
            rows = []

            def describe(value):
                if value is None:
                    return ""
                if isinstance(value, (bytes, bytearray)):
                    shown = "0x" + bytes(value[:64]).hex()
                    return shown if len(value) <= 64 else shown + "\u2026 (" + str(len(value)) + " bytes)"
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

            def add_ons(root):
                if not addon_prefix:
                    return None
                try:
                    metadata = root.get_impl(dict, addon_prefix + "_metadata")
                    checks = root.get_impl(list, addon_prefix + "_checks")
                    return {"metadata": {str(k): describe(v) for k, v in metadata.items()},
                            "checks": [str(c) for c in checks], "error": None}
                except Exception as ex:
                    return {"metadata": {}, "checks": [], "error": type(ex).__name__ + ": " + str(ex)}

            try:
                spec = importlib.util.spec_from_file_location("generated_drb_driver",
                        os.path.join(package_dir, "__init__.py"), submodule_search_locations=[package_dir])
                module = importlib.util.module_from_spec(spec)
                sys.modules[spec.name] = module
                spec.loader.exec_module(module)
                root = getattr(module, factory_name)().create(DrbFileFactory().create(sample_path))
                if changes_path:
                    changes = json.load(open(changes_path, encoding="utf-8"))
                    written = root.get_impl(bytes, addon_prefix + "_write", changes=changes)
                    # Without restore(), what prepare() did can't be undone: compare with what it produced.
                    reference = None
                    if module._hook("prepare") is not None and module._hook("restore") is None:
                        reference = base64.b64encode(root.decoded_bytes).decode("ascii")
                    print(json.dumps({"written": base64.b64encode(written).decode("ascii"), "reference": reference,
                                      "error": None}))
                    sys.exit(0)
                complete = walk(root, 0)
                trailing = getattr(root, "trailing_bytes", None)
                print(json.dumps({"rows": rows, "truncated": not complete, "error": None, "trailing": trailing,
                                  "addOns": add_ons(root)}))
            except Exception as ex:
                print(json.dumps({"rows": [], "truncated": False, "error": type(ex).__name__ + ": " + str(ex)}))
            """;

    /**
     * What writing a sample back produced.
     *
     * @param written   the written bytes, or null on failure
     * @param reference what to compare them with instead of the sample, when a hand-written add-in's
     *                  {@code prepare()} changed the bytes before decoding and there's no {@code restore()}
     *                  to undo it: the bytes {@code prepare()} produced. Null otherwise.
     * @param error     why it failed, or null
     */
    public record WriteOutcome(byte[] written, byte[] reference, String error) {
        static WriteOutcome failure(String error) {
            return new WriteOutcome(null, null, error);
        }
    }

    /** Parses an add-in without running it: syntax errors, and which hooks it defines at the top level. */
    private static final String CHECK_SCRIPT = """
            import ast, json, sys
            source = open(sys.argv[1], encoding="utf-8").read()
            try:
                tree = ast.parse(source, "addin.py")
            except SyntaxError as ex:
                print(json.dumps({"error": "line " + str(ex.lineno) + ": " + str(ex.msg), "hooks": []}))
            else:
                hooks = [n.name for n in tree.body if isinstance(n, ast.FunctionDef)]
                print(json.dumps({"error": None, "hooks": hooks}))
            """;

    /** The hooks a hand-written add-in can define; see {@code drb-python/interpreter.py}. */
    public static final List<String> ADD_IN_HOOKS = List.of("prepare", "check", "metadata", "restore");

    /**
     * What a drb-python run produced: the decoded tree, and what the driver's
     * add-ons returned (null when they weren't asked for, or the file
     * couldn't be decoded).
     */
    public record Outcome(SampleDecodeResult decoded, AddOnResults addOns) {
    }

    /**
     * @param metadata the {@code metadata} add-on's result, as text
     * @param checks   the {@code checks} add-on's problems; empty if none
     * @param error    why the add-ons failed, or null
     */
    public record AddOnResults(Map<String, String> metadata, List<String> checks, String error) {
    }

    private final String configuredExecutable;
    private final boolean runsAddIns;
    private final ObjectMapper json = new ObjectMapper();
    private volatile String resolvedExecutable;
    private volatile String resolvedVersion;
    private volatile long lastFailedProbe;

    @Autowired
    public DrbPythonSampleRunner(@Value("${archive.drb-python.executable:}") String configuredExecutable,
                                 @Value("${archive.drb-python.run-hand-written-add-ins:false}") boolean runsAddIns) {
        this.configuredExecutable = configuredExecutable == null ? "" : configuredExecutable.strip();
        this.runsAddIns = runsAddIns;
    }

    public DrbPythonSampleRunner(String configuredExecutable) {
        this(configuredExecutable, false);
    }

    /** Whether sample tests run hand-written add-ins ({@code archive.drb-python.run-hand-written-add-ins}). */
    public boolean runsAddIns() {
        return runsAddIns;
    }

    /** Why a sample test with a hand-written add-in wasn't run. */
    public static final String ADD_INS_NOT_RUN = "This driver has a hand-written add-in, which is Python code, and "
            + "this server doesn't run hand-written code: set archive.drb-python.run-hand-written-add-ins to true "
            + "to allow it (anyone who can log in could then run code on the server). The package can still be "
            + "downloaded and tested elsewhere.";

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
        return run(driverModule, null, factoryName, null, sample, fileName).decoded();
    }

    /**
     * Runs the driver, with its hand-written add-in if there is one, then its
     * {@code metadata} and {@code checks} add-ons.
     *
     * @param addIn       the hand-written add-in ({@code addin.py}), or null; not run unless {@link #runsAddIns()}
     * @param addonPrefix the driver's add-on prefix ({@link DrbGenerator#pythonDriverId}), or null to skip the add-ons
     */
    public Outcome run(String driverModule, String addIn, String factoryName, String addonPrefix, byte[] sample,
                       String fileName) {
        if (drbVersion().isEmpty()) {
            return new Outcome(SampleDecodeResult.failure(notAvailableMessage()), null);
        }
        if (addIn != null && !runsAddIns) {
            return new Outcome(SampleDecodeResult.failure(ADD_INS_NOT_RUN), null);
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("repinfo-tools-drb-");
            Path packageDir = Files.createDirectory(dir.resolve("generated_drb_driver"));
            Files.writeString(packageDir.resolve("__init__.py"), driverModule, StandardCharsets.UTF_8);
            if (addIn != null) {
                Files.writeString(packageDir.resolve("addin.py"), addIn, StandardCharsets.UTF_8);
            }
            Path runner = Files.writeString(dir.resolve("run_sample.py"), RUNNER_SCRIPT, StandardCharsets.UTF_8);
            Path samplePath = Files.write(dir.resolve(safeSampleName(fileName)), sample);
            List<String> command = new ArrayList<>(List.of(resolvedExecutable, "-X", "utf8", "-W", "ignore",
                    runner.toString(), packageDir.toString(), samplePath.toString(), factoryName,
                    String.valueOf(SampleDecodeResult.MAX_ROWS)));
            if (addonPrefix != null) {
                command.add(addonPrefix);
            }
            ProcessOutput output = runPython(dir, command);
            if (output.error() != null) {
                return new Outcome(SampleDecodeResult.failure(output.error()), null);
            }
            JsonNode node = json.readTree(output.stdout());
            return new Outcome(toResult(node), toAddOns(node.path("addOns")));
        } catch (IOException e) {
            return new Outcome(SampleDecodeResult.failure("Could not run drb-python: " + e.getMessage()), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(SampleDecodeResult.failure("Interrupted while waiting for drb-python."), null);
        } finally {
            deleteQuietly(dir);
        }
    }

    /**
     * Writes {@code sample} back with the driver's {@code write} add-on: decodes
     * it, applies {@code changes} (element path -> new value as text) and
     * encodes it again with the same description.
     */
    public WriteOutcome write(String driverModule, String addIn, String factoryName, String addonPrefix, byte[] sample,
                              Map<String, String> changes) {
        if (drbVersion().isEmpty()) {
            return WriteOutcome.failure(notAvailableMessage());
        }
        if (addIn != null && !runsAddIns) {
            return WriteOutcome.failure(ADD_INS_NOT_RUN);
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("repinfo-tools-drb-");
            Path packageDir = Files.createDirectory(dir.resolve("generated_drb_driver"));
            Files.writeString(packageDir.resolve("__init__.py"), driverModule, StandardCharsets.UTF_8);
            if (addIn != null) {
                Files.writeString(packageDir.resolve("addin.py"), addIn, StandardCharsets.UTF_8);
            }
            Path runner = Files.writeString(dir.resolve("run_sample.py"), RUNNER_SCRIPT, StandardCharsets.UTF_8);
            Path samplePath = Files.write(dir.resolve("sample.bin"), sample);
            Path changesPath = Files.writeString(dir.resolve("changes.json"), json.writeValueAsString(changes),
                    StandardCharsets.UTF_8);
            ProcessOutput output = runPython(dir, List.of(resolvedExecutable, "-X", "utf8", "-W", "ignore",
                    runner.toString(), packageDir.toString(), samplePath.toString(), factoryName,
                    String.valueOf(SampleDecodeResult.MAX_ROWS), addonPrefix, changesPath.toString()));
            if (output.error() != null) {
                return WriteOutcome.failure(output.error());
            }
            JsonNode node = json.readTree(output.stdout());
            if (node.hasNonNull("error")) {
                return WriteOutcome.failure(node.get("error").asText());
            }
            java.util.Base64.Decoder base64 = java.util.Base64.getDecoder();
            return new WriteOutcome(base64.decode(node.get("written").asText()),
                    node.hasNonNull("reference") ? base64.decode(node.get("reference").asText()) : null, null);
        } catch (IOException e) {
            return WriteOutcome.failure("Could not run drb-python: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WriteOutcome.failure("Interrupted while waiting for drb-python.");
        } finally {
            deleteQuietly(dir);
        }
    }

    /**
     * Checks a hand-written add-in by parsing it with Python, which doesn't
     * run it: syntax errors, and whether it defines any of the {@link #ADD_IN_HOOKS}.
     *
     * @return what's wrong, in plain words; empty if nothing is found, or if no Python is available to check with
     */
    public List<String> checkAddIn(String addIn) {
        if (drbVersion().isEmpty()) {
            return List.of();
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("repinfo-tools-drb-");
            Path source = Files.writeString(dir.resolve("addin.py"), addIn, StandardCharsets.UTF_8);
            Path script = Files.writeString(dir.resolve("check_addin.py"), CHECK_SCRIPT, StandardCharsets.UTF_8);
            ProcessOutput output = runPython(dir, List.of(resolvedExecutable, "-I", "-X", "utf8", script.toString(),
                    source.toString()));
            if (output.error() != null) {
                return List.of("Could not check the add-in: " + output.error());
            }
            JsonNode node = json.readTree(output.stdout());
            if (node.hasNonNull("error")) {
                return List.of("This isn't valid Python (" + node.get("error").asText() + ").");
            }
            List<String> hooks = new ArrayList<>();
            node.path("hooks").forEach(h -> hooks.add(h.asText()));
            if (hooks.stream().noneMatch(ADD_IN_HOOKS::contains)) {
                return List.of("The add-in doesn't define any of the functions the driver calls: "
                        + String.join("(), ", ADD_IN_HOOKS) + "().");
            }
            return List.of();
        } catch (IOException e) {
            return List.of("Could not check the add-in: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of("Interrupted while checking the add-in.");
        } finally {
            deleteQuietly(dir);
        }
    }

    private record ProcessOutput(String stdout, String error) {
    }

    private ProcessOutput runPython(Path dir, List<String> command) throws IOException, InterruptedException {
        Path out = dir.resolve("out.json");
        Path err = dir.resolve("err.txt");
        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectOutput(out.toFile())
                .redirectError(err.toFile())
                .start();
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new ProcessOutput(null, "drb-python did not finish within " + TIMEOUT_SECONDS + " seconds.");
        }
        String stdout = Files.readString(out, StandardCharsets.UTF_8).strip();
        if (stdout.isEmpty()) {
            return new ProcessOutput(null, "drb-python exited with code " + process.exitValue() + ":\n"
                    + Files.readString(err, StandardCharsets.UTF_8).strip());
        }
        return new ProcessOutput(stdout, null);
    }

    private static AddOnResults toAddOns(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        node.path("metadata").fields().forEachRemaining(e -> metadata.put(e.getKey(), e.getValue().asText()));
        List<String> checks = new ArrayList<>();
        node.path("checks").forEach(c -> checks.add(c.asText()));
        return new AddOnResults(metadata, checks, node.hasNonNull("error") ? node.get("error").asText() : null);
    }

    /**
     * Lines a successful run's rows up against the description the driver was
     * generated from, like the other engines' results (see {@link SampleDecodeResult}).
     */
    public static SampleDecodeResult align(FormatDescription format, SampleDecodeResult raw) {
        if (!raw.ok()) {
            return raw;
        }
        SampleDecodeResult aligned = SampleDecodeResult.of(format, SampleDecodeResult.toStructureNode(raw.rows()));
        return new SampleDecodeResult(aligned.rows(), aligned.truncated() || raw.truncated(), null, raw.trailingBytes(),
                aligned.warning());
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
        Long trailing = node.hasNonNull("trailing") ? node.get("trailing").asLong() : null;
        return new SampleDecodeResult(rows, node.path("truncated").asBoolean(), null, trailing, null);
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

    /** Keeps only characters that are safe in a file name on every OS; never a path. Empty if nothing is left. */
    public static String safeName(String fileName) {
        String base = fileName == null ? "" : fileName.replaceAll(".*[/\\\\]", "");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[.]+", "");
        if (base.length() > 80) {
            base = base.substring(base.length() - 80);
        }
        return base.isEmpty() ? "sample.bin" : base;
    }

    /** Keeps only characters that are safe in a file name on every OS; never a path. */
    static String safeSampleName(String fileName) {
        String base = fileName == null ? "" : fileName.replaceAll(".*[/\\\\]", "");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[.]+", "");
        if (base.length() > 80) {
            base = base.substring(base.length() - 80);
        }
        // Reserved by the runner's own files in the same temp directory.
        if (base.isEmpty() || base.equals("generated_drb_driver") || base.equals("run_sample.py")
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
