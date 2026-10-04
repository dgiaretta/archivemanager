package info.oais.archive.manager.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a BagIt bag (RFC 8493, version 1.0) as a zip whose one top-level
 * folder is the bag: {@code bagit.txt}, {@code bag-info.txt}, the payload
 * under {@code data/}, {@code manifest-sha256.txt} with every payload
 * file's SHA-256 digest, any other tag files given, and
 * {@code tagmanifest-sha256.txt} with the tag files' digests. Payload
 * files can also be referred to rather than included: listed in
 * {@code fetch.txt} by the URL to fetch them from (RFC 8493 section 2.2.3),
 * and still in the manifest with their digest, and in {@code Payload-Oxum},
 * so the bag is complete once they're fetched. A receiver fetches them and
 * checks the bag is complete and unchanged with any BagIt tool, e.g.
 * {@code bagit.py --validate} (after fetching).
 */
public final class BagIt {

    private BagIt() {
    }

    /**
     * A payload file referred to rather than included.
     *
     * @param url    where to fetch it from
     * @param path   where it goes in the bag, starting {@code data/}
     * @param length its length in bytes
     * @param sha256 its SHA-256 digest, in lower-case hex
     */
    public record Fetched(String url, String path, long length, String sha256) {
    }

    /** A bag with every payload file included. */
    public static byte[] write(String name, Map<String, byte[]> payload, Map<String, String> info,
                               Map<String, byte[]> tags) throws IOException {
        return write(name, payload, List.of(), info, tags);
    }

    /**
     * @param name    the bag's folder name
     * @param payload the payload files included, by path, each starting {@code data/}
     * @param fetched the payload files referred to in {@code fetch.txt}
     * @param info    {@code bag-info.txt} fields, in order; {@code Payload-Oxum} is added
     * @param tags    other tag files, by name, at the top of the bag
     */
    public static byte[] write(String name, Map<String, byte[]> payload, List<Fetched> fetched,
                               Map<String, String> info, Map<String, byte[]> tags) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("bagit.txt", "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n".getBytes(StandardCharsets.UTF_8));

        long octets = 0;
        StringBuilder manifest = new StringBuilder();
        for (Map.Entry<String, byte[]> e : payload.entrySet()) {
            if (!e.getKey().startsWith("data/")) {
                throw new IllegalArgumentException("Payload files must be under data/: " + e.getKey());
            }
            octets += e.getValue().length;
            manifest.append(BitStore.sha256(e.getValue())).append("  ").append(encode(e.getKey())).append('\n');
        }
        StringBuilder fetch = new StringBuilder();
        for (Fetched f : fetched) {
            if (!f.path().startsWith("data/") || payload.containsKey(f.path())) {
                throw new IllegalArgumentException("A fetched file must be under data/, and not included too: "
                        + f.path());
            }
            octets += f.length();
            manifest.append(f.sha256()).append("  ").append(encode(f.path())).append('\n');
            fetch.append(f.url().replace(" ", "%20")).append(' ').append(f.length()).append(' ')
                    .append(encode(f.path())).append('\n');
        }
        StringBuilder bagInfo = new StringBuilder();
        Map<String, String> fields = new LinkedHashMap<>(info);
        fields.put("Payload-Oxum", octets + "." + (payload.size() + fetched.size()));
        fields.forEach((k, v) -> bagInfo.append(k).append(": ").append(v.replaceAll("\\R", " ")).append('\n'));
        files.put("bag-info.txt", bagInfo.toString().getBytes(StandardCharsets.UTF_8));
        files.put("manifest-sha256.txt", manifest.toString().getBytes(StandardCharsets.UTF_8));
        if (!fetched.isEmpty()) {
            files.put("fetch.txt", fetch.toString().getBytes(StandardCharsets.UTF_8));
        }
        files.putAll(tags);

        StringBuilder tagManifest = new StringBuilder();
        files.forEach((path, bytes) -> tagManifest.append(BitStore.sha256(bytes)).append("  ").append(path)
                .append('\n'));
        files.put("tagmanifest-sha256.txt", tagManifest.toString().getBytes(StandardCharsets.UTF_8));
        files.putAll(payload);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(name + "/" + e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** A path as a manifest gives it: {@code %}, CR and LF percent-encoded (RFC 8493 section 2.1.3). */
    static String encode(String path) {
        return path.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }
}
