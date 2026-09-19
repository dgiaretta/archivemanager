package info.oais.archive.manager.service;

import info.oais.archive.manager.i18n.LanguagePreference;
import info.oais.archive.manager.model.ClassOption;
import info.oais.archive.manager.model.PropertyOption;
import info.oais.archive.manager.rdf.LabelPicker;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the entity editor needs to know about "what classes and
 * properties exist" comes from here, and is read live from the ontology
 * graph rather than hard-coded -- so it covers every RiC-O class (from the
 * bundled {@code rico-vocabulary.ttl} stub, all 107, or the real RiC-O
 * ontology if one has been loaded -- see README), every OAIS class (from
 * the full {@code oais_im_schema-sh-v5.ttl}), the 15 Dublin Core elements
 * used by NAM's Bulk Upload Spreadsheet ({@code dc-vocabulary.ttl}), and
 * NAM's own small extension vocabulary ({@code nam-vocabulary.ttl}), plus
 * the curated property suggestions bundled alongside RiC-O/OAIS. Free-text
 * entry (see {@link #resolveIri}) covers anything not in those lists.
 *
 * <p>Labels are resolved with {@link LanguagePreference}/{@link LabelPicker}:
 * a class or property with labels in several languages (the real RiC-O
 * ontology labels everything in English, French, and Spanish, classes also
 * in German) shows one label, in the user's preferred language where
 * available, rather than one dropdown entry per language.
 */
@Service
public class OntologyService {

    private final RdfStore store;
    private final QueryRunner q;
    private final LanguagePreference languagePreference;

    public OntologyService(RdfStore store, QueryRunner q, LanguagePreference languagePreference) {
        this.store = store;
        this.q = q;
        this.languagePreference = languagePreference;
    }

    public List<ClassOption> listClasses() {
        String preferredLang = languagePreference.current();
        String sparql = Ns.PREFIXES + """
                SELECT ?c ?label ?labelLang WHERE {
                  ?c a owl:Class .
                  FILTER%s
                  OPTIONAL { ?c rdfs:label ?label . BIND(LANG(?label) AS ?labelLang) }
                }
                """.formatted(namespaceFilter("?c"));

        Map<String, List<LabelPicker.Candidate>> candidatesByIri = new LinkedHashMap<>();
        for (Map<String, String> row : q.select(store.ontologyModel(), sparql)) {
            String iri = row.get("c");
            candidatesByIri.computeIfAbsent(iri, k -> new ArrayList<>())
                    .add(new LabelPicker.Candidate(row.get("label"), row.get("labelLang")));
        }

        List<ClassOption> out = new ArrayList<>();
        for (Map.Entry<String, List<LabelPicker.Candidate>> entry : candidatesByIri.entrySet()) {
            String iri = entry.getKey();
            String local = q.localName(iri);
            String label = LabelPicker.pick(entry.getValue(), preferredLang, local);
            out.add(new ClassOption(iri, local, label, sourceOf(iri)));
        }
        out.sort((a, b) -> {
            int c = a.source().compareTo(b.source());
            return c != 0 ? c : a.label().compareToIgnoreCase(b.label());
        });
        return out;
    }

    public List<PropertyOption> listObjectProperties() {
        List<PropertyOption> out = new ArrayList<>(listProperties("owl:ObjectProperty"));
        // rdfs:seeAlso is the canonical external-link property used for URI-valued
        // references. It isn't declared in the app's loaded ontology fragments, so it
        // is added explicitly here as a known object property rather than left to free-text
        // entry only. The generic resource view already renders these as clickable links.
        out.add(new PropertyOption(Ns.RDFS + "seeAlso", "seeAlso", "See also", "rdfs"));
        out.sort((a, b) -> {
            int c = a.source().compareTo(b.source());
            return c != 0 ? c : a.label().compareToIgnoreCase(b.label());
        });
        return out;
    }

    public List<PropertyOption> listDatatypeProperties() {
        List<PropertyOption> out = new ArrayList<>(listProperties("owl:DatatypeProperty"));
        // rdfs:label / rdfs:comment aren't rico:/im:/dc:/nam: properties, so the
        // namespace-filtered query above never finds them -- but they're exactly what
        // this app's own sample data uses to attach descriptive text to OAIS
        // individuals (which otherwise have no datatype properties of their own; see
        // the note on the "Properties" panel), so they're added here as standing
        // suggestions rather than left to free-text entry only.
        out.add(new PropertyOption(Ns.RDFS + "label", "label", "Label", "rdfs"));
        out.add(new PropertyOption(Ns.RDFS + "comment", "comment", "Comment", "rdfs"));
        return out;
    }

    private List<PropertyOption> listProperties(String owlPropertyType) {
        String preferredLang = languagePreference.current();
        String sparql = Ns.PREFIXES + """
                SELECT ?p ?label ?labelLang WHERE {
                  ?p a %s .
                  FILTER%s
                  OPTIONAL { ?p rdfs:label ?label . BIND(LANG(?label) AS ?labelLang) }
                }
                """.formatted(owlPropertyType, namespaceFilter("?p"));

        Map<String, List<LabelPicker.Candidate>> candidatesByIri = new LinkedHashMap<>();
        for (Map<String, String> row : q.select(store.ontologyModel(), sparql)) {
            String iri = row.get("p");
            candidatesByIri.computeIfAbsent(iri, k -> new ArrayList<>())
                    .add(new LabelPicker.Candidate(row.get("label"), row.get("labelLang")));
        }

        List<PropertyOption> out = new ArrayList<>();
        for (Map.Entry<String, List<LabelPicker.Candidate>> entry : candidatesByIri.entrySet()) {
            String iri = entry.getKey();
            String local = q.localName(iri);
            String label = LabelPicker.pick(entry.getValue(), preferredLang, local);
            out.add(new PropertyOption(iri, local, label, sourceOf(iri)));
        }
        out.sort((a, b) -> {
            int c = a.source().compareTo(b.source());
            return c != 0 ? c : a.label().compareToIgnoreCase(b.label());
        });
        return out;
    }

    /** SPARQL FILTER clause text matching any of the recognized namespaces this app treats as "known". */
    private String namespaceFilter(String variable) {
        return "(STRSTARTS(STR(" + variable + "), \"" + Ns.RICO + "\") "
                + "|| STRSTARTS(STR(" + variable + "), \"" + Ns.IM + "\") "
                + "|| STRSTARTS(STR(" + variable + "), \"" + Ns.BRIDGE + "\") "
                + "|| STRSTARTS(STR(" + variable + "), \"" + Ns.DC + "\") "
                + "|| STRSTARTS(STR(" + variable + "), \"" + Ns.NAM + "\"))";
    }

    /** "rico", "oais", "bridge", "dc", "nam", or "other", by namespace -- used to group picker dropdowns into optgroups. */
    public String sourceOf(String iri) {
        if (iri.startsWith(Ns.RICO)) {
            return "rico";
        }
        if (iri.startsWith(Ns.IM)) {
            return "oais";
        }
        if (iri.startsWith(Ns.BRIDGE)) {
            return "bridge";
        }
        if (iri.startsWith(Ns.DC)) {
            return "dc";
        }
        if (iri.startsWith(Ns.NAM)) {
            return "nam";
        }
        return "other";
    }

    /**
     * Every class in {@code superclassIri}'s subclass closure -- itself plus every
     * class transitively reachable via {@code rdfs:subClassOf} -- as full IRIs. This is
     * how "list all Agents" / "list all Record Resources" / etc. stay ontology-driven:
     * the set of qualifying classes comes from the {@code rdfs:subClassOf} hierarchy
     * declared in the ontology graph (see {@code rico-vocabulary.ttl}), not a hard-coded
     * Java list of leaf class names.
     */
    public List<String> subclassClosure(String superclassIri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?c WHERE {
                  ?c rdfs:subClassOf* <%s> .
                }
                """.formatted(superclassIri);
        List<String> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.ontologyModel(), sparql)) {
            out.add(row.get("c"));
        }
        return out;
    }

    /** Like {@link #subclassClosure(String)} but returns local names, for building a fast membership set. */
    public Set<String> subclassLocalNames(String superclassIri) {
        Set<String> out = new HashSet<>();
        for (String iri : subclassClosure(superclassIri)) {
            out.add(q.localName(iri));
        }
        return out;
    }

    /**
     * Resolves free-text class/property/resource input into a full IRI.
     * Accepts, in order: an absolute IRI ({@code http://...}), a prefixed
     * name ({@code rico:hasCreator}, {@code im:InformationObject}, {@code dc:title},
     * {@code nam:recordNumber}, {@code bridge:...}, {@code ex:...}), or a bare
     * local name, which defaults to the {@code rico:} namespace (the common
     * case for a RiC-O-centric archive). Returns {@code null} for blank input.
     */
    public String resolveIri(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String prefix = trimmed.substring(0, colon);
            String local = trimmed.substring(colon + 1);
            return switch (prefix) {
                case "rico" -> Ns.RICO + local;
                case "im" -> Ns.IM + local;
                case "dc" -> Ns.DC + local;
                case "nam" -> Ns.NAM + local;
                case "bridge" -> Ns.BRIDGE + local;
                case "ex" -> Ns.EX + local;
                case "skos" -> Ns.SKOS + local;
                case "rdfs" -> Ns.RDFS + local;
                default -> trimmed; // not a namespace we know; treat the whole thing as an IRI-ish literal
            };
        }
        return Ns.RICO + trimmed;
    }
}
