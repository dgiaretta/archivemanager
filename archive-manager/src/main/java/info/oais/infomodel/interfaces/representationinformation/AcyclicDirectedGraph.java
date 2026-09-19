/**
 *
 */
package info.oais.infomodel.interfaces.representationinformation;

/**
 * This interface provides methods which can be used to navigate a RepInfoNetwork.
 *
 * The acyclic directed graph has one root vertex.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:45
 */
public interface AcyclicDirectedGraph  {

	/**
	 * Get the RootVertex of the graph.
	 *
	 * @return The RootVertex of the graph.
	 */
	public RootVertex getRootVertex();

	/**
	 * Set the RootVertex of the graph.
	 *
	 * @param rv The RootVertex of the graph.
	 */
	public void setRootVertex(RootVertex rv);

}