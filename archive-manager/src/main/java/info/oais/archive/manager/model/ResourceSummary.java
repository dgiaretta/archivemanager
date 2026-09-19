package info.oais.archive.manager.model;

/** One row in a resource listing: its IRI, a short display id, its type's local name, and a title. */
public record ResourceSummary(String iri, String id, String typeLocalName, String title) {
}
