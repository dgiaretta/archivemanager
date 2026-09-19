package info.oais.infomodel.implementation.representationinformation;

import java.io.Serializable;

import info.oais.infomodel.interfaces.representationinformation.RootVertex;

/**
 * The root Vertex of the graph.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:47
 */

public class RootVertexRefImpl extends VertexRefImpl implements RootVertex, Serializable {

	/**
	 * To be used when object is serialized.
	 */
	private static final long serialVersionUID = 7978020382539635519L;

	/**
	 * This is a method which can be used to check whether or not the graph is acyclic.
	 * For example if a Vertex or an Edge is added this can be used to check if this
	 * would cause the graph to contain a cycle i.e. FAIL to be acyclic, in which case
	 * the added component would be removed.
	 *
	 * @return boolean which is true if the Graph is acyclic.
	 */
	public boolean isAcyclic() {
		//TODO
		return true;
	}

}
