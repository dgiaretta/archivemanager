package info.oais.infomodel.interfaces.utility;

/**
 * A single feature's geometry: what kind of shape it is, and the ordered
 * coordinate tuples that describe it.
 *
 * Modelled after OaisIfTimeStamp - a small value interface that OaisIfVector
 * constrains column zero to hold, the same way OaisIfTimeSeries constrains
 * its own column zero to an OaisIfTimeStamp.
 *
 */
public interface OaisIfGeometry {

	/**
	 * Find the kind of shape this geometry is.
	 * @return which kind of shape this geometry is
	 */
	public GeometryKind getKind();

	/**
	 * The number of ordinates per coordinate tuple - 2 for plane (x, y)
	 * coordinates, 3 if a z/elevation ordinate is present. Every tuple
	 * returned by getCoordinates() has exactly this many ordinates.
	 *
	 * @return 2 or 3
	 */
	public int getDimension();

	/**
	 * The ordered coordinate tuples making up this geometry - a single tuple
	 * for GeometryKind.POINT, the vertices in order for
	 * GeometryKind.LINE_STRING/GeometryKind.POLYGON (a POLYGON's first and
	 * last tuple coincide, closing the ring), or the independent members for
	 * a MULTI_* kind.
	 *
	 * @return the coordinate tuples, each of length getDimension()
	 */
	public double[][] getCoordinates();

	/**
	 * Set which kind of shape this geometry is.
	 * @param kind the geometry kind
	 */
	public void setKind(GeometryKind kind);

	/**
	 * Set the number of ordinates per coordinate tuple.
	 * @param dimension 2 or 3
	 */
	public void setDimension(int dimension);

	/**
	 * Set the ordered coordinate tuples making up this geometry.
	 * @param coordinates the coordinate tuples, each of length getDimension()
	 */
	public void setCoordinates(double[][] coordinates);
}
