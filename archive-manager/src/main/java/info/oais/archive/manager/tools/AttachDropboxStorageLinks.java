package info.oais.archive.manager.tools;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.tdb2.TDB2Factory;
import info.oais.archive.manager.rdf.Ns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Adds external file links to the RDF data store by matching filenames to a
 * record's {@code nam:recordNumber} and attaching a
 * {@code im:hasStorageLocation} URI to that record.
 *
 * <p>Usage examples:
 * <pre>
 *   java ... info.oais.archive.manager.tools.AttachDropboxStorageLinks \
 *     --tdb data/tdb2 \
 *     --dir "C:/Users/me/Dropbox/records" \
 *     --base-url "https://www.dropbox.com/your-folder"
 * </pre>
 *
 * The script records the full file URL as an external resource (URI object), not
 * as a literal text value, because this app treats external links as graph links,
 * not as node-to-node RDF resource relationships.
 */
public class AttachDropboxStorageLinks {

    private static final Pattern RECORD_NUMBER_PATTERN = Pattern.compile("(?i)R\\d+");
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    public static void main(String[] args) throws Exception {
        Options options = parseArgs(args);
        if (options.help) {
            printUsage();
            return;
        }

        if (options.sourceDir == null || options.sourceDir.isBlank()) {
            throw new IllegalArgumentException("Missing required --dir argument");
        }
        if (options.baseUrl == null || options.baseUrl.isBlank()) {
            throw new IllegalArgumentException("Missing required --base-url argument");
        }

        Path sourceDir = Path.of(options.sourceDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }

        String baseUrl = normalizeDropboxBaseUrl(options.baseUrl);

        Path tdbLocation = Path.of(options.tdbLocation).toAbsolutePath().normalize();
        if (!Files.isDirectory(tdbLocation)) {
            System.out.println("TDB2 directory not found at " + tdbLocation + "; creating it.");
            Files.createDirectories(tdbLocation);
        }

        Dataset dataset = TDB2Factory.connectDataset(tdbLocation.toString());
        boolean committed = false;
        dataset.begin(ReadWrite.WRITE);
        try {
            Model data = dataset.getNamedModel("urn:x-archive:data");
            Property recordNumber = data.createProperty(Ns.NAM + "recordNumber");
            Property storageLocation = data.createProperty(Ns.IM + "hasStorageLocation");

            Map<String, List<Resource>> recordsByNumber = loadRecordsByNumber(data, recordNumber);

            int filesChecked = 0;
            int filesMatched = 0;
            int recordsUpdated = 0;
            int missingRecords = 0;
            int duplicatesSkipped = 0;

            List<String> planned = new ArrayList<>();
            for (Path file : findFiles(sourceDir, options.extensions)) {
                filesChecked++;
                String recordNumberLiteral = extractRecordNumber(file.getFileName().toString());
                if (recordNumberLiteral == null) {
                    continue;
                }
                filesMatched++;

                String normalized = recordNumberLiteral.toUpperCase(Locale.ROOT);
                List<Resource> subjects = recordsByNumber.getOrDefault(normalized, List.of());
                if (subjects.isEmpty()) {
                    missingRecords++;
                    System.out.println("No record found for " + recordNumberLiteral + " in " + file.getFileName());
                    continue;
                }

                String relativePath = sourceDir.relativize(file).toString().replace('\\', '/');
                String fileUrl = appendDropboxRelativePath(baseUrl, relativePath);
                for (Resource subject : subjects) {
                    boolean alreadyLinked = hasLink(data, subject, storageLocation, fileUrl);
                    if (alreadyLinked) {
                        duplicatesSkipped++;
                        continue;
                    }
                    if (options.dryRun) {
                        planned.add("Would attach " + fileUrl + " to " + subject.getURI() + " (record " + recordNumberLiteral + ")");
                    } else {
                        subject.addProperty(storageLocation, data.createResource(fileUrl));
                        recordsUpdated++;
                    }
                }
            }

            if (options.dryRun) {
                System.out.println("Dry run only. No triples were written.");
                for (String message : planned) {
                    System.out.println(message);
                }
                System.out.println("Files checked: " + filesChecked);
                System.out.println("Files matched to a record number: " + filesMatched);
                System.out.println("Missing records: " + missingRecords);
                System.out.println("Duplicate links skipped: " + duplicatesSkipped);
                dataset.abort();
            } else {
                dataset.commit();
                committed = true;
                System.out.println("Added storage links for " + recordsUpdated + " record links across " + filesMatched + " matched files.");
                System.out.println("Files checked: " + filesChecked);
                System.out.println("Missing records: " + missingRecords);
                System.out.println("Duplicate links skipped: " + duplicatesSkipped);
            }
        } finally {
            if (dataset.isInTransaction()) {
                if (!committed) {
                    dataset.abort();
                }
                dataset.end();
            }
            dataset.close();
        }
    }

    private static Map<String, List<Resource>> loadRecordsByNumber(Model data, Property recordNumber) {
        Map<String, List<Resource>> result = new HashMap<>();
        for (Statement stmt : data.listStatements(null, recordNumber, (String) null).toList()) {
            String raw = stmt.getObject().asLiteral().getString();
            String key = raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
            if (key == null || key.isBlank()) {
                continue;
            }
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(stmt.getSubject());
        }
        return result;
    }

    private static boolean hasLink(Model data, Resource subject, Property storageLocation, String url) {
        return data.listStatements(subject, storageLocation, (String) null).toList().stream()
                .anyMatch(stmt -> stmt.getObject().isResource()
                        && Objects.equals(stmt.getObject().asResource().getURI(), url));
    }

    private static List<Path> findFiles(Path root, Set<String> extensions) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        if (dot < 0 || dot == name.length() - 1) {
                            return false;
                        }
                        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                        return extensions.contains(ext);
                    })
                    .forEach(result::add);
        }
        return result;
    }

    private static String extractRecordNumber(String fileName) {
        Matcher matcher = RECORD_NUMBER_PATTERN.matcher(fileName);
        return matcher.find() ? matcher.group() : null;
    }

    private static String normalizeDropboxBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String normalized = baseUrl.trim();
        normalized = normalized.replace("&dl=0", "&dl=1");
        normalized = normalized.replace("?dl=0", "?dl=1");
        normalized = normalized.replace("&dl=1", "&dl=1");
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String appendDropboxRelativePath(String baseUrl, String relativePath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return relativePath;
        }
        int queryIndex = baseUrl.indexOf('?');
        if (queryIndex >= 0) {
            String before = baseUrl.substring(0, queryIndex);
            String after = baseUrl.substring(queryIndex + 1);
            return before + "/" + relativePath + "?" + after;
        }
        return baseUrl + "/" + relativePath;
    }

    private static Options parseArgs(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean help = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                help = true;
                continue;
            }
            if (arg.startsWith("--")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    values.put(arg.substring(2), args[++i]);
                } else {
                    values.put(arg.substring(2), "true");
                }
            }
        }
        Options options = new Options();
        options.help = help;
        options.tdbLocation = values.getOrDefault("tdb", "data/tdb2");
        options.sourceDir = values.getOrDefault("dir", "");
        options.baseUrl = values.getOrDefault("base-url", "");
        options.dryRun = Boolean.parseBoolean(values.getOrDefault("dry-run", "false"));
        options.extensions = new HashSet<>(DEFAULT_EXTENSIONS);
        return options;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  AttachDropboxStorageLinks --dir <local-folder> --base-url <dropbox-folder-url> [--tdb <tdb-folder>] [--dry-run]");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  AttachDropboxStorageLinks --dir " + '"' + "C:/Users/me/Dropbox/records" + '"' + " --base-url " + '"' + "https://www.dropbox.com/scl/fi/abc123/records" + '"' + " --tdb data/tdb2");
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - Filename must contain a record number like R00003 or R00003_A.jpg");
        System.out.println("  - A matching record is found by nam:recordNumber in the TDB2 data graph.");
        System.out.println("  - The property added is im:hasStorageLocation, which points to the external URL.");
    }

    private static final class Options {
        boolean help;
        String tdbLocation;
        String sourceDir;
        String baseUrl;
        Set<String> extensions;
        boolean dryRun;
    }
}
