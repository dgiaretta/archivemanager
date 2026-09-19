package info.oais.infomodel.interfaces.utility;

/**
 * The kind of geometry an {@link OaisIfGeometry} holds - the vocabulary a
 * vector/GIS format's geometry column is drawn from (broadly, the OGC Simple
 * Features set), kept intentionally small.
 */
public enum GeometryKind {

	/** A single coordinate tuple. */
	POINT,

	/** An ordered sequence of coordinate tuples describing a connected line. */
	LINE_STRING,

	/**
	 * An ordered sequence of coordinate tuples describing a closed ring (the
	 * first and last tuples coincide); holes are not represented here - a
	 * polygon with holes is multiple {@link OaisIfGeometry} values, one per
	 * ring.
	 */
	POLYGON,

	/** An unordered collection of otherwise-independent points. */
	MULTI_POINT,

	/** An unordered collection of otherwise-independent line strings. */
	MULTI_LINE_STRING,

	/** An unordered collection of otherwise-independent polygons. */
	MULTI_POLYGON
}
