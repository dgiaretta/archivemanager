package info.oais.archive.manager.model.format;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A format description being built up interactively, held in the HTTP
 * session for the duration of one editing pass (see {@code RepInfoToolController})
 * and never written to the archive until the user explicitly saves it --
 * mirrors {@code ImportController}'s "build in a throwaway place first,
 * commit only on success" approach, just applied to something assembled
 * field-by-field instead of pasted in as one document.
 *
 * <p>Deliberately a plain mutable class rather than a record: the whole
 * point is that the editor mutates one instance of this in place, one field
 * or node at a time, across many small POSTs.
 */
public class FormatDefinition implements Serializable {

    private String name = "";
    private String notes = "";
    private FormatDefinitionKind kind = FormatDefinitionKind.BYTE_LAYOUT;
    private ByteOrder defaultByteOrder = ByteOrder.BIG_ENDIAN;
    private final List<FormatField> fields = new ArrayList<>();
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
        this.defaultByteOrder = defaultByteOrder;
    }

    /** Byte-layout fields, in file order. Meaningful only when {@code kind == BYTE_LAYOUT}. */
    public List<FormatField> getFields() {
        return fields;
    }

    /** HDF5 logical-schema rows, in document order. Meaningful only when {@code kind == LOGICAL_TREE}. */
    public List<Hdf5Node> getNodes() {
        return nodes;
    }

    public void addField(FormatField field) {
        fields.add(field);
    }

    public void replaceField(int index, FormatField field) {
        if (index >= 0 && index < fields.size()) {
            fields.set(index, field);
        }
    }

    public void removeField(int index) {
        if (index >= 0 && index < fields.size()) {
            fields.remove(index);
        }
    }

    public void moveField(int index, int delta) {
        move(fields, index, delta);
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
        move(nodes, index, delta);
    }

    private <T> void move(List<T> list, int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= list.size() || target < 0 || target >= list.size()) {
            return;
        }
        T item = list.remove(index);
        list.add(target, item);
    }
}
