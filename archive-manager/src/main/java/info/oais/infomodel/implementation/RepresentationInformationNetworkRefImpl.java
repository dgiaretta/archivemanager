package info.oais.infomodel.implementation;

import java.io.Serializable;

import info.oais.infomodel.interfaces.RepresentationInformationNetwork;
import info.oais.infomodel.interfaces.representationinformation.AcyclicDirectedGraph;
import info.oais.infomodel.interfaces.representationinformation.RootVertex;

public class RepresentationInformationNetworkRefImpl extends RepresentationInformationRefImpl
		implements RepresentationInformationNetwork, AcyclicDirectedGraph, Serializable {

	/**
	 * For serialising object
	 */
	private static final long serialVersionUID = -5688872469674766429L;

	/**
	 * The root Vertex for the RIN
	 */
	private RootVertex rootVertex = null;


	/**
	 * Create the RIN given the RootVertex
	 *
	 * @param rv The RootVertex to set for the RIN.
	 */
	public RepresentationInformationNetworkRefImpl(RootVertex rv) {
		super();
		this.rootVertex = rv;
	}

	/**
	 * Get the Root Vertex for the RIN.
	 *
	 * @return The RootVertex for the RIN.
	 */
	@Override
	public RootVertex getRootVertex() {
		return rootVertex;
	}

	/**
	 * Set the RootVertex for the RIN.
	 *
	 * @param rv The RootVertex for the RIN
	 */
	@Override
	public void setRootVertex(RootVertex rv) {
		rootVertex = rv;

	}
}
