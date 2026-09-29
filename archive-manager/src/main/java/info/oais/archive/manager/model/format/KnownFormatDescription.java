package info.oais.archive.manager.model.format;

import info.oais.infomodel.structure.description.Semantics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The meaning of data whose format is already known -- a spreadsheet, or
 * delimited text -- being built in RepInfo Tools: Semantic Representation
 * Information for data that already has, or can be given, Structure and Other
 * Representation Information (the format's specification, and software that
 * reads it). Unlike a {@link FormatDefinition}, nothing describes bytes here.
 *
 * <p>Tables are taken to have one variable per column: each {@link Part} is
 * a sheet (for delimited text, the file), found by name, whose column
 * headers are on {@link Part#headerRow}; each {@link Item} is a column, found
 * by its header, with its meaning.</p>
 *
 * <p>The format profile (Structure Representation Information) refines the
 * format's registry identifier with what it leaves out: version, and for
 * text the character encoding, line endings, delimiter and quote.</p>
 */
public class KnownFormatDescription implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * A sheet (or, for delimited text, the file).
     *
     * @param name      the sheet's name, as the workbook shows it
     * @param headerRow the 1-based row holding the column headers; the data starts on the row after it
     */
    public record Part(String id, String name, int headerRow, List<Item> items) implements Serializable {
        public Part {
            items = List.copyOf(items);
        }
    }

    /**
     * A column: one variable.
     *
     * @param header the column's header text, which finds it
     */
    public record Item(String id, String header, Semantics semantics) implements Serializable {
    }

    private String name = "";
    private String notes = "";
    private String format = "xlsx";
    private String registryId = "";
    private String version = "";
    private String characterEncoding = "";
    private String lineEnding = "";
    private String delimiter = "";
    private String quote = "";
    private final List<Part> parts = new ArrayList<>();

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.strip();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes.strip();
    }

    /** The {@code KnownFormat} key, e.g. {@code xlsx}. */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getRegistryId() {
        return registryId;
    }

    public void setRegistryId(String registryId) {
        this.registryId = registryId == null ? "" : registryId.strip();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? "" : version.strip();
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding == null ? "" : characterEncoding.strip();
    }

    /** {@code CRLF}, {@code LF} or {@code CR}; empty if not stated. */
    public String getLineEnding() {
        return lineEnding;
    }

    public void setLineEnding(String lineEnding) {
        this.lineEnding = lineEnding == null ? "" : lineEnding.strip();
    }

    /** The field delimiter; {@code TAB} for a tab. */
    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter == null ? "" : delimiter.strip();
    }

    public String getQuote() {
        return quote;
    }

    public void setQuote(String quote) {
        this.quote = quote == null ? "" : quote.strip();
    }

    /** The delimiter as a character: {@code TAB} is a tab; a comma if none is set. */
    public char delimiterChar() {
        if (delimiter.equalsIgnoreCase("TAB") || delimiter.equals("\\t")) {
            return '\t';
        }
        return delimiter.isEmpty() ? ',' : delimiter.charAt(0);
    }

    public List<Part> getParts() {
        return List.copyOf(parts);
    }

    public Optional<Part> part(String id) {
        return parts.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public void addPart(Part part) {
        parts.add(part);
    }

    public void replacePart(String id, Part part) {
        parts.replaceAll(p -> p.id().equals(id) ? part : p);
    }

    public void removePart(String id) {
        parts.removeIf(p -> p.id().equals(id));
    }

    public void setParts(List<Part> newParts) {
        parts.clear();
        parts.addAll(newParts);
    }

    /** The part holding item {@code itemId}, and the item. */
    public Optional<Part> partOfItem(String itemId) {
        return parts.stream().filter(p -> p.items().stream().anyMatch(i -> i.id().equals(itemId))).findFirst();
    }

    public Optional<Item> item(String itemId) {
        return parts.stream().flatMap(p -> p.items().stream()).filter(i -> i.id().equals(itemId)).findFirst();
    }

    /** Replaces item {@code itemId} (in whichever part holds it). */
    public void replaceItem(String itemId, Item item) {
        partOfItem(itemId).ifPresent(p -> replacePart(p.id(), new Part(p.id(), p.name(), p.headerRow(),
                p.items().stream().map(i -> i.id().equals(itemId) ? item : i).toList())));
    }

    public void removeItem(String itemId) {
        partOfItem(itemId).ifPresent(p -> replacePart(p.id(), new Part(p.id(), p.name(), p.headerRow(),
                p.items().stream().filter(i -> !i.id().equals(itemId)).toList())));
    }

    public void addItem(String partId, Item item) {
        part(partId).ifPresent(p -> {
            List<Item> items = new ArrayList<>(p.items());
            items.add(item);
            replacePart(partId, new Part(p.id(), p.name(), p.headerRow(), items));
        });
    }

    /** Where item {@code item} of part {@code part} is, e.g. {@code Readings!"Air temperature"}. */
    public static String locator(Part part, Item item) {
        return part.name() + "!\"" + item.header() + "\"";
    }
}
