package info.oais.infomodel.interfaces.utility;

/**
 * An interface useful for dealing with vector (feature/geometry) information
 *
 * Column zero must have class OaisIfGeometry, and holds the geometry of the
 * feature described by this row. All other columns provide attributes of
 * that feature.
 *
 */
public interface OaisIfVector extends OaisIfTable {

}
