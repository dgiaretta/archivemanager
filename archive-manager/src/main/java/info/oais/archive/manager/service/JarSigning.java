package info.oais.archive.manager.service;

import jdk.security.jarsigner.JarSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

/**
 * Signs the jars the archive serves for launching with OpenWebStart (see
 * {@link LaunchService}), so OpenWebStart asks the viewer to trust one
 * publisher -- this archive -- instead of warning that an unsigned
 * application wants unrestricted access.
 *
 * <p>The key is the archive's own, with a <em>self-signed</em> certificate:
 * made on first use with the JDK's {@code keytool} and kept in
 * {@code archive.launch.signing-location} (a PKCS #12 keystore, its password
 * in a file beside it). Viewers trust it once; a certificate from a
 * recognised authority would avoid even that. Each jar is signed once, with
 * {@code Permissions: all-permissions} and {@code Application-Name} added to
 * its manifest as OpenWebStart expects, and the signed copy kept until the
 * original changes. Without {@code keytool} (a JRE rather than a JDK), jars
 * are served unsigned.</p>
 */
@Component
public class JarSigning {

    private static final Logger log = LoggerFactory.getLogger(JarSigning.class);
    private static final String ALIAS = "archive-launch";

    private final Path location;
    private final String subject;
    private PrivateKey key;
    private CertPath certPath;
    private boolean tried;

    public JarSigning(@Value("${archive.launch.signing-location:data/launch}") String location,
                      @Value("${archive.launch.signing-subject:CN=OAIS Archive Manager (self-signed), O=OAIS Archive}")
                      String subject) {
        this.location = Path.of(location).toAbsolutePath().normalize();
        this.subject = subject;
    }

    /** Whether jars can be signed: the key exists or could be made. */
    public synchronized boolean available() {
        load();
        return key != null;
    }

    /** The certificate jars are signed with, DER-encoded, if there's a key. */
    public synchronized Optional<X509Certificate> certificate() {
        load();
        return key == null ? Optional.empty() : Optional.of((X509Certificate) certPath.getCertificates().get(0));
    }

    /** The certificate's SHA-256 fingerprint, as OpenWebStart shows it, e.g. {@code AB:CD:...}. */
    public Optional<String> fingerprint() {
        return certificate().map(c -> {
            try {
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
                return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * {@code source} signed, as {@code name} names it for OpenWebStart:
     * kept and reused until {@code source} changes; {@code source} itself if
     * jars can't be signed here.
     */
    public synchronized Path signed(Path source, String name) throws IOException {
        load();
        if (key == null) {
            return source;
        }
        String stamp = source.toAbsolutePath() + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source)
                + "|" + subject;
        Path target = location.resolve("signed").resolve(BitStore.safeName(name).replaceAll("\\.jar$", "") + "-"
                + BitStore.sha256(stamp.getBytes(StandardCharsets.UTF_8)).substring(0, 16) + ".jar");
        if (Files.isRegularFile(target)) {
            return target;
        }
        Files.createDirectories(target.getParent());
        long start = System.nanoTime();
        Path withManifest = Files.createTempFile(target.getParent(), "manifest-", ".jar");
        Path signing = Files.createTempFile(target.getParent(), "signing-", ".jar");
        try {
            addPermissions(source, withManifest, name);
            try (ZipFile zip = new ZipFile(withManifest.toFile()); OutputStream out = Files.newOutputStream(signing)) {
                new JarSigner.Builder(key, certPath).digestAlgorithm("SHA-256").signatureAlgorithm("SHA256withRSA")
                        .signerName("ARCHIVE").build().sign(zip, out);
            } catch (GeneralSecurityException e) {
                throw new IOException("Couldn't sign " + name + ": " + e.getMessage(), e);
            }
            Files.move(signing, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(withManifest);
            Files.deleteIfExists(signing);
        }
        log.info("Signed {} for OpenWebStart in {} ms", name, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return target;
    }

    /** {@code jar} signed, for jars made on the fly; {@code jar} itself if jars can't be signed here. */
    public byte[] signed(byte[] jar, String name) throws IOException {
        if (!available()) {
            return jar;
        }
        Files.createDirectories(location.resolve("signed"));
        Path source = location.resolve("signed").resolve("source-" + BitStore.sha256(jar).substring(0, 16) + ".jar");
        if (!Files.isRegularFile(source)) {
            Files.write(source, jar);
        }
        return Files.readAllBytes(signed(source, name));
    }

    /**
     * Copies {@code source} to {@code target} with {@code Permissions:
     * all-permissions} and {@code Application-Name} in its manifest, and
     * without any signatures it already had.
     */
    private static void addPermissions(Path source, Path target, String name) throws IOException {
        try (JarFile in = new JarFile(source.toFile(), false)) {
            Manifest manifest = in.getManifest() == null ? new Manifest() : new Manifest(in.getManifest());
            Attributes main = manifest.getMainAttributes();
            main.putIfAbsent(Attributes.Name.MANIFEST_VERSION, "1.0");
            main.put(new Attributes.Name("Permissions"), "all-permissions");
            main.putIfAbsent(new Attributes.Name("Application-Name"), name.replaceAll("\\.jar$", ""));
            manifest.getEntries().clear();
            try (OutputStream out = Files.newOutputStream(target); JarOutputStream jar = new JarOutputStream(out,
                    manifest)) {
                Enumeration<JarEntry> entries = in.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    String entryName = e.getName().toUpperCase(java.util.Locale.ROOT);
                    if (entryName.equals("META-INF/MANIFEST.MF") || entryName.startsWith("META-INF/")
                            && (entryName.endsWith(".SF") || entryName.endsWith(".RSA") || entryName.endsWith(".DSA")
                            || entryName.endsWith(".EC"))) {
                        continue;
                    }
                    JarEntry copy = new JarEntry(e.getName());
                    copy.setTime(e.getTime());
                    jar.putNextEntry(copy);
                    if (!e.isDirectory()) {
                        try (InputStream entry = in.getInputStream(e)) {
                            entry.transferTo(jar);
                        }
                    }
                    jar.closeEntry();
                }
            }
        }
    }

    /** Loads the key, making it the first time. */
    private void load() {
        if (tried) {
            return;
        }
        tried = true;
        Path keystore = location.resolve("signing.p12");
        Path passwordFile = location.resolve("signing.password");
        try {
            Files.createDirectories(location);
            if (!Files.isRegularFile(keystore)) {
                String password = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
                Files.writeString(passwordFile, password, StandardCharsets.UTF_8);
                make(keystore, password);
            }
            char[] password = Files.readString(passwordFile, StandardCharsets.UTF_8).strip().toCharArray();
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keystore)) {
                store.load(in, password);
            }
            key = (PrivateKey) store.getKey(ALIAS, password);
            Certificate[] chain = store.getCertificateChain(ALIAS);
            certPath = CertificateFactory.getInstance("X.509").generateCertPath(Arrays.asList(chain));
            Arrays.fill(password, '\0');
            log.info("Jars for OpenWebStart are signed with the archive's self-signed certificate {}: {}", keystore,
                    ((X509Certificate) chain[0]).getSubjectX500Principal());
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            key = null;
            log.warn("Jars for OpenWebStart will be served unsigned: no signing key ({})", e.getMessage());
        }
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /** Makes the keystore with the JDK's keytool: an RSA key and a self-signed certificate, for ten years. */
    private void make(Path keystore, String password) throws IOException {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        if (!Files.isExecutable(keytool)) {
            throw new IOException("there's no keytool in " + keytool.getParent() + " (a JDK is needed, not a JRE)");
        }
        Process p = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA",
                "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "3650", "-dname", subject,
                "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", password, "-keypass", password,
                "-ext", "KeyUsage=digitalSignature", "-ext", "ExtendedKeyUsage=codeSigning")
                .redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(output);
        }
        try {
            if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
                p.destroyForcibly();
                Files.deleteIfExists(keystore);
                throw new IOException("keytool failed: " + output.toString(StandardCharsets.UTF_8).strip());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted making the signing key", e);
        }
        log.info("Made the archive's self-signed signing key for OpenWebStart: {}", keystore);
    }
}
