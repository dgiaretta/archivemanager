package info.oais.archive.manager.model;

/**
 * One edge in the interactive relationship graph, i.e. one RDF triple whose
 * object is a resource (literal-valued triples aren't graph edges).
 *
 * @param crossOntology true if the two endpoints belong to different source
 *                      ontologies (RiC-O vs. OAIS) -- these are exactly the
 *                      edges the bridge makes possible (bridge:hasOAISCounterpart
 *                      / bridge:hasRiCDescription), so they're drawn
 *                      differently to make the bridge visible in the graph.
 */
public record GraphEdge(String from, String to, String label, boolean crossOntology) {
}
