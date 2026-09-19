package info.oais.archive.manager.model;

/**
 * One property the entity editor offers as a suggestion, sourced from the
 * ontology graph ({@code owl:ObjectProperty} / {@code owl:DatatypeProperty}
 * declarations in the {@code rico:} or {@code im:} namespace). The editor
 * always also accepts free-text property names, so this is a convenience
 * list, not a hard constraint.
 *
 * @param source "rico" or "oais"
 */
public record PropertyOption(String iri, String localName, String label, String source) {
}
