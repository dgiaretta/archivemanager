package info.oais.archive.manager.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import info.oais.archive.manager.model.ImportResult;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imports NAM's "Bulk Upload Spreadsheet" -- the 26-column format
 * documented in the Preservation Policy and Implementation Plan ("Upload
 * Spreadsheet and Accession Register") and used, per the Records Management
 * Plan's transfer workflow, to configure bulk uploads to the Eternal
 * digital archive -- from either a CSV ({@link #importCsv}) or an XLSX
 * ({@link #importXlsx}) file. One row becomes one {@code rico:Record}; the
 * whole import becomes one {@code nam:Accession} linking every record it
 * created via {@code rico:hasOrganicOrFunctionalProvenance} (the same
 * provenance property the rest of this app already uses for "what activity
 * produced this record").
 *
 * <p><b>Prefer XLSX over CSV when the source data has non-Latin script in
 * it (Dhivehi, in NAM's case).</b> Excel's plain "CSV (Comma delimited)"
 * export writes the file using the system's ANSI code page, not UTF-8,
 * unless "CSV UTF-8 (Comma delimited)" is chosen specifically -- and the
 * Windows ANSI code page has no representation for Thaana script at all, so
 * that export step silently replaces every Dhivehi character with a
 * literal {@code ?} in the file itself, before this app ever reads it. That
 * corruption is permanent once it's in the CSV -- re-importing the same
 * lossy CSV file, however well this app handles encoding, cannot recover
 * text that's already gone. Reading the XLSX directly avoids the lossy
 * step altogether: XLSX stores text as UTF-8 XML internally, no ANSI code
 * page involved anywhere in the path. Both import methods feed the exact
 * same row-processing logic ({@link #importRow}) once a row's cells are
 * read into a plain {@code List<String>}, so there's exactly one place the
 * column-to-vocabulary mapping is decided, not two copies to keep in sync.
 *
 * <p>Column-to-vocabulary mapping, decided once here rather than per field
 * as questions come up:
 * <ul>
 *   <li>Where RiC-O has a specific, closer-fitting property than a bare
 *       literal, that's used: Title -> {@code rico:title}; Creator -> a
 *       real {@code rico:CorporateBody} Agent (found or created by name)
 *       linked via {@code rico:hasCreator}, not a literal; Description ->
 *       {@code rico:generalDescription}; Identifier(s) -> real
 *       {@code rico:Identifier} individuals linked via
 *       {@code rico:hasOrHadIdentifier}, matching how identifiers/DOIs are
 *       modelled in this project's own sample data.
 *   <li>The remaining Dublin Core columns (Subject, Other Title, Publisher,
 *       Contributor, Date, Type, Format, Source, Language, Relation,
 *       Coverage, Rights) use the real {@code dc:} elements directly --
 *       they're literally what NAM's spreadsheet columns are, so there's no
 *       reason to invent a RiC-O-shaped substitute. "Other Title" uses
 *       {@code dc:title} specifically, leaving {@code rico:title} for the
 *       one primary title.
 *   <li>Preservation-specific columns (Provenance, FixityHashSHA256,
 *       Rights again, FormatInfo, Semantics, OtherRI) build a real, if
 *       lightweight, OAIS counterpart for the record -- an
 *       ArchivalInformationPackage wrapping a Content Information /
 *       Information Object (double-typed, same reasoning as this project's
 *       other sample data) and a Preservation Description Information with
 *       whichever of Provenance/Fixity/AccessRights/Reference Information
 *       the row actually supplied, plus a Representation Information split
 *       into Structure/Semantic/Other facets if FormatInfo/Semantics/OtherRI
 *       were supplied -- bridged to the RiC-O Record via
 *       {@code bridge:hasOAISCounterpart}/{@code hasRiCDescription}, so the
 *       existing OAIS mapping page works on every imported record without
 *       any changes to it.
 *   <li>Columns with no natural home anywhere else (Record No, storage
 *       Location, Received/Accessioned Date, TransferredRecord) use the
 *       small {@code nam:} extension vocabulary.
 * </ul>
 *
 * <p>"Repeatable" columns (per the spreadsheet's own specification) accept
 * multiple values separated by {@code ;} in one cell, each becoming a
 * separate triple.
 *
 * <p>No column is treated as mandatory. Real NAM spreadsheets routinely
 * have several blank cells (even ones the policy documents mark with an
 * asterisk as required), and a record with fewer properties is far more
 * useful than a row rejected outright over a missing value the source
 * system simply didn't have -- a blank cell just means that property isn't
 * recorded for that row. "Original Title" is accepted as an alternate
 * header for "Title" (some real spreadsheets use that name for the same
 * column the policy documents call "Title"), distinct from "Other Title"
 * (a genuinely different, second title, mapped to {@code dc:title}).
 */
@Service
public class CatalogueImportService {

    private final RdfStore store;
    private final QueryRunner q;

    public CatalogueImportService(RdfStore store, QueryRunner q) {
        this.store = store;
        this.q = q;
    }

    public ImportResult importCsv(Reader csvReader, String accessionDescription, String depositor,
                                   String recordingArchivist, String accessionDate) throws IOException {
        Model m = store.dataModel();
        List<String> warnings = new ArrayList<>();
        List<String> createdRecordIris = new ArrayList<>();
        String batchSlug = newBatchSlug();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (CSVParser parser = format.parse(csvReader)) {
            Map<String, Integer> headerIndex = new HashMap<>();
            for (Map.Entry<String, Integer> e : parser.getHeaderMap().entrySet()) {
                headerIndex.put(normalize(e.getKey()), e.getValue());
            }

            int rowNum = 0;
            for (CSVRecord row : parser) {
                rowNum++;
                List<String> values = new ArrayList<>();
                for (int i = 0; i < row.size(); i++) {
                    values.add(row.get(i));
                }
                importRowSafely(m, values, headerIndex, batchSlug, rowNum, createdRecordIris, warnings);
            }
        }

        return finishAccession(m, batchSlug, createdRecordIris, warnings,
                accessionDescription, depositor, recordingArchivist, accessionDate);
    }

    /**
     * Reads the first sheet of an XLSX workbook. String cells are read via
     * {@link Cell#getStringCellValue()} directly -- the simplest, most
     * direct POI API for actual text content, which is the overwhelming
     * majority of what this archival metadata is -- rather than routing
     * everything through {@link DataFormatter}, which exists primarily for
     * *numeric and date formatting* (turning a raw numeric cell value into
     * the same text Excel would display for it, honoring the cell's number
     * format) and is only actually needed for non-string cells here.
     * {@link #cellToString} keeps that DataFormatter fallback for numeric,
     * date, boolean, and formula cells (so a numeric-looking Record No like
     * "00042" still reads back correctly rather than as the number 42, and
     * a formula cell reads as its computed value rather than its formula
     * text) but no longer runs plain text through it at all.
     */
    public ImportResult importXlsx(InputStream xlsxStream, String accessionDescription, String depositor,
                                    String recordingArchivist, String accessionDate) throws IOException {
        Model m = store.dataModel();
        List<String> warnings = new ArrayList<>();
        List<String> createdRecordIris = new ArrayList<>();
        String batchSlug = newBatchSlug();

        try (Workbook workbook = WorkbookFactory.create(xlsxStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IOException("The spreadsheet's first row (column headers) is empty.");
            }
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null) {
                    String header = cellToString(cell, formatter);
                    if (notBlank(header)) {
                        headerIndex.put(normalize(header), c - headerRow.getFirstCellNum());
                    }
                }
            }

            int rowNum = 0;
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue; // fully blank row
                }
                rowNum++;
                List<String> values = new ArrayList<>();
                int firstCol = headerRow.getFirstCellNum();
                int lastCol = row.getLastCellNum();
                for (int c = firstCol; c < lastCol; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.add(cellToString(cell, formatter));
                }
                importRowSafely(m, values, headerIndex, batchSlug, rowNum, createdRecordIris, warnings);
            }
        }

        return finishAccession(m, batchSlug, createdRecordIris, warnings,
                accessionDescription, depositor, recordingArchivist, accessionDate);
    }

    /** String cells: the direct API. Everything else (numeric, date, boolean, formula, blank): DataFormatter's display-text conversion. */
    private String cellToString(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
            return cell.getStringCellValue();
        }
        return formatter.formatCellValue(cell);
    }

    private void importRowSafely(Model m, List<String> values, Map<String, Integer> headerIndex, String batchSlug,
                                  int rowNum, List<String> createdRecordIris, List<String> warnings) {
        try {
            createdRecordIris.add(importRow(m, values, headerIndex, batchSlug, rowNum));
        } catch (RuntimeException e) {
            warnings.add("Row " + rowNum + " skipped: " + e.getMessage());
        }
    }

    private String newBatchSlug() {
        return "acc-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Builds the nam:Accession individual and links every successfully-imported record to it. Shared by both import methods. */
    private ImportResult finishAccession(Model m, String batchSlug, List<String> createdRecordIris, List<String> warnings,
                                          String accessionDescription, String depositor, String recordingArchivist,
                                          String accessionDate) {
        String accessionIri = Ns.EX + batchSlug;
        Resource accession = m.createResource(accessionIri);
        accession.addProperty(RDF.type, m.createResource(Ns.NAM + "Accession"));
        if (notBlank(accessionDescription)) {
            accession.addProperty(prop(m, Ns.RICO, "generalDescription"), accessionDescription);
        }
        if (notBlank(depositor)) {
            accession.addProperty(prop(m, Ns.NAM, "depositor"), depositor);
        }
        if (notBlank(recordingArchivist)) {
            accession.addProperty(prop(m, Ns.NAM, "recordingArchivist"), recordingArchivist);
        }
        accession.addProperty(prop(m, Ns.NAM, "quantityReceived"), String.valueOf(createdRecordIris.size()));
        if (notBlank(accessionDate)) {
            Resource dateRes = m.createResource(accessionIri + "-date");
            dateRes.addProperty(RDF.type, m.createResource(Ns.RICO + "Date"));
            dateRes.addProperty(prop(m, Ns.RICO, "textualValue"), accessionDate);
            accession.addProperty(prop(m, Ns.RICO, "occurredAtDate"), dateRes);
        }
        for (String recordIri : createdRecordIris) {
            m.createResource(recordIri).addProperty(prop(m, Ns.RICO, "hasOrganicOrFunctionalProvenance"), accession);
        }

        return new ImportResult(accessionIri, createdRecordIris.size(), warnings);
    }

    private String importRow(Model m, List<String> values, Map<String, Integer> idx, String batchSlug, int rowNum) {
        String recordNo = field(values, idx, "recordno");
        String recordSlug = notBlank(recordNo) ? slugify(recordNo) : "row" + rowNum;
        String recordIri = Ns.EX + "record-" + batchSlug + "-" + recordSlug;
        Resource record = m.createResource(recordIri);
        record.addProperty(RDF.type, m.createResource(Ns.RICO + "Record"));

        // "Original Title" is an alternate header some real NAM spreadsheets use for
        // the same column the policy documents call "Title" -- not to be confused
        // with "Other Title" (a genuinely different, second/alternative title,
        // mapped to dc:title below). Neither is treated as mandatory: in practice,
        // plenty of real rows have several columns blank, and importing a record
        // with fewer properties is far more useful than silently skipping it.
        String title = firstOf(values, idx, "title", "originaltitle");
        if (notBlank(title)) {
            record.addProperty(prop(m, Ns.RICO, "title"), title);
        }
        addEach(record, prop(m, Ns.DC, "title"), repeatable(values, idx, "othertitle"));
        addEach(record, prop(m, Ns.DC, "subject"), repeatable(values, idx, "subject"));
        addEach(record, prop(m, Ns.RICO, "generalDescription"), repeatable(values, idx, "description"));

        String creator = field(values, idx, "creator");
        if (notBlank(creator)) {
            Resource creatorAgent = findOrCreateCorporateBody(m, creator);
            record.addProperty(prop(m, Ns.RICO, "hasCreator"), creatorAgent);
        }

        addEach(record, prop(m, Ns.DC, "publisher"), repeatable(values, idx, "publisher"));
        addEach(record, prop(m, Ns.DC, "contributor"), repeatable(values, idx, "contributor"));
        addEach(record, prop(m, Ns.DC, "date"), repeatable(values, idx, "date"));
        addEach(record, prop(m, Ns.DC, "type"), repeatable(values, idx, "type"));
        addEach(record, prop(m, Ns.DC, "format"), repeatable(values, idx, "format"));

        List<String> identifiers = repeatable(values, idx, "identifier");
        int idCounter = 0;
        for (String idVal : identifiers) {
            idCounter++;
            Resource idRes = m.createResource(recordIri + "-id-" + idCounter);
            idRes.addProperty(RDF.type, m.createResource(Ns.RICO + "Identifier"));
            idRes.addProperty(prop(m, Ns.RICO, "textualValue"), idVal);
            record.addProperty(prop(m, Ns.RICO, "hasOrHadIdentifier"), idRes);
        }

        addEach(record, prop(m, Ns.DC, "source"), repeatable(values, idx, "source"));
        addEach(record, prop(m, Ns.DC, "language"), repeatable(values, idx, "language"));
        addEach(record, prop(m, Ns.DC, "relation"), repeatable(values, idx, "relation"));
        addEach(record, prop(m, Ns.DC, "coverage"), repeatable(values, idx, "coverage"));

        List<String> rights = repeatable(values, idx, "rights");
        addEach(record, prop(m, Ns.DC, "rights"), rights);

        addIfPresent(record, prop(m, Ns.NAM, "storageLocation"), field(values, idx, "location"));
        addIfPresent(record, prop(m, Ns.NAM, "receivedDate"), field(values, idx, "receiveddate"));
        addIfPresent(record, prop(m, Ns.NAM, "accessionedDate"), field(values, idx, "accessioneddate"));

        List<String> provenance = repeatable(values, idx, "provenance");
        String fixity = field(values, idx, "fixityhashsha256");
        List<String> formatInfo = repeatable(values, idx, "formatinfo");
        List<String> semantics = repeatable(values, idx, "semantics");
        List<String> otherRi = repeatable(values, idx, "otherri");

        addIfPresent(record, prop(m, Ns.NAM, "transferredRecord"), field(values, idx, "transferredrecord"));
        if (notBlank(recordNo)) {
            record.addProperty(prop(m, Ns.NAM, "recordNumber"), recordNo);
        }

        buildOaisCounterpart(m, recordIri, record, provenance, fixity, rights, identifiers, formatInfo, semantics, otherRi);

        return recordIri;
    }

    /**
     * Builds a lightweight but structurally correct OAIS counterpart for one
     * record: an ArchivalInformationPackage wrapping a Content Information /
     * Information Object (double-typed, matching this project's other sample
     * data) and a Preservation Description Information populated with
     * whichever facets the row actually supplied, plus a Representation
     * Information split into Structure/Semantic/Other if any of those
     * columns were present. Bridged to the record so the existing OAIS
     * mapping page (/oais/{id}) works on it unchanged.
     */
    private void buildOaisCounterpart(Model m, String recordIri, Resource record, List<String> provenance,
                                       String fixity, List<String> rights, List<String> identifiers,
                                       List<String> formatInfo, List<String> semantics, List<String> otherRi) {
        String base = recordIri + "-oais";

        Resource infoObject = m.createResource(base + "-infoobject");
        infoObject.addProperty(RDF.type, m.createResource(Ns.IM + "ContentInformation"));
        infoObject.addProperty(RDF.type, m.createResource(Ns.IM + "InformationObject"));
        infoObject.addProperty(prop(m, Ns.BRIDGE, "hasRiCDescription"), record);
        record.addProperty(prop(m, Ns.BRIDGE, "hasOAISCounterpart"), infoObject);

        Resource aip = m.createResource(base + "-aip");
        aip.addProperty(RDF.type, m.createResource(Ns.IM + "ArchivalInformationPackage"));
        aip.addProperty(prop(m, Ns.IM, "hasContentInformation"), infoObject);

        Resource pdi = m.createResource(base + "-pdi");
        pdi.addProperty(RDF.type, m.createResource(Ns.IM + "PreservationDescriptionInformation"));
        aip.addProperty(prop(m, Ns.IM, "hasPreservationDescriptiveInformation"), pdi);

        if (!provenance.isEmpty()) {
            Resource provInfo = m.createResource(base + "-provenance");
            provInfo.addProperty(RDF.type, m.createResource(Ns.IM + "ProvenanceInformation"));
            provInfo.addProperty(RDFS.comment, String.join("; ", provenance));
            pdi.addProperty(prop(m, Ns.IM, "hasProvenanceInformation"), provInfo);
        }
        if (notBlank(fixity)) {
            Resource fixityInfo = m.createResource(base + "-fixity");
            fixityInfo.addProperty(RDF.type, m.createResource(Ns.IM + "FixityInformation"));
            fixityInfo.addProperty(RDFS.comment, "SHA-256: " + fixity);
            pdi.addProperty(prop(m, Ns.IM, "hasFixityInformation"), fixityInfo);
        }
        if (!rights.isEmpty()) {
            Resource accessRights = m.createResource(base + "-accessrights");
            accessRights.addProperty(RDF.type, m.createResource(Ns.IM + "AccessRightsInformation"));
            accessRights.addProperty(RDFS.comment, String.join("; ", rights));
            pdi.addProperty(prop(m, Ns.IM, "hasAccessRightsInformation"), accessRights);
        }
        if (!identifiers.isEmpty()) {
            Resource refInfo = m.createResource(base + "-reference");
            refInfo.addProperty(RDF.type, m.createResource(Ns.IM + "ReferenceInformation"));
            refInfo.addProperty(RDFS.comment, String.join("; ", identifiers));
            pdi.addProperty(prop(m, Ns.IM, "hasReferenceInformation"), refInfo);
        }

        if (!formatInfo.isEmpty() || !semantics.isEmpty() || !otherRi.isEmpty()) {
            Resource repInfo = m.createResource(base + "-representation");
            repInfo.addProperty(RDF.type, m.createResource(Ns.IM + "RepresentationInformation"));
            infoObject.addProperty(prop(m, Ns.IM, "hasRepresentationInformation"), repInfo);

            if (!formatInfo.isEmpty()) {
                Resource structInfo = m.createResource(base + "-structure");
                structInfo.addProperty(RDF.type, m.createResource(Ns.IM + "StructureRepresentationInformation"));
                structInfo.addProperty(RDFS.comment, String.join("; ", formatInfo));
                repInfo.addProperty(prop(m, Ns.IM, "hasStructureRepresentationInformation"), structInfo);
            }
            if (!semantics.isEmpty()) {
                Resource semInfo = m.createResource(base + "-semantic");
                semInfo.addProperty(RDF.type, m.createResource(Ns.IM + "SemanticRepresentationInformation"));
                semInfo.addProperty(RDFS.comment, String.join("; ", semantics));
                repInfo.addProperty(prop(m, Ns.IM, "hasSemanticRepresentationInformation"), semInfo);
            }
            if (!otherRi.isEmpty()) {
                Resource otherInfo = m.createResource(base + "-other");
                otherInfo.addProperty(RDF.type, m.createResource(Ns.IM + "OtherRepresentationInformation"));
                otherInfo.addProperty(RDFS.comment, String.join("; ", otherRi));
                repInfo.addProperty(prop(m, Ns.IM, "hasOtherRepresentationInformation"), otherInfo);
            }
        }
    }

    /** Finds an existing rico:CorporateBody with this exact name, or creates one -- so 50 rows by the same creator share one Agent. */
    private Resource findOrCreateCorporateBody(Model m, String name) {
        String sparql = Ns.PREFIXES + """
                SELECT ?a WHERE {
                  ?a a rico:CorporateBody .
                  ?a rico:name "%s" .
                } LIMIT 1
                """.formatted(Ns.escapeSparqlLiteral(name));
        List<Map<String, String>> rows = q.select(store.dataModel(), sparql);
        if (!rows.isEmpty()) {
            return m.getResource(rows.get(0).get("a"));
        }
        Resource agent = m.createResource(Ns.EX + "agent-" + slugify(name) + "-" + UUID.randomUUID().toString().substring(0, 6));
        agent.addProperty(RDF.type, m.createResource(Ns.RICO + "CorporateBody"));
        agent.addProperty(prop(m, Ns.RICO, "name"), name);
        return agent;
    }

    private Property prop(Model m, String namespace, String localName) {
        return m.createProperty(namespace, localName);
    }

    private void addEach(Resource subject, Property property, List<String> values) {
        for (String v : values) {
            subject.addProperty(property, v);
        }
    }

    /** Adds one triple only if the value is actually present -- the normal case now that no column is treated as mandatory. */
    private void addIfPresent(Resource subject, Property property, String value) {
        if (notBlank(value)) {
            subject.addProperty(property, value);
        }
    }

    private String field(List<String> values, Map<String, Integer> idx, String normalizedName) {
        Integer i = idx.get(normalizedName);
        if (i == null || i >= values.size()) {
            return null;
        }
        String value = values.get(i);
        return value == null ? null : value.trim();
    }

    /** The first non-blank value found across several alternate column-header spellings, for fields real spreadsheets label inconsistently (e.g. "Title" / "Original Title"). */
    private String firstOf(List<String> values, Map<String, Integer> idx, String... normalizedNames) {
        for (String name : normalizedNames) {
            String value = field(values, idx, name);
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> repeatable(List<String> values, Map<String, Integer> idx, String normalizedName) {
        String raw = field(values, idx, normalizedName);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("null")) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String normalize(String header) {
        return header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String slugify(String s) {
        String lower = s.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-");
        return lower.replaceAll("^-+|-+$", "");
    }
}
