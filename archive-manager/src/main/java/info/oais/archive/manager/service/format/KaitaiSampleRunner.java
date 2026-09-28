package info.oais.archive.manager.service.format;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.kaitai.KaitaiFormatSpecification;
import info.oais.infomodel.structure.kaitai.KaitaiStructureRepInfo;
import io.kaitai.struct.KaitaiStruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Runs a generated Kaitai Struct {@code .ksy} (see {@link KaitaiGenerator})
 * against a sample data file -- the Kaitai counterpart of
 * {@link DfdlSampleRunner}. Kaitai descriptions aren't interpreted at run
 * time, so this takes three steps:
 * <ol>
 *   <li>the <strong>Kaitai Struct compiler</strong> turns the {@code .ksy}
 *       into Java source. The compiler (GPL-3.0) is bundled in the jar under
 *       {@code kaitai-compiler/}, not on the application's classpath: it's
 *       extracted once to a temporary directory and run as a <em>separate
 *       Java process</em>, with the {@code java} this application runs on
 *       (see {@code third-party/README.md});</li>
 *   <li>that source is compiled in-process with the JDK's compiler, or with
 *       the bundled Eclipse compiler when the application runs on a JRE;</li>
 *   <li>the compiled class is loaded in its own class loader and applied to
 *       the sample through {@code oais-structure-kaitai}'s
 *       {@link KaitaiStructureRepInfo}. It's compiled with {@code --debug},
 *       so the result carries byte positions.</li>
 * </ol>
 * Only one description is compiled at a time; each run takes a few seconds,
 * mostly the compiler starting up.
 */
@Component
public class KaitaiSampleRunner {

    private static final String PACKAGE = "repinfo.kaitai";
    private static final String RUNTIME_JAR = "kaitai-struct-runtime-0.11.jar";

    private final long timeoutSeconds;
    private final boolean eclipseCompiler;
    private Path toolDir;

    @Autowired
    public KaitaiSampleRunner(@Value("${archive.kaitai.timeout-seconds:120}") long timeoutSeconds) {
        this(timeoutSeconds, false);
    }

    /** @param eclipseCompiler use the bundled Eclipse compiler even when the JDK's is available (for tests) */
    public KaitaiSampleRunner(long timeoutSeconds, boolean eclipseCompiler) {
        this.timeoutSeconds = timeoutSeconds;
        this.eclipseCompiler = eclipseCompiler;
    }

    /** A {@code .ksy} the compiler rejected, with the compiler's own messages. */
    public static final class CompileException extends Exception {
        private static final long serialVersionUID = 1L;

        CompileException(String message) {
            super(message);
        }
    }

    /** @param format the description the {@code .ksy} was generated from, to line the result up against */
    public SampleDecodeResult run(FormatDescription format, String ksy, byte[] sample) {
        return run(format, ksy, format.root().name(), sample);
    }

    /**
     * @param format   the description to line the result up against, or null not to (e.g. for a
     *                 {@code .ksy} written by hand)
     * @param rootName the {@code .ksy}'s {@code meta/id}
     */
    public SampleDecodeResult run(FormatDescription format, String ksy, String rootName, byte[] sample) {
        try {
            return withDecoded(ksy, rootName, sample,
                    root -> format == null ? SampleDecodeResult.of(root) : SampleDecodeResult.of(format, root));
        } catch (CompileException e) {
            return SampleDecodeResult.failure(e.getMessage());
        } catch (IOException | UncheckedIOException e) {
            return SampleDecodeResult.failure("Could not run the Kaitai Struct compiler: " + e.getMessage());
        } catch (RuntimeException e) {
            // Includes StructureInterpretationException, e.g. the data ending too early.
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            if (e.getCause() != null && e.getCause().getMessage() != null && !message.contains(e.getCause().getMessage())) {
                message += "\n" + e.getCause().getMessage();
            }
            return SampleDecodeResult.failure(message);
        }
    }

    /**
     * Compiles {@code ksy}, decodes {@code sample} with it, and hands the
     * decoded tree to {@code use} -- while the compiled classes are still
     * loaded, since Kaitai reads some elements (those at an offset) only
     * when asked for them.
     *
     * @param rootName the {@code .ksy}'s {@code meta/id}, which names the class to load
     */
    public <T> T withDecoded(String ksy, String rootName, byte[] sample, Function<StructureNode, T> use)
            throws IOException, CompileException {
        Path work = Files.createTempDirectory("repinfo-kaitai-");
        try {
            Path src = Files.createDirectories(work.resolve("src"));
            Path classes = Files.createDirectories(work.resolve("classes"));
            Path ksyFile = Files.writeString(work.resolve(rootName + ".ksy"), ksy, StandardCharsets.UTF_8);
            synchronized (this) {
                compileKsy(ksyFile, src);
                compileJava(src, classes);
            }
            try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
                    KaitaiStruct.class.getClassLoader())) {
                Class<? extends KaitaiStruct> type;
                try {
                    type = loader.loadClass(PACKAGE + "." + className(rootName)).asSubclass(KaitaiStruct.class);
                } catch (ClassNotFoundException e) {
                    throw new CompileException("The Kaitai Struct compiler didn't produce a class for '" + rootName + "'.");
                }
                StructureNode root = new KaitaiStructureRepInfo(new KaitaiFormatSpecification(type))
                        .apply(new DigitalObjectRefImpl(new ByteArrayInputStream(sample)));
                return use.apply(root);
            }
        } finally {
            deleteRecursively(work);
        }
    }

    private void compileKsy(Path ksyFile, Path outDir) throws IOException, CompileException {
        Path tools = tools();
        List<String> classpath = new ArrayList<>();
        for (String jar : compilerJars()) {
            classpath.add(tools.resolve(jar).toString());
        }
        List<String> command = List.of(javaExecutable(), "-Xmx256m", "-cp",
                String.join(java.io.File.pathSeparator, classpath), "io.kaitai.struct.JavaMain",
                "-t", "java", "--debug", "--java-package", PACKAGE, "--outdir", outDir.toString(), ksyFile.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output;
        try (InputStream in = process.getInputStream()) {
            output = in.readAllBytes();
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CompileException("The Kaitai Struct compiler took longer than " + timeoutSeconds + " seconds.");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new CompileException("Interrupted while waiting for the Kaitai Struct compiler.");
        }
        String messages = new String(output, StandardCharsets.UTF_8).strip();
        boolean wroteJava;
        try (Stream<Path> files = Files.walk(outDir)) {
            wroteJava = files.anyMatch(p -> p.toString().endsWith(".java"));
        }
        if (process.exitValue() != 0 || !wroteJava) {
            throw new CompileException("The Kaitai Struct compiler rejected the description:\n"
                    + (messages.isEmpty() ? "(no messages; exit code " + process.exitValue() + ")" : messages));
        }
    }

    private void compileJava(Path src, Path classes) throws IOException, CompileException {
        List<String> args = new ArrayList<>(List.of("-nowarn", "-proc:none", "-encoding", "UTF-8",
                "-d", classes.toString(), "-cp", tools().resolve(RUNTIME_JAR).toString()));
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).forEach(args::add);
        }
        JavaCompiler jdk = eclipseCompiler ? null : ToolProvider.getSystemJavaCompiler();
        boolean ok;
        String messages;
        if (jdk != null) {
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ok = jdk.run(null, errors, errors, args.toArray(String[]::new)) == 0;
            messages = errors.toString(StandardCharsets.UTF_8);
        } else {
            // A JRE has no compiler of its own: use the bundled Eclipse compiler, through its
            // batch API - its javax.tools entry point calls System.exit when it finishes.
            args.addAll(0, List.of("-17"));
            StringWriter errors = new StringWriter();
            PrintWriter out = new PrintWriter(errors);
            ok = BatchCompiler.compile(args.toArray(String[]::new), out, out, null);
            out.flush();
            messages = errors.toString();
        }
        if (!ok) {
            throw new CompileException("The Java generated by the Kaitai Struct compiler didn't compile:\n" + messages);
        }
    }

    /** Extracts the bundled compiler's jars (once per application run). */
    private synchronized Path tools() throws IOException {
        if (toolDir != null && Files.isDirectory(toolDir)) {
            return toolDir;
        }
        Path dir = Files.createTempDirectory("archive-manager-kaitai-compiler-");
        List<String> jars = new ArrayList<>(compilerJars());
        jars.add(RUNTIME_JAR);
        for (String jar : jars) {
            try (InputStream in = KaitaiSampleRunner.class.getResourceAsStream("/kaitai-compiler/" + jar)) {
                if (in == null) {
                    throw new IOException("The bundled Kaitai Struct compiler is missing " + jar
                            + " (was the jar built with 'mvn package'?)");
                }
                Files.copy(in, dir.resolve(jar), StandardCopyOption.REPLACE_EXISTING);
            }
            dir.resolve(jar).toFile().deleteOnExit();
        }
        dir.toFile().deleteOnExit();
        toolDir = dir;
        return dir;
    }

    private static List<String> compilerJars() throws IOException {
        try (InputStream in = KaitaiSampleRunner.class.getResourceAsStream("/kaitai-compiler/classpath.txt")) {
            if (in == null) {
                throw new IOException("The bundled Kaitai Struct compiler is missing (kaitai-compiler/classpath.txt)");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        }
    }

    private static String javaExecutable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    /** Kaitai's Java class name for a {@code meta/id}: {@code telemetry_packets} becomes {@code TelemetryPackets}. */
    static String className(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // A leftover temp file isn't worth failing the request over.
                }
            });
        } catch (IOException ignored) {
            // As above.
        }
    }
}
