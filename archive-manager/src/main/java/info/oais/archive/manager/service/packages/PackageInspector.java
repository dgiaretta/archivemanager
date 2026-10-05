package info.oais.archive.manager.service.packages;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Looks inside a packaged AIP -- a BagIt bag, zipped, 7-Zipped or tarred, as
 * written by Eternal / Archivematica (or by this archive) -- and finds where
 * each component OAIS requires of an AIP is in it, as a person would when
 * checking one by hand:
 * <ul>
 *   <li>the bag itself: its declaration, its manifests (every file's SHA-256
 *       checked against them) and {@code Payload-Oxum};</li>
 *   <li>the upload spreadsheet carried in the package ({@code metadata.csv}),
 *       with the definitions of its columns ({@code metadata_definition.xml}),
 *       whether its columns have the spreadsheet's own names (Rights,
 *       Provenance, FixityHashSHA256, Semantics, OtherRI ...) or Dublin Core
 *       ones ({@code dc.rights}, {@code dc.relation} ...);</li>
 *   <li>the METS file's PREMIS: each file's format (with its PRONOM
 *       identifier), size and digest, and the events recorded for it;</li>
 *   <li>{@code checksum_audit.xml} (the checksums of the original and of what
 *       entered the AIP), {@code preservation_description_information.xml},
 *       and the technical metadata of the original before normalisation.</li>
 * </ul>
 * The result is a list of {@link Finding}s, one per AIP component, each
 * saying where in the package it is and what it says -- or that it's missing.
 */
public final class PackageInspector {

    /** Text files kept for reading, up to this size; every file is hashed whatever its size. */
    private static final long KEEP_TEXT = 8L * 1024 * 1024;

    private PackageInspector() {
    }

    /** A file in the package. */
    public record PackageFile(String path, long size, String sha256, String md5) {
    }

    /** Whether the bag's files match its manifest. */
    public record BagCheck(String manifest, int listed, int matched, List<String> mismatched, List<String> missing,
                           List<String> unlisted, String oxumDeclared, String oxumActual) {

        public boolean valid() {
            return listed > 0 && matched == listed && mismatched.isEmpty() && missing.isEmpty() && unlisted.isEmpty()
                    && (oxumDeclared == null || oxumDeclared.equals(oxumActual));
        }
    }

    /**
     * Where a component is in the package, and what it says there.
     *
     * @param link a page about it, e.g. the archive's page of a separate AIP it identifies; or null
     */
    public record Source(String where, String value, String link) {

        public Source(String where, String value) {
            this(where, value, null);
        }
    }

    /**
     * One component of an AIP.
     *
     * @param required whether OAIS requires it (Other Representation Information is optional)
     * @param sources  where it is in the package
     * @param gaps     the upload spreadsheet's entries expected to hold it that aren't filled in, each with why
     *                 (e.g. "metadata.csv: FixityHashSHA256" -- "blank in this package")
     */
    public record Finding(String component, int depth, boolean required, boolean found, List<Source> sources,
                          String note, List<Source> gaps) {

        /** A row of the mapping: where, what, and whether it's a gap. */
        public record Row(String where, String value, String link, boolean gap) {
        }

        /** Its rows: where it's found, then the expected entries that aren't filled in. */
        public List<Row> rows() {
            List<Row> rows = new ArrayList<>();
            sources.forEach(s -> rows.add(new Row(s.where(), s.value(), s.link(), false)));
            gaps.forEach(g -> rows.add(new Row(g.where(), g.value(), null, true)));
            return rows;
        }
    }

    /** What a package holds. */
    public record Inspection(String archiveFormat, long size, String bagName, String bagitVersion,
                             Map<String, String> bagInfo, BagCheck bagCheck, List<PackageFile> files,
                             List<Finding> findings, String spreadsheet, List<String> spreadsheetColumns,
                             Map<String, String> columnDefinitions, List<String> notes) {

        /** How many required components aren't in the package. */
        public long missing() {
            return findings.stream().filter(f -> f.required() && !f.found()).count();
        }

        /** How many of the upload spreadsheet's expected entries, for required components, aren't filled in. */
        public long unfilled() {
            return findings.stream().filter(Finding::required).mapToLong(f -> f.gaps().size()).sum();
        }
    }

    /** Reads the package {@code file} -- named {@code name}, for its format -- and maps it to the AIP components. */
    public static Inspection inspect(Path file, String name) throws IOException {
        Contents contents = read(file, name);
        return new Analysis(contents).run();
    }

    // ------------------------------------------------------------------
    // Reading the archive
    // ------------------------------------------------------------------

    private record Contents(String format, long size, List<PackageFile> files, Map<String, byte[]> texts) {
    }

    @SuppressWarnings("deprecation") // SevenZFile(File): the builder arrives after Commons Compress 1.25
    private static Contents read(Path file, String name) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        List<PackageFile> files = new ArrayList<>();
        Map<String, byte[]> texts = new LinkedHashMap<>();
        String format;
        if (lower.endsWith(".7z")) {
            format = "7-Zip";
            try (SevenZFile seven = new SevenZFile(file.toFile())) {
                for (SevenZArchiveEntry e; (e = seven.getNextEntry()) != null; ) {
                    if (!e.isDirectory() && e.hasStream()) {
                        try (InputStream in = seven.getInputStream(e)) {
                            add(e.getName(), in, files, texts);
                        }
                    }
                }
            } catch (IOException e) {
                throw new IOException("Couldn't read the 7-Zip package: " + e.getMessage()
                        + (String.valueOf(e.getMessage()).contains("xz") || String.valueOf(e.getMessage()).contains("LZMA")
                        ? " (its compression, LZMA, needs a library the archive doesn't have; BZip2, Deflate and "
                        + "uncompressed 7-Zip packages can be read)" : ""), e);
            } catch (NoClassDefFoundError e) {
                throw new IOException("Couldn't read the 7-Zip package: its compression (LZMA) needs the XZ library, "
                        + "which the archive doesn't have", e);
            }
        } else if (lower.endsWith(".zip")) {
            format = "ZIP";
            try (ZipFile zip = new ZipFile(file.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (!e.isDirectory()) {
                        try (InputStream in = zip.getInputStream(e)) {
                            add(e.getName(), in, files, texts);
                        }
                    }
                }
            }
        } else if (lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            format = lower.endsWith(".tar") ? "tar" : "tar (gzip)";
            try (InputStream raw = Files.newInputStream(file);
                 InputStream in = lower.endsWith(".tar") ? raw : new GzipCompressorInputStream(raw);
                 ArchiveInputStream<?> tar = new TarArchiveInputStream(in)) {
                for (ArchiveEntry e; (e = tar.getNextEntry()) != null; ) {
                    if (!e.isDirectory()) {
                        add(e.getName(), tar, files, texts);
                    }
                }
            }
        } else {
            throw new IOException("A package must be a .7z, .zip, .tar or .tar.gz file, not " + name);
        }
        return new Contents(format, Files.size(file), files, texts);
    }

    private static void add(String rawName, InputStream in, List<PackageFile> files, Map<String, byte[]> texts)
            throws IOException {
        String path = rawName.replace('\\', '/').replaceFirst("^\\./", "");
        MessageDigest sha256 = digest("SHA-256");
        MessageDigest md5 = digest("MD5");
        boolean keep = isText(path);
        ByteArrayOutputStream kept = keep ? new ByteArrayOutputStream() : null;
        byte[] buffer = new byte[64 * 1024];
        long size = 0;
        for (int n; (n = in.read(buffer)) > 0; ) {
            sha256.update(buffer, 0, n);
            md5.update(buffer, 0, n);
            if (kept != null && size + n <= KEEP_TEXT) {
                kept.write(buffer, 0, n);
            }
            size += n;
        }
        files.add(new PackageFile(path, size, HexFormat.of().formatHex(sha256.digest()),
                HexFormat.of().formatHex(md5.digest())));
        if (kept != null && size <= KEEP_TEXT) {
            texts.put(path, kept.toByteArray());
        }
    }

    private static boolean isText(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".txt") || p.endsWith(".csv") || p.endsWith(".xml") || p.endsWith(".json")
                || p.endsWith(".log") || p.endsWith(".html");
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Finding the components
    // ------------------------------------------------------------------

    /** A PREMIS object of the METS file, with the events recorded with it. */
    private record PremisObject(String originalName, String size, String digest, String digestAlgorithm,
                                String formatName, String formatVersion, String registryKey, List<String> events) {
    }

    private static final class Analysis {
        private final Contents contents;
        private final List<String> notes = new ArrayList<>();
        private String root = "";

        Analysis(Contents contents) {
            this.contents = contents;
        }

        Inspection run() {
            // The bag is the folder holding bagit.txt; paths below are relative to it.
            contents.files().stream().map(PackageFile::path).filter(p -> p.equals("bagit.txt") || p.endsWith("/bagit.txt"))
                    .min(java.util.Comparator.comparingInt(String::length))
                    .ifPresentOrElse(p -> root = p.substring(0, p.length() - "bagit.txt".length()),
                            () -> notes.add("It isn't a BagIt bag: there's no bagit.txt."));
            String bagName = root.isEmpty() ? null : root.replaceAll("/$", "").replaceAll(".*/", "");
            Map<String, String> bagit = fields(text("bagit.txt").orElse(""));
            Map<String, String> bagInfo = fields(text("bag-info.txt").orElse(""));
            BagCheck bagCheck = checkBag(bagInfo);

            String spreadsheetPath = find(p -> p.startsWith("data/") && p.endsWith("/metadata.csv")).orElse(null);
            List<Map<String, String>> spreadsheet = spreadsheetPath == null ? List.of()
                    : rows(text(spreadsheetPath).orElse(""));
            List<String> columns = spreadsheet.isEmpty() ? List.of() : List.copyOf(spreadsheet.get(0).keySet());
            String definitionsPath = find(p -> p.endsWith("/metadata_definition.xml")).orElse(null);
            Map<String, String> definitions = definitionsPath == null ? Map.of()
                    : definitions(text(definitionsPath).orElse(""));
            String metsPath = find(p -> p.matches("data/METS\\.[^/]*\\.xml")).orElse(null);
            List<PremisObject> premis = metsPath == null ? List.of() : premis(text(metsPath).orElse(""));

            // The Data Objects: the spreadsheet's file names, else the files in data/objects.
            List<String> objects = new ArrayList<>();
            for (Map<String, String> row : spreadsheet) {
                String name = value(row, "filename", "objectname");
                if (name != null) {
                    objects.add(name.startsWith("data/") ? name : "data/" + name);
                }
            }
            if (objects.isEmpty()) {
                contents.files().stream().map(f -> relative(f.path())).filter(p -> p != null && p.startsWith("data/objects/")
                        && !p.startsWith("data/objects/metadata/") && !p.startsWith("data/objects/submissionDocumentation/"))
                        .forEach(objects::add);
            }

            List<Finding> findings = new ArrayList<>();
            String sheet = spreadsheetPath == null ? null : "metadata.csv";

            // Packaging Information
            List<Source> packaging = new ArrayList<>();
            packaging.add(new Source("the package file", contents.format() + ", " + contents.size() + " bytes"));
            if (bagName != null) {
                packaging.add(new Source("bagit.txt", "BagIt " + bagit.getOrDefault("BagIt-Version", "?")
                        + ", tag files in " + bagit.getOrDefault("Tag-File-Character-Encoding", "?")));
                packaging.add(new Source(bagCheck.manifest(), bagCheck.valid()
                        ? "all " + bagCheck.listed() + " payload files match their checksums"
                        : bagCheck.matched() + " of " + bagCheck.listed() + " payload files match their checksums"));
                if (find(p -> p.startsWith("tagmanifest-")).isPresent()) {
                    packaging.add(new Source("tagmanifest", "checksums of the tag files"));
                }
                if (bagCheck.oxumDeclared() != null) {
                    packaging.add(new Source("bag-info.txt: Payload-Oxum", bagCheck.oxumDeclared()
                            + (bagCheck.oxumDeclared().equals(bagCheck.oxumActual()) ? " (matches)"
                            : " (the payload is " + bagCheck.oxumActual() + ")")));
                }
            }
            metsPath(metsPath).ifPresent(m -> packaging.add(new Source(m, "METS: the package's structure and "
                    + "each file's technical and preservation metadata")));
            findings.add(new Finding("Packaging Information", 0, true, bagName != null, packaging,
                    "how the components are bound together and extracted: the archive file, the bag and its manifests",
                    bagName == null ? List.of(new Source("bagit.txt", "the package isn't a BagIt bag")) : List.of()));

            Map<String, String> row = spreadsheet.isEmpty() ? Map.of() : spreadsheet.get(0);
            Expected expect = new Expected(row, sheet, columns, definitions);

            // Package Description
            List<Source> description = new ArrayList<>();
            if (sheet != null) {
                String summary = join(value(row, "originaltitle", "title"), value(row, "description"));
                description.add(new Source(sheet, summary == null ? "the descriptive fields of the record" : summary));
            }
            metsPath(metsPath).ifPresent(m -> {
                Map<String, String> dc = dublinCore(text(m).orElse(""));
                if (!dc.isEmpty() && join(dc.get("title"), dc.get("description")) != null) {
                    description.add(new Source(m + ": Dublin Core", join(dc.get("title"), dc.get("description"))));
                }
            });
            find(p -> p.equals("data/README.html")).ifPresent(p -> description.add(new Source(relative(p),
                    "an explanation of the package for a person opening it")));
            findings.add(new Finding("Package Description", 0, true, !description.isEmpty(), description, null,
                    sheet == null ? List.of(new Source("metadata.csv", "the package has no upload spreadsheet"))
                            : List.of()));

            // Data Object
            List<Source> data = new ArrayList<>();
            for (String object : objects) {
                Optional<PackageFile> f = file(object);
                data.add(new Source(sheet != null ? sheet + ": filename" : "data/objects",
                        object + (f.isPresent() ? " (" + f.get().size() + " bytes)" : " -- not in the package")));
            }
            boolean dataFound = objects.stream().anyMatch(o -> file(o).isPresent());
            findings.add(new Finding("Data Object", 1, true, dataFound, data, null, dataFound ? List.of()
                    : List.of(new Source("metadata.csv: filename", "no file it names is in data/objects"))));

            // Structure Representation Information: metadata.csv Format + FormatInfo, and the PREMIS format
            List<Source> structure = new ArrayList<>();
            List<Source> structureGaps = new ArrayList<>();
            expect.column(structure, structureGaps, "Format", "format");
            expect.column(structure, structureGaps, "FormatInfo", "formatinfo");
            for (String object : objects) {
                premisFor(premis, object).ifPresent(o -> structure.add(new Source(metsPath(metsPath).orElse("METS")
                        + ": PREMIS format", join(o.formatName(), o.formatVersion() == null || o.formatVersion().isBlank()
                        ? null : "version " + o.formatVersion(), o.registryKey() == null ? null
                        : "PRONOM " + o.registryKey()))));
            }
            findings.add(new Finding("Structure Representation Information", 2, true, !structure.isEmpty(), structure,
                    "a format's registry identifier (PRONOM) points to its specification", structureGaps));

            // Semantic Representation Information: metadata.csv Semantics (perhaps the UUID of a separate AIP
            // holding it) + Language
            List<Source> semantic = new ArrayList<>();
            List<Source> semanticGaps = new ArrayList<>();
            expect.column(semantic, semanticGaps, "Semantics", "semantics");
            semantic.replaceAll(s -> s.where().endsWith(": Semantics") || s.where().endsWith(": dc.semantics")
                    ? new Source(s.where(), s.value() + (uuid(s.value()) == null ? "" : " -- the identifier of the "
                    + "separate AIP that holds the Semantic Representation Information")) : s);
            expect.column(semantic, semanticGaps, "Language", "language");
            expect.extra(semantic, "anotherlanguage", "idioma");
            findings.add(new Finding("Semantic Representation Information", 2, true, !semantic.isEmpty(), semantic,
                    "what the data means: often in a separate AIP, which Semantics identifies", semanticGaps));

            // Other Representation Information: metadata.csv OtherRI (optional)
            List<Source> other = new ArrayList<>();
            List<Source> otherGaps = new ArrayList<>();
            expect.column(other, otherGaps, "OtherRI", "otherri");
            findings.add(new Finding("Other Representation Information", 2, false, !other.isEmpty(), other,
                    "optional: e.g. software or documentation needed to use the data", otherGaps));

            Map<String, String> pdi = pdi(find(p -> p.endsWith("/preservation_description_information.xml"))
                    .flatMap(this::text).orElse(""));
            String pdiPath = find(p -> p.endsWith("/preservation_description_information.xml")).map(this::relative)
                    .orElse(null);
            Optional<String> audit = find(p -> p.endsWith("/checksum_audit.xml"));

            // PDI: Fixity -- metadata.csv FixityHashSHA256, and what the package itself records
            List<Source> fixity = new ArrayList<>();
            List<Source> fixityGaps = new ArrayList<>();
            expect.column(fixity, fixityGaps, "FixityHashSHA256", "fixityhashsha256");
            for (String object : objects) {
                Optional<PackageFile> f = file(object);
                String inManifest = manifestEntry(object);
                if (inManifest != null) {
                    fixity.add(new Source(bagCheck.manifest() + ": " + object, inManifest
                            + (f.isPresent() && inManifest.equalsIgnoreCase(f.get().sha256()) ? " (verified: the file "
                            + "matches)" : f.isPresent() ? " (DOESN'T match the file, " + f.get().sha256() + ")" : "")));
                }
                premisFor(premis, object).filter(o -> o.digest() != null).ifPresent(o -> fixity.add(new Source(
                        metsPath(metsPath).orElse("METS") + ": PREMIS " + o.digestAlgorithm(), o.digest())));
                String declared = value(row, "fixityhashsha256");
                if (declared != null && f.isPresent() && !declared.strip().equalsIgnoreCase(f.get().sha256())) {
                    notes.add("The spreadsheet's FixityHashSHA256 for " + object + " is the original file's, which "
                            + "differs from the file in the package -- e.g. if it was normalised on ingest.");
                }
            }
            audit.ifPresent(a -> fixity.add(new Source(relative(a), checksumAudit(text(a).orElse("")))));
            pdiValue(pdi, pdiPath, fixity, "pdifixity");
            findings.add(new Finding("PDI: Fixity Information", 1, true, !fixity.isEmpty(), fixity, null, fixityGaps));

            // PDI: Provenance -- metadata.csv Provenance + Creator + Publisher + Contributor, and more
            List<Source> provenance = new ArrayList<>();
            List<Source> provenanceGaps = new ArrayList<>();
            expect.column(provenance, provenanceGaps, "Provenance", "provenance");
            expect.column(provenance, provenanceGaps, "Creator", "creator");
            expect.column(provenance, provenanceGaps, "Publisher", "publisher");
            expect.column(provenance, provenanceGaps, "Contributor", "contributor");
            expect.extra(provenance, "date", "source", "origem", "receivedon", "enteredon");
            for (String object : objects) {
                premisFor(premis, object).filter(o -> !o.events().isEmpty()).ifPresent(o -> provenance.add(new Source(
                        metsPath(metsPath).orElse("METS") + ": PREMIS events", String.join("; ", o.events()))));
            }
            find(p -> p.endsWith("/file_before_processed_info.xml")).ifPresent(p -> provenance.add(new Source(
                    relative(p), "technical metadata of the original file, before it was normalised on ingest")));
            pdiValue(pdi, pdiPath, provenance, "pdiprovenanceinformation");
            findings.add(new Finding("PDI: Provenance Information", 1, true, !provenance.isEmpty(), provenance, null,
                    provenanceGaps));

            // PDI: Context -- metadata.csv Relation
            List<Source> context = new ArrayList<>();
            List<Source> contextGaps = new ArrayList<>();
            expect.column(context, contextGaps, "Relation", "relation");
            pdiValue(pdi, pdiPath, context, "pdicontextinformation");
            findings.add(new Finding("PDI: Context Information", 1, true, !context.isEmpty(), context, null,
                    contextGaps));

            // PDI: Reference -- the AIP's name and identifiers
            List<Source> reference = new ArrayList<>();
            if (bagName != null) {
                reference.add(new Source("the AIP's name", bagName));
            }
            if (bagInfo.containsKey("External-Identifier")) {
                reference.add(new Source("bag-info.txt: External-Identifier", bagInfo.get("External-Identifier")));
            }
            expect.extra(reference, "recordno");
            if (value(row, "recordno") == null && value(row, "subject") != null && definitions.getOrDefault("dc.subject", "")
                    .toLowerCase(Locale.ROOT).contains("identify")) {
                // Older packages fold the record number into dc.subject, first (metadata_definition.xml says so).
                reference.add(new Source(sheet + ": dc.subject, first part (the record number, as "
                        + "metadata_definition.xml defines it)", value(row, "subject").split(";")[0].strip()));
            }
            expect.extra(reference, "identifier");
            pdiValue(pdi, pdiPath, reference, "pdireferenceinformation");
            findings.add(new Finding("PDI: Reference Information", 1, true, !reference.isEmpty(), reference, null,
                    reference.isEmpty() ? List.of(new Source("the AIP's name", "the package has no named bag"))
                            : List.of()));

            // PDI: Access Rights -- metadata.csv Rights
            List<Source> rights = new ArrayList<>();
            List<Source> rightsGaps = new ArrayList<>();
            expect.column(rights, rightsGaps, "Rights", "rights");
            pdiValue(pdi, pdiPath, rights, "pdiaccessrightsinformation");
            findings.add(new Finding("PDI: Access Rights Information", 1, true, !rights.isEmpty(), rights, null,
                    rightsGaps));

            if (spreadsheetPath == null) {
                notes.add("There's no upload spreadsheet (metadata.csv) in the package.");
            } else if (spreadsheet.size() > 1) {
                notes.add("The upload spreadsheet has " + spreadsheet.size() + " rows; the components are shown for "
                        + "the first.");
            }
            if (!pdi.isEmpty() && pdi.values().stream().allMatch(Analysis::placeholder)) {
                notes.add("preservation_description_information.xml has a field for each kind of PDI, but holds "
                        + "only placeholders in them (" + pdi.values().iterator().next() + ").");
            }
            return new Inspection(contents.format(), contents.size(), bagName, bagit.get("BagIt-Version"), bagInfo,
                    bagCheck, contents.files(), findings, spreadsheetPath == null ? null : relative(spreadsheetPath),
                    columns, definitions, notes);
        }

        /** The upload spreadsheet's row, and how to find an entry in it -- or say why it isn't there. */
        private final class Expected {
            private final Map<String, String> row;
            private final String sheet;
            private final List<String> columns;
            private final Map<String, String> definitions;

            Expected(Map<String, String> row, String sheet, List<String> columns, Map<String, String> definitions) {
                this.row = row;
                this.sheet = sheet;
                this.columns = columns;
                this.definitions = definitions;
            }

            /**
             * The entry {@code label} (any column whose name, as compared, is one
             * of {@code names}, e.g. "Rights" or "dc.rights"): into
             * {@code sources} if it's filled in, else into {@code gaps} with why
             * not -- the package has no spreadsheet; the column is blank; it's
             * defined in metadata_definition.xml but left out of metadata.csv, as
             * packages leave out blank columns; or the spreadsheet used for this
             * package has no such column.
             */
            void column(List<Source> sources, List<Source> gaps, String label, String... names) {
                List<String> wanted = List.of(names);
                boolean filled = false;
                for (Map.Entry<String, String> e : row.entrySet()) {
                    if (wanted.contains(normalise(e.getKey())) && filled(e.getValue())) {
                        sources.add(new Source(sheet + ": " + e.getKey(), e.getValue().strip()));
                        filled = true;
                    }
                }
                if (filled) {
                    return;
                }
                if (sheet == null) {
                    gaps.add(new Source("metadata.csv: " + label, "the package has no upload spreadsheet"));
                    return;
                }
                for (String c : columns) {
                    if (wanted.contains(normalise(c))) {
                        gaps.add(new Source("metadata.csv: " + c, "blank in this package"));
                        return;
                    }
                }
                for (String d : definitions.keySet()) {
                    if (wanted.contains(normalise(d))) {
                        gaps.add(new Source("metadata.csv: " + d, "defined in metadata_definition.xml, but not in "
                                + "metadata.csv, so it was left blank"));
                        return;
                    }
                }
                gaps.add(new Source("metadata.csv: " + label, "the upload spreadsheet used for this package has no "
                        + label + " column"));
            }

            /** Entries that add to a component when filled in, but aren't expected of it. */
            void extra(List<Source> sources, String... names) {
                for (String name : names) {
                    for (Map.Entry<String, String> e : row.entrySet()) {
                        if (normalise(e.getKey()).equals(name) && filled(e.getValue())) {
                            sources.add(new Source(sheet + ": " + e.getKey(), e.getValue().strip()));
                        }
                    }
                }
            }

            private boolean filled(String value) {
                return value != null && !value.isBlank() && !value.strip().equals("0");
            }
        }

        private static void pdiValue(Map<String, String> pdi, String path, List<Source> into, String field) {
            String v = pdi.get(field);
            if (v != null && !placeholder(v)) {
                into.add(new Source(path + ": " + field, v));
            }
        }

        private static boolean placeholder(String v) {
            return v.isBlank() || v.strip().matches("[\\p{L} ]*:");
        }

        private static String value(Map<String, String> row, String... columns) {
            for (String c : columns) {
                for (Map.Entry<String, String> e : row.entrySet()) {
                    if (normalise(e.getKey()).equals(c) && e.getValue() != null && !e.getValue().isBlank()) {
                        return e.getValue().strip();
                    }
                }
            }
            return null;
        }

        /** A column's name as compared: lower case, without "dc." or anything but letters and digits. */
        private static String normalise(String column) {
            return column.toLowerCase(Locale.ROOT).replaceFirst("^dc\\.", "").replaceAll("[^a-z0-9]", "");
        }

        /** The first file whose path within the bag passes {@code test}: its full path in the package. */
        private Optional<String> find(Predicate<String> test) {
            return contents.files().stream().map(PackageFile::path)
                    .filter(p -> relative(p) != null && test.test(relative(p))).findFirst();
        }

        private Optional<String> metsPath(String path) {
            return Optional.ofNullable(path).map(this::relative);
        }

        /** {@code path} relative to the bag, or null if it's outside it. */
        private String relative(String path) {
            return path.startsWith(root) ? path.substring(root.length()) : null;
        }

        private Optional<PackageFile> file(String bagPath) {
            return contents.files().stream().filter(f -> f.path().equals(root + bagPath)).findFirst();
        }

        private Optional<String> text(String path) {
            String full = contents.texts().containsKey(path) ? path : root + path;
            byte[] bytes = contents.texts().get(full);
            if (bytes == null) {
                return Optional.empty();
            }
            String s = new String(bytes, StandardCharsets.UTF_8);
            return Optional.of(s.startsWith("﻿") ? s.substring(1) : s);
        }

        private String manifestName() {
            for (String algorithm : List.of("sha256", "sha512", "sha1", "md5")) {
                if (file("manifest-" + algorithm + ".txt").isPresent()) {
                    return "manifest-" + algorithm + ".txt";
                }
            }
            return null;
        }

        private Map<String, String> manifest;

        private String manifestEntry(String bagPath) {
            return manifest == null ? null : manifest.get(bagPath);
        }

        private BagCheck checkBag(Map<String, String> bagInfo) {
            String name = manifestName();
            manifest = new LinkedHashMap<>();
            if (name == null) {
                return new BagCheck("no payload manifest", 0, 0, List.of(), List.of(), List.of(), null, null);
            }
            for (String line : text(name).orElse("").split("\\R")) {
                int space = line.indexOf(' ');
                if (space > 0) {
                    manifest.put(line.substring(space).strip().replaceFirst("^\\*", "").replace("%0A", "\n")
                            .replace("%0D", "\r").replace("%25", "%"), line.substring(0, space).strip().toLowerCase());
                }
            }
            boolean md5 = name.contains("md5");
            int matched = 0;
            List<String> mismatched = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, String> e : manifest.entrySet()) {
                Optional<PackageFile> f = file(e.getKey());
                if (f.isEmpty()) {
                    missing.add(e.getKey());
                } else if (name.contains("sha256") || md5) {
                    if ((md5 ? f.get().md5() : f.get().sha256()).equalsIgnoreCase(e.getValue())) {
                        matched++;
                    } else {
                        mismatched.add(e.getKey());
                    }
                } else {
                    matched++; // an algorithm not computed here: present, not checked
                }
            }
            List<String> unlisted = new ArrayList<>();
            long octets = 0;
            int streams = 0;
            for (PackageFile f : contents.files()) {
                String p = relative(f.path());
                if (p != null && p.startsWith("data/")) {
                    octets += f.size();
                    streams++;
                    if (!manifest.containsKey(p)) {
                        unlisted.add(p);
                    }
                }
            }
            return new BagCheck(name, manifest.size(), matched, mismatched, missing, unlisted,
                    bagInfo.get("Payload-Oxum"), octets + "." + streams);
        }
    }

    // ------------------------------------------------------------------
    // Reading the files in it
    // ------------------------------------------------------------------

    /** "Label: value" lines, as in bagit.txt and bag-info.txt. */
    static Map<String, String> fields(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                fields.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
            }
        }
        return fields;
    }

    /** CSV rows (RFC 4180: quoted fields may hold commas, quotes and line breaks), as maps keyed by the header. */
    static List<Map<String, String>> rows(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                field.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        records.removeIf(r -> r.stream().allMatch(String::isBlank));
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<String> r : records.subList(1, records.size())) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                String key = header.get(i);
                String value = i < r.size() ? r.get(i) : "";
                row.merge(key, value, (a, b) -> a.isBlank() ? b : b.isBlank() ? a : a + "; " + b);
            }
            rows.add(row);
        }
        return rows;
    }

    private static Document xml(String text) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Element> elements(Node parent, String localName) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent instanceof Document d ? d.getElementsByTagNameNS("*", localName)
                : ((Element) parent).getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add((Element) nodes.item(i));
        }
        return found;
    }

    private static String first(Element parent, String localName) {
        List<Element> found = elements(parent, localName);
        return found.isEmpty() ? null : found.get(0).getTextContent().strip();
    }

    /** The column definitions of metadata_definition.xml: each column's name and what it holds. */
    static Map<String, String> definitions(String text) {
        Map<String, String> definitions = new LinkedHashMap<>();
        Document d = xml(text);
        if (d == null) {
            return definitions;
        }
        for (Element metadata : elements(d, "metadata")) {
            for (Node n = metadata.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e) {
                    String name = first(e, "originalName");
                    String translation = first(e, "translation");
                    String definition = first(e, "definition");
                    if (name == null) {
                        continue;
                    }
                    String what = join(translation == null || translation.isBlank() ? null : translation, definition);
                    definitions.merge(name, what == null ? "" : what, (a, b) -> a.isBlank() ? b : b.isBlank() || a.equals(b)
                            ? a : a + " | " + b);
                }
            }
        }
        return definitions;
    }

    /** The PREMIS objects of a METS file, each with the events recorded in the same amdSec. */
    private static List<PremisObject> premis(String text) {
        List<PremisObject> objects = new ArrayList<>();
        Document d = xml(text);
        if (d == null) {
            return objects;
        }
        for (Element amd : elements(d, "amdSec")) {
            List<Element> premisObjects = elements(amd, "object");
            if (premisObjects.isEmpty()) {
                continue;
            }
            Element o = premisObjects.get(0);
            List<String> events = new ArrayList<>();
            for (Element ev : elements(amd, "event")) {
                String type = first(ev, "eventType");
                String when = first(ev, "eventDateTime");
                String outcome = first(ev, "eventOutcome");
                if (type != null) {
                    events.add(type + (when == null ? "" : " " + when) + (outcome == null || outcome.isBlank() ? ""
                            : " (" + outcome + ")"));
                }
            }
            objects.add(new PremisObject(first(o, "originalName"), first(o, "size"), first(o, "messageDigest"),
                    first(o, "messageDigestAlgorithm"), first(o, "formatName"), first(o, "formatVersion"),
                    first(o, "formatRegistryKey"), events));
        }
        return objects;
    }

    private static Optional<PremisObject> premisFor(List<PremisObject> premis, String bagPath) {
        String name = bagPath.replaceFirst("^data/", "");
        return premis.stream().filter(o -> o.originalName() != null && (o.originalName().endsWith("/" + name)
                || o.originalName().endsWith("%" + name) || o.originalName().endsWith(name))).findFirst();
    }

    /** The Dublin Core of a METS file's dmdSec, by element name. */
    private static Map<String, String> dublinCore(String text) {
        Map<String, String> dc = new LinkedHashMap<>();
        Document d = xml(text);
        if (d == null) {
            return dc;
        }
        NodeList all = d.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            dc.merge(e.getLocalName(), e.getTextContent().strip(), (a, b) -> a);
        }
        return dc;
    }

    /** The PDI fields of preservation_description_information.xml, by element name. */
    private static Map<String, String> pdi(String text) {
        Map<String, String> pdi = new LinkedHashMap<>();
        Document d = xml(text);
        if (d == null) {
            return pdi;
        }
        for (Element metadata : elements(d, "metadata")) {
            for (Node n = metadata.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && e.getLocalName() != null && e.getLocalName().startsWith("pdi")) {
                    pdi.put(e.getLocalName(), e.getTextContent().strip());
                }
            }
        }
        return pdi;
    }

    /** What checksum_audit.xml says: the checksums of the original and of what entered the AIP. */
    private static String checksumAudit(String text) {
        Document d = xml(text);
        if (d == null) {
            return "an integrity trail recorded at ingestion";
        }
        List<String> parts = new ArrayList<>();
        for (String step : List.of("informed", "ingested", "validation", "processed")) {
            for (Element e : elements(d, step)) {
                String sha = e.getAttribute("sha256");
                String note = e.getAttribute("note");
                String status = e.hasAttribute("verdict") ? e.getAttribute("verdict") : e.getAttribute("status");
                parts.add(step + ": " + (sha.isEmpty() ? status : sha) + (note.isEmpty() ? "" : " (" + note + ")"));
            }
        }
        return parts.isEmpty() ? "an integrity trail recorded at ingestion" : String.join("; ", parts);
    }

    private static final java.util.regex.Pattern UUID = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** The first UUID in {@code text}, e.g. in "URI: 6ffddacf-...", or null. */
    public static String uuid(String text) {
        java.util.regex.Matcher m = text == null ? null : UUID.matcher(text);
        return m != null && m.find() ? m.group().toLowerCase(Locale.ROOT) : null;
    }

    private static String join(String... parts) {
        List<String> present = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                present.add(p.strip());
            }
        }
        return present.isEmpty() ? null : String.join(" -- ", present);
    }
}
