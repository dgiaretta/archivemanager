package info.oais.infomodel.implementation.representationinformation;

import java.io.Serializable;

import info.oais.infomodel.interfaces.representationinformation.DirectedEdge;
import info.oais.infomodel.interfaces.representationinformation.Vertex;
/**
 * Each edge has exactly one Source Vertex and one Target Vertex.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
public class DirectedEdgeRefImpl implements DirectedEdge, Serializable {

	/**
	 * To be used when object is serialized
	 */
	private static final long serialVersionUID = -5673935707384232138L;

	/**
	 * Internal value of the sourceVertex.
	 */
	public Vertex sourceVertex = null;
	/**
	 * Internal value of the targeteVertex.
	 */
	public Vertex targetVertex = null;

	/**
	 * Internal value of the type.
	 */
	public String type = null;

	/**
	 * Returns the Vertex from which the Edge starts.
	 *
	 * @return sourceVertex The originating end of the Edge i.e. the Edge goes from
	 * the source to the target.
	 *
	 */
	public Vertex getSourceVertex() {
		return sourceVertex;
	}

	/**
	 * Set the source Vertex for this Edge.
	 *
	 * @param ver The source Vertex for this edge.
	 */
	public void setSourceVertex(Vertex ver) {
		sourceVertex = ver;
	}

	/**
	 * Returns the Vertex at which the Edge ends.
	 *
	 * @return tergetVertex The target end of the Edge i.e. the Edge goes from the
	 * source to the target.
	 *
	 */
	public Vertex getTargetVertex() {
		return targetVertex;
	}

	/**
	 * Set the target Vertex for this Edge.
	 *
	 * @param ver The target Vertex for this edge.
	 */
	public void setTargetVertex(Vertex ver) {
		targetVertex = ver;
	}

	/**
	 * Returns the type associated with the Edge.
	 * @return String The type of association the Edge represents.
	 */
	public String getType() {
		return this.type;
	}

	/**
	 * Set the type associated with this Edge.
	 *
	 * @param type  The type for this Edge.
	 */
	public void setType(String type) {
		this.type = type;
	}



}
