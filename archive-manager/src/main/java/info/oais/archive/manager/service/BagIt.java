package info.oais.archive.manager.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a BagIt bag (RFC 8493, version 1.0) as a zip whose one top-level
 * folder is the bag: {@code bagit.txt}, {@code bag-info.txt}, the payload
 * under {@code data/}, {@code manifest-sha256.txt} with every payload
 * file's SHA-256 digest, any other tag files given, and
 * {@code tagmanifest-sha256.txt} with the tag files' digests. A receiver
 * checks the bag is complete and unchanged with any BagIt tool, e.g.
 * {@code bagit.py --validate}.
 */
public final class BagIt {

    private BagIt() {
    }

    /**
     * @param name    the bag's folder name
     * @param payload the payload files by path, each starting {@code data/}
     * @param info    {@code bag-info.txt} fields, in order; {@code Payload-Oxum} is added
     * @param tags    other tag files, by name, at the top of the bag
     */
    public static byte[] write(String name, Map<String, byte[]> payload, Map<String, String> info,
                               Map<String, byte[]> tags) throws IOException {
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
        StringBuilder bagInfo = new StringBuilder();
        Map<String, String> fields = new LinkedHashMap<>(info);
        fields.put("Payload-Oxum", octets + "." + payload.size());
        fields.forEach((k, v) -> bagInfo.append(k).append(": ").append(v.replaceAll("\\R", " ")).append('\n'));
        files.put("bag-info.txt", bagInfo.toString().getBytes(StandardCharsets.UTF_8));
        files.put("manifest-sha256.txt", manifest.toString().getBytes(StandardCharsets.UTF_8));
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
