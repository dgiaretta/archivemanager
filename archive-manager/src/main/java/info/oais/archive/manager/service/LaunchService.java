package info.oais.archive.manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Launching TOPCAT and SPLAT on the viewer's own computer with OpenWebStart
 * (https://openwebstart.com/), the open-source successor to Java Web Start,
 * for those who'd rather not download, start and connect the applications
 * themselves: the archive serves a JNLP file that names the application's
 * jars -- served by the archive too -- and the data to open, as VOTable.
 * <ul>
 *   <li>TOPCAT is one jar, {@code archive.launch.topcat-jar} (e.g.
 *       {@code topcat-full.jar}).</li>
 *   <li>SPLAT is an installation, {@code archive.launch.splat-home} (the
 *       folder SPLAT-VO's installer made): every jar under its {@code lib}
 *       folder, and the native libraries in each of {@code lib}'s
 *       subfolders, packed into a jar per platform as JNLP needs.</li>
 * </ul>
 * Either is offered only when it's configured and found. The jars are
 * served signed with the archive's own self-signed certificate
 * ({@link JarSigning}), so OpenWebStart asks the viewer to trust the archive
 * once, rather than warning about each unsigned application.
 */
@Service
public class LaunchService {

    private static final Logger log = LoggerFactory.getLogger(LaunchService.class);

    private final Path topcatJar;
    private final Path splatHome;
    private final String splatMainClass;
    private final JarSigning signing;

    public LaunchService(@Value("${archive.launch.topcat-jar:}") String topcatJar,
                         @Value("${archive.launch.splat-home:}") String splatHome, JarSigning signing) {
        this.signing = signing;
        this.topcatJar = existing(topcatJar, false);
        Path home = existing(splatHome, true);
        String mainClass = null;
        if (home != null) {
            Path jar = home.resolve("lib/splat/splat.jar");
            try (JarFile j = new JarFile(jar.toFile())) {
                mainClass = j.getManifest() == null ? null
                        : j.getManifest().getMainAttributes().getValue("Main-Class");
            } catch (IOException e) {
                log.warn("archive.launch.splat-home {} has no readable lib/splat/splat.jar: {}", home, e.getMessage());
            }
            if (mainClass == null) {
                log.warn("SPLAT can't be launched: {} names no Main-Class", jar);
                home = null;
            }
        }
        this.splatHome = home;
        this.splatMainClass = mainClass;
        log.info("OpenWebStart launching: TOPCAT {}, SPLAT {}", this.topcatJar == null ? "not configured" : this.topcatJar,
                this.splatHome == null ? "not configured" : this.splatHome);
    }

    private static Path existing(String location, boolean directory) {
        if (location == null || location.isBlank()) {
            return null;
        }
        Path p = Path.of(location).toAbsolutePath().normalize();
        if (directory ? !Files.isDirectory(p) : !Files.isRegularFile(p)) {
            log.warn("{} isn't there, so it can't be launched with OpenWebStart", p);
            return null;
        }
        return p;
    }

    public boolean topcat() {
        return topcatJar != null;
    }

    public boolean splat() {
        return splatHome != null;
    }

    /**
     * A JNLP file launching TOPCAT with {@code votableUrl} loaded.
     *
     * @param codebase the address the launch files are under, ending {@code /launch/}
     * @param href     this JNLP file's address relative to {@code codebase}
     */
    public String topcatJnlp(String codebase, String href, String title, String votableUrl) {
        StringBuilder sb = head(codebase, href, "TOPCAT: " + title, "Mark Taylor, University of Bristol",
                "https://www.star.bris.ac.uk/~mbt/topcat/");
        sb.append("  <resources>\n");
        sb.append("    <j2se version=\"1.8+\" max-heap-size=\"1024m\"/>\n");
        sb.append("    <jar href=\"files/topcat-full.jar\" main=\"true\"/>\n");
        sb.append("  </resources>\n");
        sb.append("  <application-desc main-class=\"uk.ac.starlink.topcat.Driver\">\n");
        argument(sb, "-f");
        argument(sb, "votable");
        argument(sb, votableUrl);
        sb.append("  </application-desc>\n</jnlp>\n");
        return sb.toString();
    }

    /** A JNLP file launching SPLAT with the spectrum at {@code votableUrl} (ending {@code .vot}) loaded. */
    public String splatJnlp(String codebase, String href, String title, String votableUrl) throws IOException {
        StringBuilder sb = head(codebase, href, "SPLAT: " + title, "Starlink / German Astrophysical Virtual Observatory",
                "https://www.g-vo.org/pmwiki/About/SPLAT");
        sb.append("  <resources>\n");
        sb.append("    <j2se version=\"1.8+\" max-heap-size=\"1024m\"/>\n");
        for (String jar : splatJars()) {
            sb.append("    <jar href=\"files/splat/").append(escape(jar)).append('"')
                    .append(jar.equals("lib/splat/splat.jar") ? " main=\"true\"" : "").append("/>\n");
        }
        sb.append("  </resources>\n");
        for (NativeLibraries n : splatNatives()) {
            sb.append("  <resources os=\"").append(n.os()).append("\" arch=\"").append(n.jnlpArch()).append("\">\n");
            sb.append("    <nativelib href=\"files/splat-native/").append(n.key()).append(".jar\"/>\n");
            sb.append("  </resources>\n");
        }
        sb.append("  <application-desc main-class=\"").append(escape(splatMainClass)).append("\">\n");
        argument(sb, votableUrl);
        sb.append("  </application-desc>\n</jnlp>\n");
        return sb.toString();
    }

    private static StringBuilder head(String codebase, String href, String title, String vendor, String homepage) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<jnlp spec=\"1.0+\" codebase=\"").append(escape(codebase)).append("\" href=\"")
                .append(escape(href)).append("\">\n");
        sb.append("  <information>\n");
        sb.append("    <title>").append(escape(title)).append("</title>\n");
        sb.append("    <vendor>").append(escape(vendor)).append("</vendor>\n");
        sb.append("    <homepage href=\"").append(escape(homepage)).append("\"/>\n");
        sb.append("    <description>Opened from an OAIS archive, with the data decoded through its Representation "
                + "Information.</description>\n");
        sb.append("  </information>\n");
        sb.append("  <security>\n    <all-permissions/>\n  </security>\n");
        return sb;
    }

    private static void argument(StringBuilder sb, String value) {
        sb.append("    <argument>").append(escape(value)).append("</argument>\n");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** TOPCAT's jar, signed. */
    public Optional<Path> topcatJarFile() throws IOException {
        return topcatJar == null ? Optional.empty() : Optional.of(signing.signed(topcatJar, "topcat-full.jar"));
    }

    /**
     * Signs TOPCAT's jar in the background once the archive has started, so
     * the first launch needn't wait while 60-odd megabytes are signed.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void signInAdvance() {
        if (topcatJar == null) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                topcatJarFile();
            } catch (IOException | RuntimeException e) {
                log.warn("Couldn't sign TOPCAT's jar in advance: {}", e.getMessage());
            }
        }, "sign-topcat-jar");
        t.setDaemon(true);
        t.start();
    }

    /** SPLAT's jars, relative to its installation, {@code lib/splat/splat.jar} first. */
    public List<String> splatJars() throws IOException {
        if (splatHome == null) {
            return List.of();
        }
        List<String> jars = new ArrayList<>();
        try (Stream<Path> files = Files.walk(splatHome.resolve("lib"))) {
            files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar") && Files.isRegularFile(p))
                    .map(p -> splatHome.relativize(p).toString().replace('\\', '/')).sorted().forEach(jars::add);
        }
        jars.remove("lib/splat/splat.jar");
        jars.add(0, "lib/splat/splat.jar");
        return jars;
    }

    /** One of SPLAT's jars, signed, if {@code relative} names one. */
    public Optional<Path> splatFile(String relative) throws IOException {
        return splatJars().contains(relative)
                ? Optional.of(signing.signed(splatHome.resolve(relative), "splat-" + relative.replace('/', '-')))
                : Optional.empty();
    }

    /**
     * SPLAT's native libraries for one platform: the files of one of
     * {@code lib}'s subfolders that are libraries for one operating system.
     *
     * @param os     the JNLP operating system, e.g. {@code Windows}
     * @param arch   the subfolder's name, e.g. {@code amd64}
     * @param files  the libraries
     */
    public record NativeLibraries(String os, String arch, List<Path> files) {

        public String key() {
            return os.toLowerCase(Locale.ROOT).replace(' ', '-') + "-" + arch;
        }

        /** The architectures JNLP should match: {@code amd64} is {@code x86_64} on a Mac. */
        public String jnlpArch() {
            return arch.equals("amd64") ? "amd64 x86_64" : arch;
        }
    }

    private static final Map<String, String> OS_BY_EXTENSION = Map.of(".dll", "Windows", ".so", "Linux",
            ".dylib", "Mac OS X", ".jnilib", "Mac OS X");

    /** SPLAT's native libraries, by platform. */
    public List<NativeLibraries> splatNatives() throws IOException {
        if (splatHome == null) {
            return List.of();
        }
        Map<String, NativeLibraries> found = new LinkedHashMap<>();
        try (Stream<Path> dirs = Files.list(splatHome.resolve("lib"))) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path f : files.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString))
                            .toList()) {
                        String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
                        OS_BY_EXTENSION.forEach((extension, os) -> {
                            if (name.endsWith(extension) || name.contains(extension + ".")) {
                                found.computeIfAbsent(os + "/" + dir.getFileName(), k -> new NativeLibraries(os,
                                        dir.getFileName().toString(), new ArrayList<>())).files().add(f);
                            }
                        });
                    }
                }
            }
        }
        return List.copyOf(found.values());
    }

    /** The jar of SPLAT's native libraries for the platform {@code key} names, if there are any. */
    public Optional<byte[]> splatNativeJar(String key) throws IOException {
        for (NativeLibraries n : splatNatives()) {
            if (n.key().equals(key)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                    for (Path f : n.files()) {
                        zip.putNextEntry(new ZipEntry(f.getFileName().toString()));
                        zip.write(Files.readAllBytes(f));
                        zip.closeEntry();
                    }
                }
                return Optional.of(signing.signed(bytes.toByteArray(), "splat-native-" + key + ".jar"));
            }
        }
        return Optional.empty();
    }
}
