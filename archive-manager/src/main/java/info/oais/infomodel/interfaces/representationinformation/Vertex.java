package info.oais.infomodel.interfaces.representationinformation;

import java.util.ArrayList;


/**
 * A Vertex points to zero or more other Vertexs (sic), and it is pointed to by at
 * least one other Vertex, unless it is the RootVertex. The VertexType tells one
 * whether the the Vertexs pointed to should be taken together or are alternatives.
 * A Vertex may have an associated RepresentationInformationObject.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:47
 */
public interface Vertex  {


	/**
	 * The VertexType is RepInfoGroup or one of its sub-types. If it is an OrGroup
	 * then any of the available Edges may be followed, but if it is an AndGroup then
	 * all the associated Edges must be followed.
	 */
	public RepInfoGroup m_vertexType = null;


	/**
	 * Returns an ArrayList of Edges which end at this Vertex
	 * @return inwardEdges The ArrayList of Edges which end at this Vertex
	 */
	public ArrayList<DirectedEdge> getInwardEdges();

	/**
	 * Returns an ArrayList of Edges which start at this Vertex
	 * @return outwardEdges The ArrayList of Edges which start at this vertex.
	 */
	public ArrayList<DirectedEdge> getOutwardEdges();

//	/**
//	 * Return the RepInfoId for the Vertex - or null.
//	 *
//	 * @return The RepInfoId for the Vertex - or null.
//	 */
//	public RepInfoId getRepInfoId();
//
//	/**
//	 * Set the RepInfoId for the Vertex.
//	 *
//	 * @param riid The RepInfoId for the Vertex
//	 */
//	public void setRepInfoId(RepInfoId riid);

	/**
	 * Get the VertexType - a subtype of RepInfoGroup.
	 *
	 * @return The VertexType - a subtype of RepInfoGroup.
	 */
	public RepInfoGroup getVertexType();

	/**
	 * Set the VertexType - a subtype of RepInfoGroup..
	 *
	 * @param rig The VertexType - a subtype of RepInfoGroup.
	 */
	public void setVertexType(RepInfoGroup rig);

}