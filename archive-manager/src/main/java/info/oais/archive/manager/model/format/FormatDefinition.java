package info.oais.archive.manager.model.format;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A format description being built up interactively, held in the HTTP
 * session for the duration of one editing pass (see {@code RepInfoToolController})
 * and never written to the archive until the user explicitly saves it --
 * mirrors {@code ImportController}'s "build in a throwaway place first,
 * commit only on success" approach, just applied to something assembled
 * element-by-element instead of pasted in as one document.
 *
 * <p>A byte-layout definition's structure is an engine-neutral
 * {@link FormatDescription} tree from {@code oais-structure-api} (see
 * {@link #toFormatDescription()}), which every generator works from; a
 * logical-tree (HDF5-style) definition is a list of {@link Hdf5Node} rows.
 *
 * <p>Deliberately a plain mutable class rather than a record: the whole
 * point is that the editor mutates one instance of this in place, one
 * element or node at a time, across many small POSTs.
 */
public class FormatDefinition implements Serializable {

    /** The id of the root record, i.e. the whole file. */
    public static final String ROOT_ID = "root";

    private String name = "";
    private String notes = "";
    private FormatDefinitionKind kind = FormatDefinitionKind.BYTE_LAYOUT;
    private ByteOrder defaultByteOrder = ByteOrder.BIG_ENDIAN;
    private List<String> fileExtensions = new ArrayList<>();
    private RecordDescription root = emptyRoot();
    private final List<Hdf5Node> nodes = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    public FormatDefinitionKind getKind() {
        return kind;
    }

    public void setKind(FormatDefinitionKind kind) {
        this.kind = kind;
    }

    public ByteOrder getDefaultByteOrder() {
        return defaultByteOrder;
    }

    public void setDefaultByteOrder(ByteOrder defaultByteOrder) {
        this.defaultByteOrder = defaultByteOrder == null ? ByteOrder.BIG_ENDIAN : defaultByteOrder;
    }

    /**
     * File name extensions this format's files usually carry, lower-case and
     * without the leading dot (e.g. {@code fits}, {@code fit}). Optional; used
     * where a target needs to recognise the format's files by name, such as the
     * generated drb-python driver's topic signature.
     */
    public List<String> getFileExtensions() {
        return fileExtensions;
    }

    /** @param extensions comma- or space-separated, with or without leading dots, e.g. {@code ".fits, fit"} */
    public void setFileExtensions(String extensions) {
        List<String> parsed = new ArrayList<>();
        if (extensions != null) {
            for (String part : extensions.split("[,\\s]+")) {
                String ext = part.strip().replaceFirst("^\\.+", "").toLowerCase();
                if (ext.matches("[a-z0-9_+-]+") && !parsed.contains(ext)) {
                    parsed.add(ext);
                }
            }
        }
        this.fileExtensions = parsed;
    }

    /**
     * The byte layout's structure: a record standing for the whole file.
     * Meaningful only when {@code kind == BYTE_LAYOUT}.
     */
    public RecordDescription getRoot() {
        return root;
    }

    public void setRoot(RecordDescription root) {
        this.root = root == null ? emptyRoot() : root;
    }

    /** Applies an edit (see {@code Descriptions}) to the byte layout's structure. */
    public void editRoot(UnaryOperator<RecordDescription> edit) {
        setRoot(edit.apply(root));
    }

    /**
     * The whole byte-layout definition as the engine-neutral description the
     * generators work from. The root record takes its name from the format's
     * name, so the two can't drift apart.
     */
    public FormatDescription toFormatDescription() {
        RecordDescription namedRoot = new RecordDescription(root.id(), rootName(), root.children(), root.text(),
                root.occurrence(), root.semantics());
        return new FormatDescription(name, notes, defaultByteOrder, fileExtensions, namedRoot);
    }

    private String rootName() {
        String snake = name.strip().replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_").replaceAll("[^A-Za-z0-9]+", "_")
                .toLowerCase().replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (snake.isEmpty()) {
            return "format";
        }
        return Character.isDigit(snake.charAt(0)) ? "f_" + snake : snake;
    }

    private static RecordDescription emptyRoot() {
        return new RecordDescription(ROOT_ID, "format", List.of(), null, Occurrence.ONCE, Semantics.NONE);
    }

    /** HDF5 logical-schema rows, in document order. Meaningful only when {@code kind == LOGICAL_TREE}. */
    public List<Hdf5Node> getNodes() {
        return nodes;
    }

    public void addNode(Hdf5Node node) {
        nodes.add(node);
    }

    public void replaceNode(int index, Hdf5Node node) {
        if (index >= 0 && index < nodes.size()) {
            nodes.set(index, node);
        }
    }

    public void removeNode(int index) {
        if (index >= 0 && index < nodes.size()) {
            nodes.remove(index);
        }
    }

    public void moveNode(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= nodes.size() || target < 0 || target >= nodes.size()) {
            return;
        }
        nodes.add(target, nodes.remove(index));
    }
}
