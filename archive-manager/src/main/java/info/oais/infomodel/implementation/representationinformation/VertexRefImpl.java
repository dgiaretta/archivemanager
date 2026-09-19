package info.oais.infomodel.implementation.representationinformation;

import java.io.Serializable;
import java.util.ArrayList;

import info.oais.infomodel.interfaces.representationinformation.DirectedEdge;
import info.oais.infomodel.interfaces.representationinformation.RepInfoGroup;
import info.oais.infomodel.interfaces.representationinformation.Vertex;

/**
 * A Vertex points to zero or more other Vertexs (sic), and it is pointed to by at
 * least one other Vertex, unless it is the RootVertex. The VertexType tells one
 * whether the the Vertexs pointed to should be taken together or are alternatives.
 * A Vertex may have an associated RepresentationInformationObject.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:47
 */

public class VertexRefImpl implements Vertex, Serializable {

	/**
	 * To be used when object is serialized
	 */
	private static final long serialVersionUID = -2736210650449877259L;

	/**
	 * Internal value for the Incoming edges.
	 */
	public ArrayList<DirectedEdge> m_inwardDirectedEdges = null;

	/**
	 * Internal value for the Outward edges.
	 */
	public ArrayList<DirectedEdge> m_outwardDirectedEdges = null;


	/**
	 * The VertexType is RepInfoGroup or one of its sub-types. If it is an OrGroup
	 * then any of the available Edges may be followed, but if it is an AndGroup then
	 * all the associated Edges must be followed.
	 */
	public RepInfoGroup m_vertexType = null;


	/**
	 * Returns an ArrayList of Edges which end at this Vertex.
	 * The elements of the ArrayList can be inspected or added to.
	 *
	 * @return inwardEdges The ArrayList of Edges which end at this Vertex
	 */
	public ArrayList<DirectedEdge> getInwardEdges(){
		return m_inwardDirectedEdges;
	}

	/**
	 * Returns an ArrayList of Edges which start at this Vertex
	 * @return outwardEdges The ArrayList of Edges which start at this vertex.
	 */
	public ArrayList<DirectedEdge> getOutwardEdges(){
		return m_outwardDirectedEdges;
	}

	/**
	 * Get the VertexType - a subtype of RepInfoGroup - either AndGroup or OrGroup.
	 *
	 * @return The VertexType - a subtype of RepInfoGroup - either AndGroup or OrGroup.
	 */
	public RepInfoGroup getVertexType() {
		return m_vertexType;
	}

	/**
	 * Set the VertexType - a subtype of RepInfoGroup - either AndGroup or OrGroup.
	 *
	 * @param rig The VertexType - a subtype of RepInfoGroup - either AndGroup or OrGroup.
	 */
	public void setVertexType(RepInfoGroup rig) {
		m_vertexType = rig;
	}
}
