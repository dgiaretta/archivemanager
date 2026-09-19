package info.oais.archive.manager.model.format;

/**
 * Which real DRB implementation a generated DRB description targets -- these
 * are two different libraries, not two flavors of one: {@code drb-python}
 * (https://gitlab.com/drb-python) has no Java equivalent API, and the
 * original Java DRB ({@code fr.gael.drb}, as consumed by reflection in
 * oais-structure-adapters' {@code oais-structure-drb} module) has no Python
 * equivalent either.
 */
public enum DrbTarget {
    PYTHON, JAVA
}
