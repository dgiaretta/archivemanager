package info.oais.archive.manager.model.format;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.DescriptionLanguage;
import info.oais.infomodel.structure.description.Feature;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private Set<DescriptionLanguage> targets = EnumSet.allOf(DescriptionLanguage.class);
    private final Map<DescriptionLanguage, String> handWritten = new EnumMap<>(DescriptionLanguage.class);
    private final Map<DescriptionLanguage, String> generatedWhenWritten = new EnumMap<>(DescriptionLanguage.class);
    private final List<Hdf5Node> nodes = new ArrayList<>();
    private String formatRegistryIdentifier;
    private String semanticDictionary;
    private String forDataObject;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * The format's identifier in a format registry, e.g. {@code PRONOM x-fmt/383}
     * for FITS, saved on each structure description; null if none is given.
     */
    public String getFormatRegistryIdentifier() {
        return formatRegistryIdentifier;
    }

    public void setFormatRegistryIdentifier(String formatRegistryIdentifier) {
        this.formatRegistryIdentifier = formatRegistryIdentifier == null || formatRegistryIdentifier.isBlank() ? null
                : formatRegistryIdentifier.strip();
    }

    /**
     * Semantic Representation Information shared by every description of
     * this kind -- e.g. the FITS keyword dictionary -- which the saved
     * Semantic Representation Information is interpreted with; null if none.
     */
    public String getSemanticDictionary() {
        return semanticDictionary;
    }

    public void setSemanticDictionary(String semanticDictionary) {
        this.semanticDictionary = semanticDictionary;
    }

    /** The Data Object (its id) this was made from, to save it for by default; null if none. */
    public String getForDataObject() {
        return forDataObject;
    }

    public void setForDataObject(String forDataObject) {
        this.forDataObject = forDataObject;
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
    /**
     * The description languages to generate. All of them by default, which
     * limits the description to the core every language can express;
     * choosing fewer makes the {@link Feature}s they all support available.
     */
    public Set<DescriptionLanguage> getTargets() {
        return EnumSet.copyOf(targets);
    }

    /** @param targets at least one language; an empty set means all of them */
    public void setTargets(Set<DescriptionLanguage> targets) {
        this.targets = targets == null || targets.isEmpty() ? EnumSet.allOf(DescriptionLanguage.class)
                : EnumSet.copyOf(targets);
    }

    public boolean targets(DescriptionLanguage language) {
        return targets.contains(language);
    }

    /** Whether every chosen language can express {@code feature}, so the editor can offer it. */
    public boolean allows(Feature feature) {
        return feature.supportedByAll(targets);
    }

    /**
     * The description written by hand in {@code language}, if any: it's used
     * instead of the generated one (preview, sample test, download, save), for
     * what the element tree can't express. The tree still provides the meanings.
     */
    public Optional<String> handWritten(DescriptionLanguage language) {
        return Optional.ofNullable(handWritten.get(language));
    }

    public Map<DescriptionLanguage, String> getHandWritten() {
        return Map.copyOf(handWritten);
    }

    /**
     * @param generated what was generated for {@code language} when the text was written (its
     *                  starting point), to tell later whether the tree has changed since; null if none
     */
    public void setHandWritten(DescriptionLanguage language, String text, String generated) {
        handWritten.put(language, text);
        if (generated == null) {
            generatedWhenWritten.remove(language);
        } else {
            generatedWhenWritten.put(language, generated);
        }
        targets.add(language);
    }

    public void clearHandWritten(DescriptionLanguage language) {
        handWritten.remove(language);
        generatedWhenWritten.remove(language);
    }

    /** Whether the tree now generates something different from when {@code language}'s text was written. */
    public boolean handWrittenOutOfDate(DescriptionLanguage language, String generatedNow) {
        String then = generatedWhenWritten.get(language);
        return handWritten.containsKey(language) && then != null && generatedNow != null && !then.equals(generatedNow);
    }

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
