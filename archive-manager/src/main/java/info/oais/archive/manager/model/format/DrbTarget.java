package info.oais.archive.manager.model.format;

/**
 * Which real DRB implementation a generated DRB description targets -- two
 * different libraries, not two flavors of one: {@code drb-python}
 * (https://gitlab.com/drb-python; the description is a driver package), and
 * GAEL's original Java DRB ({@code fr.gael.drb}; the description is a DRB SDF
 * schema, applied through {@code oais-structure-drb}).
 */
public enum DrbTarget {
    PYTHON, JAVA
}
