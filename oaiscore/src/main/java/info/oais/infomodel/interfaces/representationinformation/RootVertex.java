package info.oais.infomodel.interfaces.representationinformation;

/**
 * The unique root Vertex of the acyclic directed graph.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:47
 */
public interface RootVertex extends Vertex {

	/**
	 * This is a method which can be used to check whether or not the graph is acyclic.
	 * For example if a Vertex or an Edge is added this can be used to check if this
	 * would cause the graph to contain a cycle i.e. FAIL to be acyclic, in which case
	 * the added component would be removed.
	 *
	 * @return Boolean which is true if the Graph is acyclic.
	 */
	public boolean isAcyclic();

}