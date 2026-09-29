package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.KnownFormatDescription;
import info.oais.infomodel.structure.description.Semantics;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a sample spreadsheet (.xlsx or .xls, with Apache POI) or delimited
 * text as sheets of cell text, so RepInfo Tools can propose the columns a
 * {@link KnownFormatDescription} describes, and test that description
 * against real data: whether each described column is there, and what its
 * values mean (codes, scaled values with units, fill values), with values
 * outside their valid range or code list counted.
 */
@Component
public class SpreadsheetReader {

    /** Rows read per sheet at most, so a large sample can't exhaust memory. */
    static final int MAX_ROWS = 100_000;
    /** Values shown per column in a test. */
    static final int SHOWN = 10;

    /** A sheet's cells as text, row by row (rows may be ragged; missing cells are ""). */
    public record SheetData(String name, List<List<String>> rows) {
        String cell(int row, int column) {
            if (row < 0 || row >= rows.size()) {
                return "";
            }
            List<String> r = rows.get(row);
            return column < r.size() ? r.get(column) : "";
        }
    }

    /** One described column, tested against the sample. */
    public record ColumnResult(String sheet, String header, String location, String problem, int values,
                               List<Shown> shown, List<String> checks) {
        public boolean found() {
            return problem == null;
        }
    }

    /** A value and what it means, e.g. "20.5" and "20.5 Cel". */
    public record Shown(String value, String meaning) {
    }

    public List<SheetData> read(KnownFormatDescription description, byte[] sample) throws IOException {
        KnownFormat format = KnownFormat.of(description.getFormat());
        if (!format.readable()) {
            throw new IOException("This application can't read " + format.label() + " files, so it can't test them.");
        }
        return format.text() ? readText(description, sample) : readWorkbook(sample);
    }

    private static List<SheetData> readWorkbook(byte[] sample) throws IOException {
        List<SheetData> sheets = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(sample))) {
            for (Sheet sheet : workbook) {
                List<List<String>> rows = new ArrayList<>();
                for (int r = 0; r <= Math.min(sheet.getLastRowNum(), MAX_ROWS - 1); r++) {
                    Row row = sheet.getRow(r);
                    List<String> cells = new ArrayList<>();
                    if (row != null) {
                        for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                            cells.add(text(row.getCell(c)));
                        }
                    }
                    rows.add(cells);
                }
                sheets.add(new SheetData(sheet.getSheetName(), rows));
            }
        } catch (RuntimeException e) {
            // POI's own exceptions for a file that isn't a workbook, or is damaged.
            throw new IOException("This isn't a workbook this application can read: " + e.getMessage(), e);
        }
        return sheets;
    }

    /** A cell's value as text: numbers in plain form, dates as ISO 8601, formulas by their last result. */
    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? String.valueOf(cell.getLocalDateTimeCellValue()).replace("T00:00", "")
                    : new BigDecimal(Double.toString(cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** Delimited text, by the description's delimiter, quote and character encoding; one "sheet" named after the format. */
    private static List<SheetData> readText(KnownFormatDescription description, byte[] sample) throws IOException {
        Charset charset;
        try {
            charset = description.getCharacterEncoding().isBlank() ? StandardCharsets.UTF_8
                    : Charset.forName(description.getCharacterEncoding());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IOException("'" + description.getCharacterEncoding() + "' isn't a character encoding Java knows.");
        }
        String text = new String(sample, charset);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = description.delimiterChar();
        char quote = description.getQuote().isEmpty() ? 0 : description.getQuote().charAt(0);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length() && rows.size() < MAX_ROWS; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == quote && i + 1 < text.length() && text.charAt(i + 1) == quote) {
                    field.append(c);
                    i++;
                } else if (c == quote) {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (quote != 0 && c == quote && field.isEmpty()) {
                quoted = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return List.of(new SheetData(KnownFormat.of(description.getFormat()).key().toUpperCase() + " file", rows));
    }

    /**
     * One part per non-empty sheet of {@code sheets}, its headers taken from its
     * first non-empty row; columns already described keep their meanings.
     */
    public List<KnownFormatDescription.Part> propose(KnownFormatDescription description, List<SheetData> sheets) {
        List<KnownFormatDescription.Part> parts = new ArrayList<>();
        for (SheetData sheet : sheets) {
            int headerRow = -1;
            for (int r = 0; r < sheet.rows().size() && headerRow < 0; r++) {
                if (sheet.rows().get(r).stream().anyMatch(c -> !c.isBlank())) {
                    headerRow = r;
                }
            }
            if (headerRow < 0) {
                continue;
            }
            Optional<KnownFormatDescription.Part> existing = description.getParts().stream()
                    .filter(p -> p.name().equals(sheet.name())).findFirst();
            Map<String, KnownFormatDescription.Item> known = new LinkedHashMap<>();
            existing.ifPresent(p -> p.items().forEach(i -> known.put(i.header(), i)));
            List<KnownFormatDescription.Item> items = new ArrayList<>();
            for (String header : sheet.rows().get(headerRow)) {
                if (!header.isBlank() && items.stream().noneMatch(i -> i.header().equals(header.strip()))) {
                    items.add(known.getOrDefault(header.strip(), new KnownFormatDescription.Item(
                            KnownFormatDescription.newId(), header.strip(), Semantics.NONE)));
                }
            }
            parts.add(new KnownFormatDescription.Part(existing.map(KnownFormatDescription.Part::id)
                    .orElseGet(KnownFormatDescription::newId), sheet.name(), headerRow + 1, items));
        }
        return parts;
    }

    /** Tests every described column against {@code sheets}. */
    public List<ColumnResult> test(KnownFormatDescription description, List<SheetData> sheets) {
        List<ColumnResult> results = new ArrayList<>();
        for (KnownFormatDescription.Part part : description.getParts()) {
            Optional<SheetData> sheet = sheets.stream().filter(s -> s.name().equals(part.name())).findFirst();
            for (KnownFormatDescription.Item item : part.items()) {
                if (sheet.isEmpty()) {
                    results.add(new ColumnResult(part.name(), item.header(), "", "There's no sheet called '"
                            + part.name() + "' in the sample.", 0, List.of(), List.of()));
                    continue;
                }
                results.add(testColumn(sheet.get(), part, item));
            }
        }
        return results;
    }

    private static ColumnResult testColumn(SheetData sheet, KnownFormatDescription.Part part,
                                           KnownFormatDescription.Item item) {
        int headerRow = part.headerRow() - 1;
        int column = -1;
        List<String> headers = headerRow < sheet.rows().size() ? sheet.rows().get(headerRow) : List.of();
        for (int c = 0; c < headers.size() && column < 0; c++) {
            if (headers.get(c).strip().equals(item.header())) {
                column = c;
            }
        }
        if (column < 0) {
            return new ColumnResult(part.name(), item.header(), "", "No column on row " + part.headerRow()
                    + " of '" + part.name() + "' has the header '" + item.header() + "'.", 0, List.of(), List.of());
        }
        Semantics s = item.semantics();
        List<Shown> shown = new ArrayList<>();
        int values = 0, blank = 0, fill = 0, unknownCode = 0, outOfRange = 0, notNumber = 0;
        boolean numeric = s.isScaled() || s.validMin() != null || s.validMax() != null;
        for (int r = headerRow + 1; r < sheet.rows().size(); r++) {
            String text = sheet.cell(r, column).strip();
            if (text.isEmpty()) {
                blank++;
                continue;
            }
            values++;
            Object raw = number(text).<Object>map(n -> n).orElse(text);
            String meaning;
            if (s.isFill(raw)) {
                fill++;
                meaning = "no value (fill)";
            } else if (!s.codes().isEmpty()) {
                meaning = s.meaningOf(raw);
                if (meaning == null) {
                    unknownCode++;
                    meaning = "not in the code list";
                }
            } else if (numeric && !(raw instanceof BigDecimal)) {
                notNumber++;
                meaning = "not a number";
            } else {
                BigDecimal physical = s.isScaled() ? s.physicalValue(raw) : raw instanceof BigDecimal b ? b : null;
                meaning = physical == null ? "" : physical.stripTrailingZeros().toPlainString()
                        + (s.units() == null ? "" : " " + s.units());
                if (physical != null && ((s.validMin() != null && physical.compareTo(s.validMin()) < 0)
                        || (s.validMax() != null && physical.compareTo(s.validMax()) > 0))) {
                    outOfRange++;
                    meaning += " (outside the valid range)";
                }
                if (!s.isScaled() && s.units() == null && !meaning.contains("outside")) {
                    meaning = "";
                }
            }
            if (shown.size() < SHOWN) {
                shown.add(new Shown(text, meaning));
            }
        }
        List<String> checks = new ArrayList<>();
        add(checks, unknownCode, "not in the code list");
        add(checks, outOfRange, "outside the valid range");
        add(checks, notNumber, "not a number, though the column is scaled or has a valid range");
        add(checks, fill, "the fill value (no data)");
        add(checks, blank, "blank");
        return new ColumnResult(part.name(), item.header(), "column " + columnLetters(column), null, values, shown,
                checks);
    }

    private static void add(List<String> checks, int count, String what) {
        if (count > 0) {
            checks.add(count + (count == 1 ? " value is " : " values are ") + what);
        }
    }

    private static Optional<BigDecimal> number(String text) {
        try {
            return Optional.of(new BigDecimal(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Spreadsheet column letters: 0 is A, 25 is Z, 26 is AA. */
    static String columnLetters(int column) {
        StringBuilder sb = new StringBuilder();
        for (int c = column + 1; c > 0; c = (c - 1) / 26) {
            sb.insert(0, (char) ('A' + (c - 1) % 26));
        }
        return sb.toString();
    }
}
