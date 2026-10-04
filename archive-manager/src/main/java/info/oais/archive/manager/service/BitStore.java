package info.oais.archive.manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The archive's own store of bits: files the archive itself makes, such as
 * the result of a Transformation, kept in a folder ({@code archive.bits-location})
 * and served at {@code /api/bits/{id}/{name}} -- the address that becomes the
 * new Data Object's {@code im:hasStorageLocation}. Each file is in its own
 * folder named by a random id, and is never changed once written.
 */
@Component
public class BitStore {

    private static final Logger log = LoggerFactory.getLogger(BitStore.class);

    /** The path, under the archive's address, at which stored bits are served. */
    public static final String PATH = "/api/bits/";

    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern LOCATION = Pattern.compile(Pattern.quote(PATH) + "(" + ID.pattern() + ")/([^/]+)$");

    private final Path root;

    public BitStore(@Value("${archive.bits-location:data/bits}") String location) {
        this.root = Path.of(location).toAbsolutePath().normalize();
        log.info("Archive bit store at {}", root);
    }

    /**
     * A stored file.
     *
     * @param id     its folder's id
     * @param name   its file name
     * @param size   its length in bytes
     * @param sha256 its SHA-256 digest, in lower-case hex
     */
    public record StoredBits(String id, String name, long size, String sha256) {

        /** The path, under the archive's address, it is served at. */
        public String path() {
            return PATH + id + "/" + name;
        }
    }

    /**
     * Stores {@code bytes} as a new file called {@code name} (reduced to
     * letters, digits, dots, dashes and underscores).
     */
    public StoredBits store(byte[] bytes, String name) throws IOException {
        String id = UUID.randomUUID().toString();
        String safe = safeName(name);
        Path dir = Files.createDirectories(root.resolve(id));
        Files.write(dir.resolve(safe), bytes);
        return new StoredBits(id, safe, bytes.length, sha256(bytes));
    }

    /** The stored file with this id and name, if there is one. */
    public Optional<Path> file(String id, String name) {
        if (!ID.matcher(id).matches() || !safeName(name).equals(name)) {
            return Optional.empty();
        }
        Path file = root.resolve(id).resolve(name);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /**
     * The stored file {@code location} -- an address ending
     * {@code /api/bits/{id}/{name}} -- names, if this store has it: read from
     * here, not over the network, whatever host the address gives (the
     * archive's address can change).
     */
    public Optional<Path> file(URI location) {
        String path = location.getPath();
        if (path == null) {
            return Optional.empty();
        }
        Matcher m = LOCATION.matcher(path);
        return m.find() ? file(m.group(1), m.group(2)) : Optional.empty();
    }

    /** Removes a stored file, e.g. when what was to use it wasn't saved after all. */
    public void delete(StoredBits bits) throws IOException {
        Path dir = root.resolve(bits.id());
        Files.deleteIfExists(dir.resolve(bits.name()));
        Files.deleteIfExists(dir);
    }

    static String safeName(String name) {
        String safe = name == null ? "" : name.strip().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^[-.]+|-+$", "");
        return safe.isEmpty() ? "data.bin" : safe;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
