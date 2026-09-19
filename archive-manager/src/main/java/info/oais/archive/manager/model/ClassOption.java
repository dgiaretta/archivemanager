package info.oais.archive.manager.model;

/**
 * One class the entity editor lets the user pick from, sourced generically
 * from the ontology graph (any {@code owl:Class} in the {@code rico:} or
 * {@code im:} namespace) rather than a hard-coded list.
 *
 * @param source "rico" or "oais", used to group the picker into optgroups
 */
public record ClassOption(String iri, String localName, String label, String source) {
}
