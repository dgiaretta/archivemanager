package info.oais.archive.manager.model;

import java.util.List;

/**
 * One node in the interactive relationship graph.
 *
 * @param id        the resource's full IRI (used as the vis-network node id)
 * @param label     display label
 * @param group     "rico", "oais", or "other" -- drives node colour, and lets
 *                  the graph visually distinguish the two source ontologies
 * @param types     every rdf:type local name this resource has (e.g.
 *                  "Record", "InformationObject") -- carried through to the
 *                  client so the graph's entity-type highlight control can
 *                  filter by it; not used for node colour (that's {@code group}).
 * @param properties every property local name attached to this resource in the
 *                   loaded graph, so the client can highlight nodes by the
 *                   properties they expose as well as by type and edge label.
 * @param title     tooltip text (IRI + type)
 * @param detailUrl where double-clicking the node should navigate to
 * @param links     literal properties whose value is itself a URL (e.g. an external
 *                  {@code rdfs:seeAlso}) -- offered in the client's right-click menu
 *                  as things to open directly, distinct from {@code detailUrl}.
 * @param viewers   for a Data Object whose Representation Information network leads to what
 *                  an application needs to show its data: those applications (TOPCAT, SPLAT,
 *                  ...), also offered on the right-click menu. Empty otherwise.
 */
public record GraphNode(String id, String label, String group, List<String> types, List<String> properties, String title,
                        String detailUrl, List<GraphLink> links, List<GraphViewer> viewers) {

    public GraphNode(String id, String label, String group, List<String> types, List<String> properties, String title,
                     String detailUrl, List<GraphLink> links) {
        this(id, label, group, types, properties, title, detailUrl, links, List.of());
    }
}
