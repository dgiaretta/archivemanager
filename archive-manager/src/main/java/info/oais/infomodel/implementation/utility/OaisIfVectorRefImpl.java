/**
 *
 */
package info.oais.infomodel.implementation.utility;

import info.oais.infomodel.interfaces.utility.OaisIfGeometry;
import info.oais.infomodel.interfaces.utility.OaisIfVector;

/**
 * Implementation of OaisIfVector
 *
 * Column zero holds each row's OaisIfGeometry; all other columns are
 * ordinary feature attributes, added the same way as for any OaisIfTable.
 *
 * Note: unlike OaisIfTimeSeriesRefImpl (which builds a second, internal
 * m_timeSeries table that the inherited accessors never actually read from),
 * this class calls the inherited addColumn(...) directly, so getRowCount(),
 * getColumnCount() etc. - all inherited from OaisIfTableRefImpl - correctly
 * see the geometry column.
 */
public class OaisIfVectorRefImpl extends OaisIfTableRefImpl implements OaisIfVector {

	/**
	 * Zero argument constructor. Adds column zero, "Geometry", of class
	 * OaisIfGeometry; further attribute columns can be added the usual way
	 * with addColumn(...).
	 */
	OaisIfVectorRefImpl() {
		super();
		addColumn("Geometry", OaisIfGeometry.class);
	}
}
