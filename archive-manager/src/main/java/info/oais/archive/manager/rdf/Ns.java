package info.oais.archive.manager.rdf;

/**
 * Namespace constants for the vocabularies this application speaks: RiC-O,
 * the OAIS Information Model, the bridging ontology that connects them,
 * Dublin Core (the National Archives of Maldives' "Bulk Upload Spreadsheet"
 * columns are DC's 15 elements plus preservation/accession extensions), a
 * small NAM-specific extension vocabulary for the handful of fields that
 * have no natural home in any of the above, and the example-data namespace.
 */
public final class Ns {

    private Ns() {
    }

    public static final String RICO = "https://www.ica.org/standards/RiC/ontology#";
    public static final String IM = "http://ontology.oais.org/im/";
    public static final String BRIDGE = "https://oais.info/bridge#";
    public static final String EX = "http://example.org/archive/";
    public static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
    public static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    /** The real, standard Dublin Core Elements 1.1 namespace -- not a project-specific invention. */
    public static final String DC = "http://purl.org/dc/elements/1.1/";
    /**
     * A small extension vocabulary for NAM/Eternal-specific fields with no home in
     * RiC-O, OAIS, or Dublin Core (e.g. the Eternal-assigned UUID, the "was this
     * transferred to NAM or created by NAM" flag). Placeholder namespace, same as
     * {@link #BRIDGE} originally was -- replace with NAM's own domain before any
     * real production use; see the note at the top of {@code nam-vocabulary.ttl}.
     */
    public static final String NAM = "https://oais.info/nam#";

    /** SPARQL PREFIX declarations shared by every query in the service layer. */
    public static final String PREFIXES = """
            PREFIX rico:   <%s>
            PREFIX im:     <%s>
            PREFIX bridge: <%s>
            PREFIX ex:     <%s>
            PREFIX skos:   <%s>
            PREFIX rdfs:   <%s>
            PREFIX dc:     <%s>
            PREFIX nam:    <%s>
            PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX owl:    <http://www.w3.org/2002/07/owl#>
            PREFIX xsd:    <http://www.w3.org/2001/XMLSchema#>
            """.formatted(RICO, IM, BRIDGE, EX, SKOS, RDFS, DC, NAM);

    /**
     * Escapes a string for safe embedding inside a SPARQL string literal
     * (i.e. between the quotes in {@code "..."} within a query built via
     * plain string interpolation, which is how every query in this app is
     * built -- there's no parameterized-query API in play). Backslash first,
     * then quote, then the two newline forms; anything embedding
     * user-supplied text into a query -- most importantly the public,
     * anonymous-access catalogue/accession-register search boxes -- must
     * run it through this first, or a search term containing a literal
     * {@code "} could break out of the intended string literal and inject
     * arbitrary additional SPARQL into the query.
     */
    public static String escapeSparqlLiteral(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
