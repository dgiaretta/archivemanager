/**
 *
 */
package info.oais.infomodel.implementation.utility;

import info.oais.infomodel.interfaces.utility.GeometryKind;
import info.oais.infomodel.interfaces.utility.OaisIfGeometry;

/**
 * Implementation of OaisIfGeometry
 */
public class OaisIfGeometryRefImpl implements OaisIfGeometry {

	private GeometryKind m_kind;
	private int m_dimension = 2;
	private double[][] m_coordinates = new double[0][];

	/**
	 * Zero argument constructor
	 */
	public OaisIfGeometryRefImpl() {
	}

	/**
	 * Constructor with all the fields set at once.
	 * @param kind the geometry kind
	 * @param dimension 2 or 3
	 * @param coordinates the coordinate tuples, each of length dimension
	 */
	public OaisIfGeometryRefImpl(GeometryKind kind, int dimension, double[][] coordinates) {
		m_kind = kind;
		m_dimension = dimension;
		m_coordinates = coordinates;
	}

	public GeometryKind getKind() {
		return m_kind;
	}

	public int getDimension() {
		return m_dimension;
	}

	public double[][] getCoordinates() {
		return m_coordinates;
	}

	public void setKind(GeometryKind kind) {
		m_kind = kind;
	}

	public void setDimension(int dimension) {
		m_dimension = dimension;
	}

	public void setCoordinates(double[][] coordinates) {
		m_coordinates = coordinates;
	}

	/**
	 * A human-readable rendering of this geometry - its kind, followed by its
	 * coordinate tuples in parentheses, ordinates space-separated within a
	 * tuple and tuples comma-separated (e.g.
	 * {@code "LINE_STRING (0.0 0.0, 10.0 0.0, 10.0 5.0)"}) - the same
	 * "KIND (ordinates, ...)" shape as Well-Known Text, which this
	 * intentionally resembles without claiming to fully implement it. Used
	 * wherever this value ends up displayed as text without any more
	 * specific formatting applied - e.g. a {@link javax.swing.JTable} cell
	 * (whose default renderer calls {@code toString()} on a value with no
	 * registered renderer of its own) or a CSV export - rather than the
	 * default {@code Object.toString()}.
	 */
	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		text.append(m_kind).append(" (");
		for (int t = 0; t < m_coordinates.length; t++) {
			if (t > 0) {
				text.append(", ");
			}
			double[] tuple = m_coordinates[t];
			for (int o = 0; o < tuple.length; o++) {
				if (o > 0) {
					text.append(' ');
				}
				text.append(tuple[o]);
			}
		}
		text.append(')');
		return text.toString();
	}
}
