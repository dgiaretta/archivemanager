package info.oais.archive.manager.model.format;

/**
 * Which of the two shapes a {@link FormatDefinition} takes -- these aren't
 * variations on one model, they're structurally different (a flat sequence
 * of fields vs. a tree of named rows), which is why {@link FormatDefinition}
 * carries both a {@code fields} list and a {@code nodes} list but only one
 * is ever meaningful at a time. The Kaitai and DFDL generators (package
 * {@code service.format}) only accept {@link #BYTE_LAYOUT} -- a logical tree
 * has no sequential byte order for either language to describe.
 */
public enum FormatDefinitionKind {
    /** A sequential binary format, like FITS: fields in file order, each with a fixed byte width. */
    BYTE_LAYOUT,
    /** A self-describing container's logical schema, like HDF5: a group/dataset/attribute tree. */
    LOGICAL_TREE
}
