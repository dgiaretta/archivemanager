package info.oais.archive.manager.service.format;

import java.util.List;

/**
 * Formats whose structure is already defined elsewhere -- by a published
 * specification and the software that reads it -- so that only the meaning
 * of their content needs describing (see {@code KnownFormatDescription}).
 * Each gives the defaults for its format profile (Structure Representation
 * Information: a registry identifier refined with what it leaves out) and
 * the software that reads it, as alternatives any one of which will do
 * (Other Representation Information, saved as an OR group).
 *
 * @param label        how the format is named on the page
 * @param extensions   its usual file extensions
 * @param registryId   its PRONOM identifier, prefixed by the registry
 * @param mediaType    its IANA media type
 * @param version      the version or conformance class of the specification described by default
 * @param specLabel    the specification
 * @param specUrl      where the specification is published
 * @param text         whether it's delimited text (so encoding, line endings, delimiter and quote apply)
 * @param readable     whether this application can read samples of it (to propose columns and test)
 * @param software     the software that reads it: alternatives, any one of which will do
 */
public record KnownFormat(String key, String label, List<String> extensions, String registryId, String mediaType,
                          String version, String specLabel, String specUrl, boolean text, boolean readable,
                          List<Software> software) {

    /**
     * Software that reads a format, as shared Other Representation Information.
     *
     * @param key         part of its stable IRI
     * @param label       its name, or a general statement of what will do
     * @param description what it is
     * @param url         where it's described, or null for a general statement
     */
    public record Software(String key, String label, String description, String url) {
    }

    private static final Software EXCEL = new Software("microsoft-excel", "Microsoft Excel",
            "Microsoft's spreadsheet application, which reads and writes .xlsx workbooks (Excel 2007 and later) "
                    + "and .xls workbooks.", "https://www.microsoft.com/microsoft-365/excel");
    private static final Software CALC = new Software("libreoffice-calc", "LibreOffice Calc",
            "The spreadsheet application of the free LibreOffice suite, which reads .xlsx, .xls and .ods workbooks "
                    + "and delimited text.", "https://www.libreoffice.org/discover/calc/");

    public static final List<KnownFormat> ALL = List.of(
            new KnownFormat("xlsx", "Excel workbook (.xlsx)", List.of("xlsx"), "PRONOM fmt/214",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "ECMA-376 Transitional",
                    "Office Open XML, ECMA-376 (ISO/IEC 29500)",
                    "https://ecma-international.org/publications-and-standards/standards/ecma-376/", false, true,
                    List.of(EXCEL, CALC, new Software("any-ooxml-spreadsheet-reader",
                            "Any application that reads OOXML spreadsheets",
                            "Any application that reads Office Open XML (ECMA-376) spreadsheets.", null))),
            new KnownFormat("xls", "Excel 97-2003 workbook (.xls)", List.of("xls"), "PRONOM fmt/61",
                    "application/vnd.ms-excel", "BIFF8", "Microsoft Excel Binary File Format (.xls)",
                    "https://learn.microsoft.com/openspecs/office_file_formats/ms-xls/", false, true,
                    List.of(EXCEL, CALC)),
            new KnownFormat("ods", "OpenDocument spreadsheet (.ods)", List.of("ods"), "PRONOM fmt/295",
                    "application/vnd.oasis.opendocument.spreadsheet", "ODF 1.2",
                    "OpenDocument Format, OASIS ODF (ISO/IEC 26300)", "https://www.oasis-open.org/committees/office/",
                    false, false,
                    List.of(CALC, EXCEL, new Software("any-odf-spreadsheet-reader",
                            "Any application that reads OpenDocument spreadsheets",
                            "Any application that reads OpenDocument Format (ISO/IEC 26300) spreadsheets.", null))),
            new KnownFormat("csv", "Comma-separated values (.csv)", List.of("csv"), "PRONOM x-fmt/18", "text/csv",
                    "RFC 4180", "Common Format and MIME Type for CSV Files, RFC 4180",
                    "https://www.rfc-editor.org/rfc/rfc4180", true, true,
                    List.of(EXCEL, CALC, new Software("any-text-editor", "Any text editor",
                            "Any application that displays plain text in the file's character encoding.", null))));

    public static KnownFormat of(String key) {
        return ALL.stream().filter(f -> f.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown format: " + key));
    }
}
