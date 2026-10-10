package info.oais.archive.manager.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import info.oais.archive.manager.i18n.LanguagePreference;
import info.oais.archive.manager.model.BridgeMapping;
import info.oais.archive.manager.model.ClassOption;
import info.oais.archive.manager.model.EditableProperty;
import info.oais.archive.manager.model.EditableRelationship;
import info.oais.archive.manager.model.FacetCount;
import info.oais.archive.manager.model.LinkedResource;
import info.oais.archive.manager.model.OaisFlatNode;
import info.oais.archive.manager.model.OaisNode;
import info.oais.archive.manager.model.Page;
import info.oais.archive.manager.model.PropertyValue;
import info.oais.archive.manager.model.ResourceSummary;
import info.oais.archive.manager.model.TypeOption;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.LabelPicker;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ArchiveService {

    private final RdfStore store;
    private final QueryRunner q;
    private final LanguagePreference languagePreference;

    public ArchiveService(RdfStore store, QueryRunner q, LanguagePreference languagePreference) {
        this.store = store;
        this.q = q;
        this.languagePreference = languagePreference;
    }

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    // Default and maximum page size, shared by every paginated listing.
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    // The "WHERE { ... }" body for each top-level listing, shared between the
    // capped/unpaginated convenience methods (used to populate dropdowns) and
    // the paginated ones (used by the actual browse pages) so the two never
    // drift out of sync with each other.
    private static final String RECORD_RESOURCE_BODY = """
            ?type rdfs:subClassOf* rico:RecordResource .
            ?s a ?type .
            OPTIONAL { ?s rico:title ?t1 }
            OPTIONAL { ?s rico:name ?t2 }
            BIND(COALESCE(?t1, ?t2, STR(?s)) AS ?title)
            """;
    private static final String AGENT_BODY = """
            ?type rdfs:subClassOf* rico:Agent .
            ?s a ?type .
            OPTIONAL { ?s rico:name ?t1 }
            BIND(COALESCE(?t1, STR(?s)) AS ?title)
            """;
    private static final String EVENT_BODY = """
            ?type rdfs:subClassOf* rico:Event .
            ?s a ?type .
            OPTIONAL { ?s rico:generalDescription ?t1 }
            BIND(COALESCE(?t1, STR(?s)) AS ?title)
            """;
    private static final String MANDATE_BODY = """
            ?type rdfs:subClassOf* rico:Mandate .
            ?s a ?type .
            OPTIONAL { ?s rico:name ?t1 }
            BIND(COALESCE(?t1, STR(?s)) AS ?title)
            """;

    /** First {@code MAX_PAGE_SIZE} record resources, for dropdowns (e.g. the "parent" picker) that just need "some". */
    public List<ResourceSummary> listRecordResources() {
        return toSummaries(Ns.PREFIXES + "SELECT ?s ?type ?title WHERE { " + RECORD_RESOURCE_BODY
                + " } ORDER BY ?type ?title LIMIT " + MAX_PAGE_SIZE);
    }

    /** The actual, paginated Records browse page. */
    public Page<ResourceSummary> listRecordResources(int page, int pageSize) {
        return pagedSummaries(RECORD_RESOURCE_BODY, "ORDER BY ?type ?title", page, pageSize);
    }

    public long countRecordResources() {
        return countMatches(RECORD_RESOURCE_BODY);
    }

    /**
     * The public NAM Catalogue search: paginated Record/RecordPart/RecordSet
     * results, optionally narrowed by a free-text keyword matched
     * case-insensitively against the title, description, and Dublin Core
     * subject. Anonymous and open, same as the rest of browsing -- the
     * keyword is escaped via {@link Ns#escapeSparqlLiteral} before being
     * embedded in the query, since this is the one search box in the app
     * exposed directly to untrusted, anonymous input.
     */
    public Page<ResourceSummary> searchCatalogue(String keyword, int page, int pageSize) {
        String body = RECORD_RESOURCE_BODY;
        if (keyword != null && !keyword.isBlank()) {
            String escaped = Ns.escapeSparqlLiteral(keyword.trim());
            body = body + """
                    FILTER(
                      CONTAINS(LCASE(STR(?title)), LCASE("%1$s"))
                      || EXISTS { ?s rico:generalDescription ?anyDesc . FILTER(CONTAINS(LCASE(STR(?anyDesc)), LCASE("%1$s"))) }
                      || EXISTS { ?s dc:subject ?anySubject . FILTER(CONTAINS(LCASE(STR(?anySubject)), LCASE("%1$s"))) }
                      || EXISTS { ?s dc:type ?anyType . FILTER(CONTAINS(LCASE(STR(?anyType)), LCASE("%1$s"))) }
                      || EXISTS { ?s nam:recordNumber ?anyRecordNo . FILTER(CONTAINS(LCASE(STR(?anyRecordNo)), LCASE("%1$s"))) }
                      || EXISTS { ?s rico:hasOrHadIdentifier ?anyIdRes . ?anyIdRes rico:textualValue ?anyId . FILTER(CONTAINS(LCASE(STR(?anyId)), LCASE("%1$s"))) }
                    )
                    """.formatted(escaped);
        }
        return pagedSummaries(body, "ORDER BY ?type ?title", page, pageSize);
    }

    /**
     * The "ask an interesting question without knowing SPARQL" advanced
     * search: any combination of these facets, all optional and
     * AND-combined, translated into a SPARQL query behind the scenes. Every
     * value is escaped via {@link Ns#escapeSparqlLiteral} before being
     * embedded -- same reasoning as {@link #searchCatalogue}, this is
     * public, anonymous-access input.
     */
    public Page<ResourceSummary> advancedSearch(String creator, String type, String language, String subject,
                                                 String coverage, String rights, String dateFrom, String dateTo,
                                                 String transferredRecord, int page, int pageSize) {
        StringBuilder body = new StringBuilder(RECORD_RESOURCE_BODY);
        if (notBlank(creator)) {
            String v = Ns.escapeSparqlLiteral(creator.trim());
            body.append("FILTER EXISTS { ?s rico:hasCreator ?_creator . ?_creator rico:name ?_creatorName . ")
                    .append("FILTER(CONTAINS(LCASE(STR(?_creatorName)), LCASE(\"").append(v).append("\"))) }\n");
        }
        appendContainsFilter(body, "dc:type", "?_type", type);
        appendContainsFilter(body, "dc:language", "?_lang", language);
        appendContainsFilter(body, "dc:subject", "?_subj", subject);
        appendContainsFilter(body, "dc:coverage", "?_cov", coverage);
        appendContainsFilter(body, "dc:rights", "?_rights", rights);
        if (notBlank(transferredRecord)) {
            String v = Ns.escapeSparqlLiteral(transferredRecord.trim());
            body.append("FILTER EXISTS { ?s nam:transferredRecord ?_tr . FILTER(LCASE(STR(?_tr)) = LCASE(\"").append(v).append("\")) }\n");
        }
        if (notBlank(dateFrom) || notBlank(dateTo)) {
            // Dates are stored as plain literals (dc:date), not typed xsd:date, since the
            // Bulk Upload Spreadsheet doesn't guarantee a single format -- but string
            // comparison on YYYY-MM-DD-formatted text happens to sort chronologically too,
            // so a plain >=/<= works correctly as long as the data actually uses that
            // format, which is what the spreadsheet's own documentation specifies.
            body.append("FILTER EXISTS { ?s dc:date ?_date . ");
            if (notBlank(dateFrom)) {
                body.append("FILTER(STR(?_date) >= \"").append(Ns.escapeSparqlLiteral(dateFrom.trim())).append("\") ");
            }
            if (notBlank(dateTo)) {
                body.append("FILTER(STR(?_date) <= \"").append(Ns.escapeSparqlLiteral(dateTo.trim())).append("\") ");
            }
            body.append("}\n");
        }
        return pagedSummaries(body.toString(), "ORDER BY ?type ?title", page, pageSize);
    }

    private void appendContainsFilter(StringBuilder body, String property, String var, String value) {
        if (notBlank(value)) {
            String v = Ns.escapeSparqlLiteral(value.trim());
            body.append("FILTER EXISTS { ?s ").append(property).append(" ").append(var).append(" . ")
                    .append("FILTER(CONTAINS(LCASE(STR(").append(var).append(")), LCASE(\"").append(v).append("\"))) }\n");
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** "How many catalogue records does each creator have?", most first. One of the pre-built "interesting question" reports. */
    public List<FacetCount> recordsByCreator() {
        String sparql = Ns.PREFIXES + """
                SELECT ?name (COUNT(DISTINCT ?s) AS ?count) WHERE {
                  ?s a ?type . ?type rdfs:subClassOf* rico:RecordResource .
                  ?s rico:hasCreator ?creator .
                  ?creator rico:name ?name .
                } GROUP BY ?name ORDER BY DESC(?count) LIMIT 25
                """;
        return facetCounts(sparql, "name");
    }

    /** "How many catalogue records of each type?" */
    public List<FacetCount> recordsByType() {
        String sparql = Ns.PREFIXES + """
                SELECT ?value (COUNT(DISTINCT ?s) AS ?count) WHERE {
                  ?s a ?type . ?type rdfs:subClassOf* rico:RecordResource .
                  ?s dc:type ?value .
                } GROUP BY ?value ORDER BY DESC(?count) LIMIT 25
                """;
        return facetCounts(sparql, "value");
    }

    /** "How many catalogue records in each language?" */
    public List<FacetCount> recordsByLanguage() {
        String sparql = Ns.PREFIXES + """
                SELECT ?value (COUNT(DISTINCT ?s) AS ?count) WHERE {
                  ?s a ?type . ?type rdfs:subClassOf* rico:RecordResource .
                  ?s dc:language ?value .
                } GROUP BY ?value ORDER BY DESC(?count) LIMIT 25
                """;
        return facetCounts(sparql, "value");
    }

    /**
     * "How many catalogue records per year?" Approximate by design: takes the
     * first 4 characters of dc:date, so it's only meaningful if dates are
     * consistently YYYY-... formatted (what the spreadsheet documentation
     * specifies, not something this app can enforce on upload).
     */
    public List<FacetCount> recordsByYear() {
        String sparql = Ns.PREFIXES + """
                SELECT ?value (COUNT(DISTINCT ?s) AS ?count) WHERE {
                  ?s a ?type . ?type rdfs:subClassOf* rico:RecordResource .
                  ?s dc:date ?date .
                  BIND(SUBSTR(STR(?date), 1, 4) AS ?value)
                  FILTER(REGEX(?value, "^[0-9]{4}$"))
                } GROUP BY ?value ORDER BY ?value LIMIT 100
                """;
        return facetCounts(sparql, "value");
    }

    private List<FacetCount> facetCounts(String sparql, String labelVar) {
        List<FacetCount> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            String label = row.get(labelVar);
            String countStr = row.get("count");
            long count = countStr == null ? 0 : Long.parseLong(countStr);
            out.add(new FacetCount(label == null ? "(none)" : label, count));
        }
        return out;
    }

    private static final String ACCESSION_BODY = """
            ?s a ?type .
            FILTER(?type = nam:Accession)
            OPTIONAL { ?s rico:generalDescription ?t1 }
            BIND(COALESCE(?t1, STR(?s)) AS ?title)
            """;

    /** The public NAM Accession Register: paginated list of nam:Accession individuals. */
    public Page<ResourceSummary> listAccessions(int page, int pageSize) {
        return pagedSummaries(ACCESSION_BODY, "ORDER BY ?title", page, pageSize);
    }

    public long countAccessions() {
        return countMatches(ACCESSION_BODY);
    }

    /** First {@code MAX_PAGE_SIZE} agents, for dropdowns (e.g. the "creator" picker). */
    public List<ResourceSummary> listAgents() {
        return toSummaries(Ns.PREFIXES + "SELECT ?s ?type ?title WHERE { " + AGENT_BODY
                + " } ORDER BY ?title LIMIT " + MAX_PAGE_SIZE);
    }

    public Page<ResourceSummary> listAgents(int page, int pageSize) {
        return pagedSummaries(AGENT_BODY, "ORDER BY ?title", page, pageSize);
    }

    public long countAgents() {
        return countMatches(AGENT_BODY);
    }

    public Page<ResourceSummary> listActivities(int page, int pageSize) {
        return pagedSummaries(EVENT_BODY, "ORDER BY ?title", page, pageSize);
    }

    public long countActivities() {
        return countMatches(EVENT_BODY);
    }

    public Page<ResourceSummary> listMandates(int page, int pageSize) {
        return pagedSummaries(MANDATE_BODY, "ORDER BY ?title", page, pageSize);
    }

    public long countMandates() {
        return countMatches(MANDATE_BODY);
    }

    private ResourceSummary rowToSummary(Map<String, String> row) {
        String iri = row.get("s");
        return new ResourceSummary(iri, encodeId(iri), q.localName(row.get("type")), row.get("title"));
    }

    private List<ResourceSummary> toSummaries(String sparql) {
        List<ResourceSummary> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(rowToSummary(row));
        }
        return out;
    }

    /**
     * Runs {@code whereAndBind} (a SPARQL "WHERE {...}" body binding ?s, ?type, and
     * ?title) as one LIMIT/OFFSET page plus a companion COUNT query for the total,
     * so every browse page can show "page X of Y" / Previous / Next instead of
     * rendering however many thousands of rows a large archive might have.
     */
    private Page<ResourceSummary> pagedSummaries(String whereAndBind, String orderBy, int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        int offset = (p - 1) * size;

        String selectSparql = Ns.PREFIXES + "SELECT ?s ?type ?title WHERE { " + whereAndBind + " } "
                + orderBy + " LIMIT " + size + " OFFSET " + offset;
        List<ResourceSummary> items = toSummaries(selectSparql);

        long total = countMatches(whereAndBind);
        return new Page<>(items, p, size, total);
    }

    private long countMatches(String whereAndBind) {
        String sparql = Ns.PREFIXES + "SELECT (COUNT(DISTINCT ?s) AS ?count) WHERE { " + whereAndBind + " }";
        List<Map<String, String>> rows = q.select(store.queryModel(), sparql);
        if (rows.isEmpty() || rows.get(0).get("count") == null) {
            return 0;
        }
        return Long.parseLong(rows.get(0).get("count"));
    }

    /**
     * Every property actually in use in the data graph, with how many
     * triples use it and how many distinct subjects have it set at least
     * once. The two counts diverge for repeatable fields (e.g. dc:subject,
     * which a single record can have several of), so both are shown rather
     * than collapsing to one number that would misrepresent one case or
     * the other. Excludes rdf:type (covered separately by
     * {@link #typesInUse}) and properties with zero use.
     */
    public List<FacetCount> propertyUsageCounts() {
        String sparql = Ns.PREFIXES + """
                SELECT ?p (COUNT(*) AS ?tripleCount) (COUNT(DISTINCT ?s) AS ?subjectCount) WHERE {
                  ?s ?p ?o .
                  FILTER(?p != rdf:type)
                } GROUP BY ?p ORDER BY DESC(?tripleCount)
                """;
        List<FacetCount> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.dataModel(), sparql)) {
            long triples = Long.parseLong(row.get("tripleCount"));
            long subjects = Long.parseLong(row.get("subjectCount"));
            String label = displayName(row.get("p")) + (triples == subjects ? "" : " (on " + subjects + " distinct records)");
            out.add(new FacetCount(label, triples));
        }
        return out;
    }

    /** "prefix:localName" for display, e.g. "rico:hasCreator", "im:hasDataObject", "dc:title" -- not the bare local name or full IRI. */
    private String displayName(String iri) {
        String prefix;
        if (iri.startsWith(Ns.RICO)) {
            prefix = "rico:";
        } else if (iri.startsWith(Ns.IM)) {
            prefix = "im:";
        } else if (iri.startsWith(Ns.DC)) {
            prefix = "dc:";
        } else if (iri.startsWith(Ns.NAM)) {
            prefix = "nam:";
        } else if (iri.startsWith(Ns.RDFS)) {
            prefix = "rdfs:";
        } else {
            prefix = "";
        }
        return prefix + q.localName(iri);
    }

    public Map<String, Long> counts() {
        return Map.of(
                "records", countRecordResources(),
                "agents", countAgents(),
                "activities", countActivities(),
                "mandates", countMandates(),
                "accessions", countAccessions(),
                "triples", store.dataModel().size());
    }

    // ------------------------------------------------------------------
    // Generic helpers
    // ------------------------------------------------------------------

    /** Best-effort human-readable label: rico:title, then rico:name, then rdfs:label, then the local name. */
    public String label(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?t1 ?t2 ?t3 ?t3Lang WHERE {
                  OPTIONAL { <%s> rico:title ?t1 }
                  OPTIONAL { <%s> rico:name ?t2 }
                  OPTIONAL { <%s> rdfs:label ?t3 . BIND(LANG(?t3) AS ?t3Lang) }
                }
                """.formatted(iri, iri, iri);
        List<Map<String, String>> rows = q.select(store.queryModel(), sparql);
        // rico:title/rico:name are effectively single-valued in this app's data model, so the
        // first row with either is fine; rdfs:label is the one that can legitimately have several
        // language-tagged values (e.g. a class from the real, multilingual RiC-O ontology), so
        // that one specifically goes through LabelPicker rather than an arbitrary "first row".
        String t1 = firstNonNull(rows, "t1");
        if (t1 != null) {
            return t1;
        }
        String t2 = firstNonNull(rows, "t2");
        if (t2 != null) {
            return t2;
        }
        List<LabelPicker.Candidate> t3Candidates = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if (row.get("t3") != null) {
                t3Candidates.add(new LabelPicker.Candidate(row.get("t3"), row.get("t3Lang")));
            }
        }
        if (!t3Candidates.isEmpty()) {
            return LabelPicker.pick(t3Candidates, languagePreference.current(), q.localName(iri));
        }
        return q.localName(iri);
    }

    private String firstNonNull(List<Map<String, String>> rows, String key) {
        for (Map<String, String> row : rows) {
            String v = row.get(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * {@code iri}'s types and properties, each value exactly as stored, or
     * only those of {@code property} if given; empty if there's nothing about it.
     */
    public java.util.Optional<info.oais.archive.manager.model.api.EntityProperties> properties(String iri,
                                                                                            String property) {
        org.apache.jena.rdf.model.Model m = store.queryModel();
        org.apache.jena.rdf.model.Resource r = m.getResource(iri);
        if (!m.listStatements(r, null, (org.apache.jena.rdf.model.RDFNode) null).hasNext()) {
            return java.util.Optional.empty();
        }
        List<String> types = new ArrayList<>();
        List<info.oais.archive.manager.model.api.EntityProperties.Property> properties = new ArrayList<>();
        for (org.apache.jena.rdf.model.Statement s : r.listProperties().toList()) {
            String p = s.getPredicate().getURI();
            org.apache.jena.rdf.model.RDFNode o = s.getObject();
            if (p.equals(org.apache.jena.vocabulary.RDF.type.getURI()) && o.isURIResource()) {
                types.add(o.asResource().getURI());
                continue;
            }
            if (property != null && !property.equals(p)) {
                continue;
            }
            if (o.isLiteral()) {
                org.apache.jena.rdf.model.Literal l = o.asLiteral();
                properties.add(new info.oais.archive.manager.model.api.EntityProperties.Property(p,
                        l.getLexicalForm(), false, null, l.getDatatypeURI(),
                        l.getLanguage().isEmpty() ? null : l.getLanguage()));
            } else if (o.isURIResource()) {
                String target = o.asResource().getURI();
                properties.add(new info.oais.archive.manager.model.api.EntityProperties.Property(p, target, true,
                        encodeId(target), null, null));
            }
        }
        properties.sort(java.util.Comparator.comparing(
                info.oais.archive.manager.model.api.EntityProperties.Property::property));
        java.util.Collections.sort(types);
        return java.util.Optional.of(new info.oais.archive.manager.model.api.EntityProperties(iri, encodeId(iri),
                label(iri), types, properties));
    }

    public List<String> types(String iri) {
        String sparql = Ns.PREFIXES + "SELECT ?t WHERE { <%s> a ?t } ".formatted(iri);
        List<String> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(q.localName(row.get("t")));
        }
        return out;
    }

    /** Like {@link #types(String)} but keeps the full IRI and resolves a label, for the type-management UI. */
    public List<ClassOption> typesAsOptions(String iri) {
        String sparql = Ns.PREFIXES + "SELECT ?t WHERE { <%s> a ?t }".formatted(iri);
        List<ClassOption> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            String t = row.get("t");
            String source = t.startsWith(Ns.RICO) ? "rico" : t.startsWith(Ns.IM) ? "oais" : "other";
            out.add(new ClassOption(t, q.localName(t), label(t), source));
        }
        return out;
    }

    /** Up to {@code MAX_PAGE_SIZE} entities, for pickers (e.g. the relationship-target dropdown) that just need "some". */
    public List<ResourceSummary> listAllEntities() {
        String sparql = Ns.PREFIXES + """
                SELECT DISTINCT ?s WHERE {
                  ?s a ?type .
                } ORDER BY ?s LIMIT %d
                """.formatted(MAX_PAGE_SIZE);
        List<ResourceSummary> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.dataModel(), sparql)) {
            out.add(summarize(row.get("s")));
        }
        out.sort((a, b) -> {
            String ta = a.title() == null ? "" : a.title();
            String tb = b.title() == null ? "" : b.title();
            return ta.compareToIgnoreCase(tb);
        });
        return out;
    }
    /** The actual, paginated Entities browse page (ordered by IRI, so OFFSET-based paging stays stable across pages). */
    /**
     * Every class actually in use in the data graph, with how many
     * individuals each has -- the "how is the archive's data actually
     * shaped" complement to the ontology's static class *list*, which says
     * nothing about usage. Excludes classes with zero individuals rather
     * than listing all ~150+ known classes with mostly-zero counts. Shared
     * by the {@code /entities} type-filter dropdown (needs the IRI to
     * filter by) and the Statistics page (just displays it, formerly via a
     * separate, now-removed method that ran the identical query).
     */
    public List<TypeOption> typesInUse() {
        String sparql = Ns.PREFIXES + """
                SELECT ?type (COUNT(DISTINCT ?s) AS ?count) WHERE {
                  ?s a ?type .
                } GROUP BY ?type ORDER BY DESC(?count)
                """;
        List<TypeOption> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.dataModel(), sparql)) {
            out.add(new TypeOption(row.get("type"), displayName(row.get("type")), Long.parseLong(row.get("count"))));
        }
        return out;
    }

    /** As {@link #listAllEntitiesPaged(int, int)}, optionally narrowed to one class (exact type match, not a subclass closure -- this is a blunt "what IS this thing" filter, not the ontology-driven family lookups elsewhere in this class). {@code typeIri} may be null/blank for no filter. */
    public Page<ResourceSummary> listAllEntitiesPaged(String typeIri, int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        int offset = (p - 1) * size;

        String typeFilter = (typeIri != null && !typeIri.isBlank())
                ? "?s a <" + typeIri + "> ." : "?s a ?anyType .";

        String selectSparql = Ns.PREFIXES + """
                SELECT DISTINCT ?s WHERE {
                  %s
                } ORDER BY ?s LIMIT %d OFFSET %d
                """.formatted(typeFilter, size, offset);
        List<ResourceSummary> items = new ArrayList<>();
        for (Map<String, String> row : q.select(store.dataModel(), selectSparql)) {
            items.add(summarize(row.get("s")));
        }

        String countSparql = Ns.PREFIXES + """
                SELECT (COUNT(DISTINCT ?s) AS ?count) WHERE {
                  %s
                }
                """.formatted(typeFilter);
        List<Map<String, String>> countRows = q.select(store.dataModel(), countSparql);
        long total = (countRows.isEmpty() || countRows.get(0).get("count") == null)
                ? 0 : Long.parseLong(countRows.get(0).get("count"));

        return new Page<>(items, p, size, total);
    }
    public Page<ResourceSummary> listAllEntitiesPaged(int page, int pageSize) {
        return listAllEntitiesPaged(null, page, pageSize);
    }

    public String encodeId(String iri) {
        return info.oais.archive.manager.rdf.IdCodec.encode(iri);
    }

    public String decodeId(String id) {
        return info.oais.archive.manager.rdf.IdCodec.decode(id);
    }

    /** A resource's IRI, short display id, type(s), and title in one shot -- the shape every listing/picker in this app needs. */
    public ResourceSummary summarize(String iri) {
        return new ResourceSummary(iri, encodeId(iri), String.join(", ", types(iri)), label(iri));
    }

    private List<ResourceSummary> resolveResources(String iri, String predicateIri) {
        String sparql = Ns.PREFIXES + "SELECT ?o WHERE { <%s> <%s> ?o }".formatted(iri, predicateIri);
        List<ResourceSummary> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(summarize(row.get("o")));
        }
        return out;
    }

    private List<ResourceSummary> resolveResourcesInverse(String iri, String predicateIri) {
        String sparql = Ns.PREFIXES + "SELECT ?s WHERE { ?s <%s> <%s> }".formatted(predicateIri, iri);
        List<ResourceSummary> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(summarize(row.get("s")));
        }
        return out;
    }

    /** Every literal-valued property found on a resource, for a generic "all attributes" panel. */
    public List<PropertyValue> attributes(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?p ?o WHERE {
                  <%s> ?p ?o .
                  FILTER(isLiteral(?o))
                  FILTER(?p != rico:title && ?p != rico:name && ?p != rico:generalDescription)
                }
                """.formatted(iri);
        List<PropertyValue> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(new PropertyValue(q.localName(row.get("p")), row.get("o"), false, null));
        }
        return out;
    }

    /** Like {@link #attributes(String)} but keeps the full property IRI, for the entity editor's delete forms. */
    public List<EditableProperty> editableLiteralProperties(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?p ?o WHERE {
                  <%s> ?p ?o .
                  FILTER(isLiteral(?o))
                }
                """.formatted(iri);
        List<EditableProperty> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(new EditableProperty(row.get("p"), q.localName(row.get("p")), row.get("o")));
        }
        return out;
    }

    /** Every outgoing resource-valued property on {@code iri}, with the full property IRI, for the entity editor. */
    public List<EditableRelationship> editableOutgoingRelationships(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?p ?o WHERE {
                  <%s> ?p ?o .
                  FILTER(isIRI(?o))
                  FILTER(?p != rdf:type)
                }
                """.formatted(iri);
        List<EditableRelationship> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(new EditableRelationship(row.get("p"), q.localName(row.get("p")), summarize(row.get("o"))));
        }
        return out;
    }

    /** Every incoming resource-valued property pointing at {@code iri}, with the full property IRI, for the entity editor. */
    public List<EditableRelationship> editableIncomingRelationships(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?p ?s WHERE {
                  ?s ?p <%s> .
                  FILTER(?p != rdf:type)
                }
                """.formatted(iri);
        List<EditableRelationship> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            out.add(new EditableRelationship(row.get("p"), q.localName(row.get("p")), summarize(row.get("s"))));
        }
        return out;
    }

    /** Every outgoing resource-valued property on {@code iri}, for the generic resource page. */
    public List<LinkedResource> outgoingLinks(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?p ?o WHERE {
                  <%s> ?p ?o .
                  FILTER(isIRI(?o))
                  FILTER(?p != rdf:type)
                }
                """.formatted(iri);
        List<LinkedResource> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            String o = row.get("o");
            out.add(new LinkedResource(q.localName(row.get("p")), summarize(o), !hasAnyType(o)));
        }
        return out;
    }

    /** Every incoming resource-valued property pointing at {@code iri}, for the generic resource page. */
    public List<LinkedResource> incomingLinks(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?p ?s WHERE {
                  ?s ?p <%s> .
                  FILTER(?p != rdf:type)
                }
                """.formatted(iri);
        List<LinkedResource> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), sparql)) {
            String s = row.get("s");
            out.add(new LinkedResource(q.localName(row.get("p")), summarize(s), !hasAnyType(s)));
        }
        return out;
    }

    /** Whether {@code iri} has at least one asserted rdf:type -- "is this a typed entity of ours, or a bare external value". */
    private boolean hasAnyType(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?type WHERE { <%s> a ?type } LIMIT 1
                """.formatted(iri);
        return !q.select(store.queryModel(), sparql).isEmpty();
    }
    // ------------------------------------------------------------------
    // Record / RecordPart / RecordSet detail
    // ------------------------------------------------------------------

    public String description(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?d WHERE {
                  OPTIONAL { <%s> rico:generalDescription ?d1 }
                  OPTIONAL { <%s> rico:history ?d2 }
                  BIND(COALESCE(?d1, ?d2) AS ?d)
                } LIMIT 1
                """.formatted(iri, iri);
        List<Map<String, String>> rows = q.select(store.queryModel(), sparql);
        return rows.isEmpty() ? null : rows.get(0).get("d");
    }

    public List<ResourceSummary> creators(String iri) {
        return resolveResources(iri, Ns.RICO + "hasCreator");
    }

    public List<ResourceSummary> constituents(String iri) {
        return resolveResources(iri, Ns.RICO + "hasOrHadConstituent");
    }

    public Optional<ResourceSummary> parent(String iri) {
        return resolveResourcesInverse(iri, Ns.RICO + "hasOrHadConstituent").stream().findFirst();
    }

    public List<ResourceSummary> mandates(String iri) {
        return resolveResources(iri, Ns.RICO + "isOrWasRegulatedBy");
    }

    public List<ResourceSummary> provenanceActivities(String iri) {
        return resolveResources(iri, Ns.RICO + "hasOrganicOrFunctionalProvenance");
    }

    public List<ResourceSummary> instantiations(String iri) {
        return resolveResources(iri, Ns.RICO + "hasOrHadInstantiation");
    }

    public List<ResourceSummary> participants(String activityIri) {
        return resolveResources(activityIri, Ns.RICO + "hasOrHadParticipant");
    }

    public Optional<String> oaisCounterpart(String iri) {
        String sparql = Ns.PREFIXES + "SELECT ?o WHERE { <%s> bridge:hasOAISCounterpart ?o }".formatted(iri);
        List<Map<String, String>> rows = q.select(store.queryModel(), sparql);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0).get("o"));
    }

    // ------------------------------------------------------------------
    // Bridge / OAIS mapping
    // ------------------------------------------------------------------

    /**
     * The class-level correspondences declared in the bridging ontology for the
     * given RiC-O class (identified by local name, e.g. "Record"), driven purely
     * by the SKOS mapping triples in oais-ric-bridge.ttl -- no mapping logic is
     * hard-coded in Java.
     */
    public List<BridgeMapping> bridgeMappingsForType(String ricoTypeLocalName) {
        return bridgeMappingsFor(Ns.RICO + ricoTypeLocalName);
    }

    /**
     * Every {@code skos:*Match} correspondence declared in the bridging ontology
     * for a given IRI, plus its {@code bridge:mappingRationale} if one is
     * recorded -- driven purely by the SKOS mapping triples in
     * oais-ric-bridge.ttl, no mapping logic hard-coded in Java. Works for any
     * subject the bridge documents a correspondence for, not just RiC-O
     * classes: e.g. rico:technicalCharacteristics (a property) has a
     * relatedMatch to im:OtherRepresentationInformation, surfaced this same
     * way.
     */
    public List<BridgeMapping> bridgeMappingsFor(String iri) {
        String sparql = Ns.PREFIXES + """
                SELECT ?rel ?target ?rationale WHERE {
                  <%s> ?rel ?target .
                  FILTER(?rel IN (skos:closeMatch, skos:broadMatch, skos:narrowMatch, skos:relatedMatch))
                  OPTIONAL { <%s> bridge:mappingRationale ?rationale }
                }
                """.formatted(iri, iri);
        List<BridgeMapping> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.ontologyModel(), sparql)) {
            out.add(new BridgeMapping(
                    q.localName(row.get("rel")),
                    row.get("target"),
                    q.localName(row.get("target")),
                    row.get("rationale")));
        }
        return out;
    }

    /** rdfs:comment / bridge:noCorrespondingClass note recorded against an OAIS class, e.g. the Representation Information gap. */
    public Optional<String> classGapNote(String oaisClassLocalName) {
        String sparql = Ns.PREFIXES + """
                SELECT ?note WHERE { im:%s bridge:noCorrespondingClass ?note }
                """.formatted(oaisClassLocalName);
        List<Map<String, String>> rows = q.select(store.ontologyModel(), sparql);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0).get("note"));
    }

    /**
     * Walks the OAIS individual graph reached from {@code iri}, following any
     * outgoing property in the {@code im:} namespace whose value is a resource
     * (i.e. the has-* structural properties: has Data Object, has
     * Representation Information, has Provenance Information, and so on),
     * purely by namespace convention -- again, no property names are
     * hard-coded. Also picks up rdfs:comment and any bridge:hasRiCDescription
     * links at each node, up to {@code maxDepth} levels.
     */
    public OaisNode oaisTree(String iri, int maxDepth) {
        return oaisTree(iri, maxDepth, new LinkedHashSet<>(), null, false);
    }

    private OaisNode oaisTree(String iri, int depthRemaining, Set<String> visited,
                               String connectingProperty, boolean incoming) {
        List<String> typeNames = types(iri);
        String typeLabel = typeNames.isEmpty() ? q.localName(iri) : String.join(" / ", resolveClassLabels(typeNames));

        String commentSparql = Ns.PREFIXES + "SELECT ?c WHERE { <%s> rdfs:comment ?c } LIMIT 1".formatted(iri);
        List<Map<String, String>> commentRows = q.select(store.queryModel(), commentSparql);
        String comment = commentRows.isEmpty() ? null : commentRows.get(0).get("c");

        String ricSparql = Ns.PREFIXES + "SELECT ?r WHERE { <%s> bridge:hasRiCDescription ?r }".formatted(iri);
        List<String> ricLinks = new ArrayList<>();
        for (Map<String, String> row : q.select(store.queryModel(), ricSparql)) {
            ricLinks.add(row.get("r"));
        }

        List<OaisNode> children = new ArrayList<>();
        if (depthRemaining > 0 && visited.add(iri)) {
            // Outgoing im: edges (the has-* structural properties: this node "has" the child) --
            // the original, and still the common, case.
            String outgoingSparql = Ns.PREFIXES + """
                    SELECT ?p ?child WHERE {
                      <%s> ?p ?child .
                      FILTER(STRSTARTS(STR(?p), "%s"))
                      FILTER(isIRI(?child))
                    }
                    """.formatted(iri, Ns.IM);
            for (Map<String, String> row : q.select(store.queryModel(), outgoingSparql)) {
                children.add(oaisTree(row.get("child"), depthRemaining - 1, visited, q.localName(row.get("p")), false));
            }

            // Incoming im: edges -- a node that points AT this one rather than the other way
            // around, e.g. an ArchivalInformationPackage reaches its Content Information via
            // hasContentInformation, not the reverse (OAIS's own containment direction there is
            // AIP-contains-ContentInformation); without this half, no AIP is ever reachable by
            // walking forward from a record's counterpart, since nothing points from the content
            // side back out to the package that holds it.
            String incomingSparql = Ns.PREFIXES + """
                    SELECT ?p ?parent WHERE {
                      ?parent ?p <%s> .
                      FILTER(STRSTARTS(STR(?p), "%s"))
                      FILTER(isIRI(?parent))
                    }
                    """.formatted(iri, Ns.IM);
            for (Map<String, String> row : q.select(store.queryModel(), incomingSparql)) {
                children.add(oaisTree(row.get("parent"), depthRemaining - 1, visited, q.localName(row.get("p")), true));
            }
        }

        return new OaisNode(iri, typeLabel, comment, ricLinks, connectingProperty, incoming, children);
    }

    private List<String> resolveClassLabels(List<String> classLocalNames) {
        List<String> labels = new ArrayList<>();
        for (String local : classLocalNames) {
            String iri = Ns.IM + local;
            String sparql = Ns.PREFIXES + "SELECT ?l WHERE { <%s> rdfs:label ?l } LIMIT 1".formatted(iri);
            List<Map<String, String>> rows = q.select(store.ontologyModel(), sparql);
            labels.add(rows.isEmpty() ? local : rows.get(0).get("l"));
        }
        return labels;
    }

    /**
     * Flattens an {@link OaisNode} tree into an ordered, pre-indented list via
     * a plain pre-order walk in Java, so the view can render it with a
     * simple, non-recursive {@code th:each} instead of a recursive Thymeleaf
     * fragment.
     */
    public List<OaisFlatNode> flatten(OaisNode root) {
        List<OaisFlatNode> out = new ArrayList<>();
        flattenInto(root, 0, out);
        return out;
    }

    private void flattenInto(OaisNode node, int depth, List<OaisFlatNode> out) {
        out.add(new OaisFlatNode(
                node.iri(),
                node.typeLocalName(),
                node.comment(),
                node.ricDescriptionIris(),
                node.connectingProperty(),
                node.incoming(),
                depth,
                "margin-left:" + (depth * 28) + "px;"));
        for (OaisNode child : node.children()) {
            flattenInto(child, depth + 1, out);
        }
    }

    public List<ResourceSummary> recordsCreatedBy(String agentIri) {
        return resolveResourcesInverse(agentIri, Ns.RICO + "hasCreator");
    }

    public List<ResourceSummary> recordsRegulatedBy(String mandateIri) {
        return resolveResourcesInverse(mandateIri, Ns.RICO + "isOrWasRegulatedBy");
    }

    public List<ResourceSummary> activitiesInvolving(String agentIri) {
        return resolveResourcesInverse(agentIri, Ns.RICO + "hasOrHadParticipant");
    }

    public List<ResourceSummary> recordsWithProvenanceActivity(String activityIri) {
        return resolveResourcesInverse(activityIri, Ns.RICO + "hasOrganicOrFunctionalProvenance");
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    /** Creates a new rico:Record / rico:RecordPart / rico:RecordSet and persists it. Returns the new IRI. */
    public String createRecordResource(String typeLocalName, String title, String description,
                                        String creatorIri, String parentIri) {
        Model m = store.dataModel();
        String iri = Ns.EX + typeLocalName.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Resource res = m.createResource(iri);

        res.addProperty(RDF.type, m.createResource(Ns.RICO + typeLocalName));
        if (title != null && !title.isBlank()) {
            res.addProperty(m.createProperty(Ns.RICO, "title"), title);
        }
        if (description != null && !description.isBlank()) {
            res.addProperty(m.createProperty(Ns.RICO, "generalDescription"), description);
        }
        if (creatorIri != null && !creatorIri.isBlank()) {
            res.addProperty(m.createProperty(Ns.RICO, "hasCreator"), m.createResource(creatorIri));
        }
        if (parentIri != null && !parentIri.isBlank()) {
            m.createResource(parentIri).addProperty(m.createProperty(Ns.RICO, "hasOrHadConstituent"), res);
        }

        return iri;
    }
}
