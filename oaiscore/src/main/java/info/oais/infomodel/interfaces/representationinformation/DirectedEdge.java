package info.oais.infomodel.interfaces.representationinformation;

/**
 * Each edge has exactly one Source Vertex and one Target Vertex.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
public interface DirectedEdge {


	/**
	 * Returns the Vertex from which the Edge starts.
	 * @return sourceVertex The originating end of the Edge i.e. the Edge goes from
	 * the source to the target.
	 */
	public Vertex getSourceVertex();

	/**
	 * Set the source Vertex for this Edge.
	 *
	 * @param ver The source Vertex for this edge.
	 */
	public void setSourceVertex(Vertex ver);

	/**
	 * Returns the Vertex at which the Edge ends.
	 * @return tergetVertex The target end of the Edge i.e. the Edge goes from the
	 * source to the target.
	 */
	public Vertex getTargetVertex();

	/**
	 * Set the target Vertex for this Edge.
	 *
	 * @param ver The target Vertex for this edge.
	 */
	public void setTargetVertex(Vertex ver);

	/**
	 * Returns the type associated with the Edge.
	 * @return String The type of association the Edge represents.
	 */
	public String getType();

	/**
	 * Set the type associated with this Edge.
	 *
	 * @param type The type for this Edge.
	 */
	public void setType(String type);

}