package info.oais.archive.manager;

import info.oais.archive.manager.service.BitStore;
import info.oais.archive.manager.service.packages.PackageInspector;
import info.oais.archive.manager.service.packages.PackageInspector.Finding;
import info.oais.archive.manager.service.packages.PackageInspector.Inspection;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapping packaged AIPs -- like Eternal's -- to the components OAIS requires of an AIP. */
class PackageInspectorTest {

    @TempDir
    Path dir;

    static final String BAG = "GHGinventory-2926-20260903163403-e9d0fddd";
    static final String TRANSFER = "data/objects/metadata/transfers/GHGinventory-2926-f50a17c9/";
    static final byte[] DATABASE = "a Microsoft Access database, for the test".getBytes(StandardCharsets.UTF_8);

    /** A package as Eternal writes it, its spreadsheet with the upload spreadsheet's own column names. */
    static Map<String, byte[]> eternalPackage() {
        Map<String, byte[]> payload = new LinkedHashMap<>();
        payload.put("data/objects/GHG_inventory_BTR1_Final.mdb", DATABASE);
        payload.put(TRANSFER + "metadata.csv", ("""
                filename,Record No.,Original Title,Description,Creator,Publisher,Contributor,Format,Language,Relation,\
                Rights,Provenance,FixityHashSHA256,FormatInfo,Semantics,OtherRI
                objects/GHG_inventory_BTR1_Final.mdb,CC-00003,Maldives National Greenhouse Gas Inventory Dataset 2022,\
                "National GHG inventory, 2022",Ministry of Climate Change,Ministry of Climate Change,\
                Refer to BTR1,Digital,English,Maldives First Biennial Transparency Report (BTR1),\
                "Access restricted. Shared only with written approval.","Compiled for BTR1 using IPCC Inventory \
                Software 2.93",%s,"Microsoft Access Database, password protected",\
                URI: 6ffddacf-89ed-450c-b142-7427fb109cce,https://www.ipcc-nggip.iges.or.jp/software/index.html
                """.formatted(BitStore.sha256(DATABASE))).getBytes(StandardCharsets.UTF_8));
        payload.put(TRANSFER + "checksum_audit.xml", """
                <checksumAudit algorithm="sha256"><ingested note="original" sha256="%s"/></checksumAudit>
                """.formatted(BitStore.sha256(DATABASE)).getBytes(StandardCharsets.UTF_8));
        payload.put("data/METS.e9d0fddd.xml", """
                <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:premis="http://www.loc.gov/premis/v3">
                  <mets:amdSec ID="amdSec_1">
                    <mets:techMD><mets:mdWrap><mets:xmlData>
                      <premis:object>
                        <premis:objectCharacteristics>
                          <premis:fixity><premis:messageDigestAlgorithm>sha256</premis:messageDigestAlgorithm>
                            <premis:messageDigest>%s</premis:messageDigest></premis:fixity>
                          <premis:size>%d</premis:size>
                          <premis:format><premis:formatDesignation><premis:formatName>Microsoft Access Database</premis:formatName>
                            <premis:formatVersion>2000</premis:formatVersion></premis:formatDesignation>
                            <premis:formatRegistry><premis:formatRegistryKey>x-fmt/238</premis:formatRegistryKey></premis:formatRegistry>
                          </premis:format>
                        </premis:objectCharacteristics>
                        <premis:originalName>%%transferDirectory%%objects/GHG_inventory_BTR1_Final.mdb</premis:originalName>
                      </premis:object>
                    </mets:xmlData></mets:mdWrap></mets:techMD>
                    <mets:digiprovMD><mets:mdWrap><mets:xmlData>
                      <premis:event><premis:eventType>ingestion</premis:eventType>
                        <premis:eventDateTime>2026-09-03T16:34:03Z</premis:eventDateTime></premis:event>
                    </mets:xmlData></mets:mdWrap></mets:digiprovMD>
                  </mets:amdSec>
                </mets:mets>
                """.formatted(BitStore.sha256(DATABASE), DATABASE.length).getBytes(StandardCharsets.UTF_8));
        return bag(payload);
    }

    /** The payload as a BagIt bag, with manifests and Payload-Oxum, in a folder named {@link #BAG}. */
    static Map<String, byte[]> bag(Map<String, byte[]> payload) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        StringBuilder manifest = new StringBuilder();
        long octets = 0;
        for (Map.Entry<String, byte[]> e : payload.entrySet()) {
            manifest.append(BitStore.sha256(e.getValue())).append("  ").append(e.getKey()).append('\n');
            octets += e.getValue().length;
        }
        files.put(BAG + "/bagit.txt", "BagIt-Version: 0.97\nTag-File-Character-Encoding: UTF-8\n"
                .getBytes(StandardCharsets.UTF_8));
        files.put(BAG + "/bag-info.txt", ("External-Identifier: e9d0fddd\nPayload-Oxum: " + octets + "."
                + payload.size() + "\n").getBytes(StandardCharsets.UTF_8));
        files.put(BAG + "/manifest-sha256.txt", manifest.toString().getBytes(StandardCharsets.UTF_8));
        payload.forEach((path, bytes) -> files.put(BAG + "/" + path, bytes));
        return files;
    }

    static Path sevenZip(Path file, Map<String, byte[]> files) throws IOException {
        try (SevenZOutputFile out = new SevenZOutputFile(file.toFile())) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                SevenZArchiveEntry entry = new SevenZArchiveEntry();
                entry.setName(e.getKey().replace('/', '\\')); // as a package made on Windows names them
                out.putArchiveEntry(entry);
                out.write(e.getValue());
                out.closeArchiveEntry();
            }
        }
        return file;
    }

    @Test
    void findsEveryComponentInAnEternalPackage() throws Exception {
        Inspection i = PackageInspector.inspect(sevenZip(dir.resolve("aip.7z"), eternalPackage()), "aip.7z");

        assertThat(i.archiveFormat()).isEqualTo("7-Zip");
        assertThat(i.bagName()).isEqualTo(BAG);
        assertThat(i.bagCheck().valid()).isTrue();
        assertThat(i.bagCheck().listed()).isEqualTo(4);
        assertThat(i.missing()).isZero();
        assertThat(i.spreadsheet()).isEqualTo(TRANSFER + "metadata.csv");

        assertThat(where(i, "Data Object")).containsEntry("metadata.csv: filename",
                "data/objects/GHG_inventory_BTR1_Final.mdb (" + DATABASE.length + " bytes)");
        assertThat(where(i, "Structure Representation Information"))
                .containsEntry("metadata.csv: FormatInfo", "Microsoft Access Database, password protected")
                .containsEntry("data/METS.e9d0fddd.xml: PREMIS format",
                        "Microsoft Access Database -- version 2000 -- PRONOM x-fmt/238");
        assertThat(where(i, "Semantic Representation Information"))
                .containsKey("metadata.csv: Semantics")
                .containsEntry("metadata.csv: Language", "English");
        assertThat(i.unfilled()).isZero();
        assertThat(where(i, "Other Representation Information")).containsKey("metadata.csv: OtherRI");
        assertThat(where(i, "PDI: Fixity Information"))
                .containsEntry("metadata.csv: FixityHashSHA256", BitStore.sha256(DATABASE))
                .containsEntry("manifest-sha256.txt: data/objects/GHG_inventory_BTR1_Final.mdb",
                        BitStore.sha256(DATABASE) + " (verified: the file matches)");
        assertThat(where(i, "PDI: Provenance Information"))
                .containsKeys("metadata.csv: Provenance", "metadata.csv: Creator", "metadata.csv: Publisher",
                        "metadata.csv: Contributor")
                .containsEntry("data/METS.e9d0fddd.xml: PREMIS events", "ingestion 2026-09-03T16:34:03Z");
        assertThat(where(i, "PDI: Context Information")).containsEntry("metadata.csv: Relation",
                "Maldives First Biennial Transparency Report (BTR1)");
        assertThat(where(i, "PDI: Reference Information")).containsEntry("the AIP's name", BAG)
                .containsEntry("bag-info.txt: External-Identifier", "e9d0fddd")
                .containsEntry("metadata.csv: Record No.", "CC-00003");
        assertThat(where(i, "PDI: Access Rights Information")).containsEntry("metadata.csv: Rights",
                "Access restricted. Shared only with written approval.");
        assertThat(where(i, "Package Description")).containsEntry("metadata.csv",
                "Maldives National Greenhouse Gas Inventory Dataset 2022 -- National GHG inventory, 2022");
        assertThat(where(i, "Packaging Information")).containsEntry("manifest-sha256.txt",
                "all 4 payload files match their checksums");
    }

    @Test
    void saysWhatIsMissingAndWhatHasChanged() throws Exception {
        Map<String, byte[]> files = eternalPackage();
        files.put(BAG + "/data/objects/GHG_inventory_BTR1_Final.mdb", "altered".getBytes(StandardCharsets.UTF_8));
        String csv = new String(files.get(BAG + "/" + TRANSFER + "metadata.csv"), StandardCharsets.UTF_8);
        files.put(BAG + "/" + TRANSFER + "metadata.csv", csv.replace(",Relation,", ",Other,")
                .getBytes(StandardCharsets.UTF_8));
        Inspection i = PackageInspector.inspect(sevenZip(dir.resolve("changed.7z"), files), "changed.7z");

        assertThat(i.bagCheck().valid()).isFalse();
        assertThat(i.bagCheck().mismatched()).containsExactlyInAnyOrder("data/objects/GHG_inventory_BTR1_Final.mdb",
                TRANSFER + "metadata.csv");
        assertThat(finding(i, "PDI: Context Information").found()).isFalse();
        assertThat(where(i, "PDI: Fixity Information").get(
                "manifest-sha256.txt: data/objects/GHG_inventory_BTR1_Final.mdb")).contains("DOESN'T match the file");
    }

    @Test
    void saysWhereAMissingComponentBelongsAndWhyItIsNotThere() throws Exception {
        Map<String, byte[]> files = eternalPackage();
        String csv = new String(files.get(BAG + "/" + TRANSFER + "metadata.csv"), StandardCharsets.UTF_8);
        // Rights blank; no Relation column, though metadata_definition.xml defines dc.relation; no OtherRI column.
        String[] lines = csv.split("\n", 2);
        String header = lines[0].replace(",Relation,", ",").replace(",OtherRI", "");
        String row = lines[1].replace("Maldives First Biennial Transparency Report (BTR1),", "")
                .replace("\"Access restricted. Shared only with written approval.\"", "")
                .replace(",https://www.ipcc-nggip.iges.or.jp/software/index.html", "");
        files.put(BAG + "/" + TRANSFER + "metadata.csv", (header + "\n" + row).getBytes(StandardCharsets.UTF_8));
        files.put(BAG + "/" + TRANSFER + "metadata_definition.xml", """
                <mets:mets xmlns:mets="http://www.loc.gov/METS/"><mets:dmdSec><mets:mdWrap><mets:xmlData><metadata>
                  <dcrelation><translation/><definition/><originalName>dc.relation</originalName></dcrelation>
                </metadata></mets:xmlData></mets:mdWrap></mets:dmdSec></mets:mets>
                """.getBytes(StandardCharsets.UTF_8));
        Inspection i = PackageInspector.inspect(sevenZip(dir.resolve("gaps.7z"), files), "gaps.7z");

        Finding rights = finding(i, "PDI: Access Rights Information");
        assertThat(rights.found()).isFalse();
        assertThat(gaps(rights)).containsExactly(Map.entry("metadata.csv: Rights", "blank in this package"));
        Finding context = finding(i, "PDI: Context Information");
        assertThat(context.found()).isFalse();
        assertThat(gaps(context)).containsExactly(Map.entry("metadata.csv: dc.relation",
                "defined in metadata_definition.xml, but not in metadata.csv, so it was left blank"));
        Finding other = finding(i, "Other Representation Information");
        assertThat(other.required()).isFalse();
        assertThat(other.found()).isFalse();
        assertThat(gaps(other)).containsExactly(Map.entry("metadata.csv: OtherRI",
                "the upload spreadsheet used for this package has no OtherRI column"));
        assertThat(i.missing()).isEqualTo(2); // Rights and Context; Other Representation Information is optional
    }

    @Test
    void saysWhichExpectedSpreadsheetEntriesAreNotFilledIn() throws Exception {
        // An older package: Dublin Core columns, the record number folded into dc.subject, no FixityHashSHA256,
        // Semantics or Provenance, and dc.publisher defined but left blank.
        Map<String, byte[]> payload = new LinkedHashMap<>();
        payload.put("data/objects/R00030_A.pdf", DATABASE);
        payload.put(TRANSFER + "metadata.csv", """
                filename,dc.title,dc.subject,dc.creator,dc.contributor,dc.language
                objects/R00030_A.pdf,R00030_A,R00030; Mosque,National Archives of Maldives,By Sultan Hassan Nooraddin,Dhivehi
                """.getBytes(StandardCharsets.UTF_8));
        payload.put(TRANSFER + "metadata_definition.xml", """
                <mets:mets xmlns:mets="http://www.loc.gov/METS/"><mets:dmdSec><mets:mdWrap><mets:xmlData><metadata>
                  <dcsubject><translation/><definition>English: Number given by the National Archives of Maldives to identify the content</definition><originalName>dc.subject</originalName></dcsubject>
                  <dcpublisher><translation/><definition/><originalName>dc.publisher</originalName></dcpublisher>
                </metadata></mets:xmlData></mets:mdWrap></mets:dmdSec></mets:mets>
                """.getBytes(StandardCharsets.UTF_8));
        Inspection i = PackageInspector.inspect(sevenZip(dir.resolve("old.7z"), bag(payload)), "old.7z");

        Finding fixity = finding(i, "PDI: Fixity Information");
        assertThat(fixity.found()).isTrue(); // the bag's manifest
        assertThat(gaps(fixity)).containsExactly(Map.entry("metadata.csv: FixityHashSHA256",
                "the upload spreadsheet used for this package has no FixityHashSHA256 column"));
        Finding semantic = finding(i, "Semantic Representation Information");
        assertThat(where(i, "Semantic Representation Information")).containsEntry("metadata.csv: dc.language",
                "Dhivehi");
        assertThat(gaps(semantic)).containsOnlyKeys("metadata.csv: Semantics");
        Finding provenance = finding(i, "PDI: Provenance Information");
        assertThat(where(i, "PDI: Provenance Information")).containsKeys("metadata.csv: dc.creator",
                "metadata.csv: dc.contributor");
        assertThat(gaps(provenance)).containsExactly(
                Map.entry("metadata.csv: Provenance", "the upload spreadsheet used for this package has no Provenance column"),
                Map.entry("metadata.csv: dc.publisher",
                        "defined in metadata_definition.xml, but not in metadata.csv, so it was left blank"));
        assertThat(where(i, "PDI: Reference Information")).containsValue("R00030");
        // Not filled in: Format, FormatInfo, Semantics, FixityHashSHA256, Provenance, Publisher, Relation, Rights
        assertThat(i.unfilled()).isEqualTo(8);
    }

    @Test
    void notesThatSemanticsIdentifiesASeparateAip() throws Exception {
        Inspection i = PackageInspector.inspect(sevenZip(dir.resolve("aip.7z"), eternalPackage()), "aip.7z");
        assertThat(where(i, "Semantic Representation Information")).containsEntry("metadata.csv: Semantics",
                "URI: 6ffddacf-89ed-450c-b142-7427fb109cce -- the identifier of the separate AIP that holds the "
                        + "Semantic Representation Information");
        assertThat(PackageInspector.uuid("URI: 6FFDDACF-89ed-450c-b142-7427fb109cce"))
                .isEqualTo("6ffddacf-89ed-450c-b142-7427fb109cce");
    }

    @Test
    void readsAZipThatIsNotABag() throws Exception {
        Path zip = dir.resolve("loose.zip");
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("report.pdf"));
            z.write("pdf".getBytes(StandardCharsets.UTF_8));
        }
        Inspection i = PackageInspector.inspect(zip, "loose.zip");
        assertThat(i.notes()).anyMatch(n -> n.contains("isn't a BagIt bag"));
        assertThat(finding(i, "Packaging Information").found()).isFalse();
        assertThat(i.files()).extracting(PackageInspector.PackageFile::path).containsExactly("report.pdf");
    }

    private static Finding finding(Inspection i, String component) {
        return i.findings().stream().filter(f -> f.component().equals(component)).findFirst().orElseThrow();
    }

    private static Map<String, String> gaps(Finding f) {
        Map<String, String> found = new LinkedHashMap<>();
        f.gaps().forEach(s -> found.put(s.where(), s.value()));
        return found;
    }

    private static Map<String, String> where(Inspection i, String component) {
        Map<String, String> found = new LinkedHashMap<>();
        finding(i, component).sources().forEach(s -> found.put(s.where(), s.value()));
        return found;
    }
}
