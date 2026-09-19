package info.oais.infomodel.implementation.representationinformation;

import java.io.Serializable;

import info.oais.infomodel.interfaces.representationinformation.AcyclicDirectedGraph;
import info.oais.infomodel.interfaces.representationinformation.RootVertex;

/**
 * This class provides methods which can be used to navigate a RepInfoNetwork.
 *
 * The acyclic directed graph has one root vertex.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
public class AcyclicDirectedGraphRefImpl  implements AcyclicDirectedGraph, Serializable {

	/**
	 * SerialVersionUID to use when object is serialised.
	 */
	private static final long serialVersionUID = 8015643630744334736L;

	/**
	 * Internal value for the RootVertex of this acyclic graph.
	 */
	RootVertex m_RootVertex = null;

	/**
	 * Get the RootVertex of the graph.
	 *
	 * @return The RootVertex of the graph.
	 */
	public RootVertex getRootVertex() {
		return m_RootVertex;
	}

	/**
	 * Set the RootVertex of the graph.
	 *
	 * @param rv The RootVertex of the graph.
	 */
	public void setRootVertex(RootVertex rv) {
		m_RootVertex = rv;
	}

}
