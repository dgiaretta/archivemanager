package info.oais.archive.manager.model.format;

/** What one row of a {@link FormatDefinitionKind#LOGICAL_TREE} definition represents. */
public enum Hdf5NodeKind {
    /** A container for other groups/datasets, roughly a directory. No {@code dtype}/{@code shape} of its own. */
    GROUP,
    /** A typed, shaped array of values. */
    DATASET,
    /** A small named value attached to a group or dataset (its path uses {@code @}, see {@link Hdf5Node}). */
    ATTRIBUTE
}
