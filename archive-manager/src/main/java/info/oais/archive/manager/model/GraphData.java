package info.oais.archive.manager.model;

import java.util.List;

/**
 * @param truncated true if the graph hit a size cap and is not the complete
 *                   result -- the caller asked for more nodes/edges than this
 *                   view is willing to render in one go.
 * @param propertyNames every property (literal-valued or resource-linking) that a node
 *                     in {@link #nodes()} actually has, used to populate the graph's
 *                     property highlight filter -- scoped to this payload so the filter
 *                     never offers a property that belongs to some unrelated node
 *                     elsewhere in the archive (which would always match nothing on
 *                     screen). The one exception is when {@link #truncated()} is true:
 *                     then this also includes every resource-linking property used
 *                     anywhere in the data store, not just this truncated payload, so
 *                     the filter stays complete even though some of the graph's own
 *                     edges didn't make it into this render.
 */
public record GraphData(List<GraphNode> nodes, List<GraphEdge> edges, boolean truncated, List<String> propertyNames) {
}
