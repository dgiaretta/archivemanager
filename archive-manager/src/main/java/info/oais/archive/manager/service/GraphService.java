package info.oais.archive.manager.service;

import info.oais.archive.manager.model.ClassOption;
import info.oais.archive.manager.model.GraphData;
import info.oais.archive.manager.model.GraphEdge;
import info.oais.archive.manager.model.GraphLink;
import info.oais.archive.manager.model.GraphNode;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import org.apache.jena.query.ReadWrite;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the data behind the interactive relationship graph (rendered client-side
 * with vis-network). Only resource-to-resource triples become edges -- literal
 * values have no second node to draw a line to, so they're rendered on the
 * regular detail pages instead -- but a node's literal properties (title,
 * description, an external seeAlso URL...) still feed the property highlight
 * filter alongside its resource-linking ones. Every triple in the graph comes
 * straight from the data model via SPARQL -- nothing about which properties
 * exist is hard-coded here.
 */
@Service
public class GraphService {

    private static final int MAX_NODES = 300;

    private final RdfStore store;
    private final QueryRunner q;
    private final ArchiveService archive;
    private final OntologyService ontology;
    private final Set<String> ricoClassLocalNames;
    private final Set<String> oaisClassLocalNames;
    private final Set<String> recordResourceLocalNames;
    private final Set<String> agentLocalNames;
    private final Set<String> eventLocalNames;
    private final Set<String> mandateLocalNames;

    private final info.oais.archive.manager.service.format.DataObjectViewService views;

    public GraphService(RdfStore store, QueryRunner q, ArchiveService archive, OntologyService ontology,
                        info.oais.archive.manager.service.format.DataObjectViewService views) {
        this.views = views;
        this.store = store;
        this.q = q;
        this.archive = archive;
        this.ontology = ontology;
        // Group membership (rico / oais / other) and routing (which detail page a
        // node's type belongs to) are both resolved against the real ontology graph
        // -- class lists and hierarchy from rico-vocabulary.ttl / the OAIS schema --
        // rather than hard-coded Java lists of class names.
        //
        // This runs during Spring bean construction at application startup, which
        // is *outside* any HTTP request -- so TransactionInterceptor (which only
        // opens a transaction in preHandle, per-request) never runs for it. TDB2
        // requires an explicit transaction for every read, with no auto-commit
        // fallback, so one is opened and closed by hand here, the same way
        // RdfStore.init() manages its own startup-time transaction.
        boolean ok = false;
        store.beginTransaction(ReadWrite.READ);
        try {
            // "rico" grouping (for graph node colour) also covers the dc:/nam:
            // namespaces -- Dublin Core and NAM's small extension vocabulary are both
            // descriptive/archival-metadata concepts, not OAIS preservation ones, so
            // they read naturally as part of the same blue "rico" family visually
            // rather than needing two more colours of their own.
            this.ricoClassLocalNames = ontology.listClasses().stream()
                    .filter(c -> "rico".equals(c.source()) || "dc".equals(c.source()) || "nam".equals(c.source()))
                    .map(ClassOption::localName)
                    .collect(Collectors.toSet());
            this.oaisClassLocalNames = ontology.listClasses().stream()
                    .filter(c -> "oais".equals(c.source()))
                    .map(ClassOption::localName)
                    .collect(Collectors.toSet());
            this.recordResourceLocalNames = ontology.subclassLocalNames(Ns.RICO + "RecordResource");
            this.agentLocalNames = ontology.subclassLocalNames(Ns.RICO + "Agent");
            this.eventLocalNames = ontology.subclassLocalNames(Ns.RICO + "Event");
            this.mandateLocalNames = ontology.subclassLocalNames(Ns.RICO + "Mandate");
            ok = true;
        } finally {
            store.endTransaction(ok);
        }
    }

    private static final int MAX_FULL_GRAPH_TRIPLES = 1500;

    /**
     * Every resource-to-resource triple in the data graph, capped at
     * {@link #MAX_FULL_GRAPH_TRIPLES} -- an archive of any real size would
     * otherwise ship its entire graph to the browser as one JSON payload and
     * hand it to vis-network's physics simulation, which is the first thing
     * to become impractical long before TDB2 itself would notice the load.
     * Use the focused {@link #subgraph(String, int)} view for anything
     * larger; {@code GraphData.truncated()} tells the caller whether this cap
     * was hit.
     */
    public GraphData fullGraph() {
        String sparql = Ns.PREFIXES + """
                SELECT ?s ?p ?o WHERE {
                  ?s ?p ?o .
                  FILTER(isIRI(?o))
                  FILTER(?p != rdf:type)
                } LIMIT %d
                """.formatted(MAX_FULL_GRAPH_TRIPLES + 1);
        List<String[]> triples = new ArrayList<>();
        for (Map<String, String> row : q.select(store.dataModel(), sparql)) {
            triples.add(new String[]{row.get("s"), row.get("p"), row.get("o")});
        }
        boolean truncated = triples.size() > MAX_FULL_GRAPH_TRIPLES;
        if (truncated) {
            triples = triples.subList(0, MAX_FULL_GRAPH_TRIPLES);
        }
        return fromTriples(triples, truncated);
    }

    /**
     * Predicates actually used somewhere in the data store on a resource-to-resource
     * triple (the same shape {@link #fromTriples} turns into node properties/edges) --
     * not every property the ontology merely declares. Querying the whole store
     * rather than just the rendered/truncated payload is what lets the property
     * highlight filter stay complete even when a focused or full-graph view had to
     * cut off some edges.
     */
    private List<String> knownPropertyNames() {
        String sparql = Ns.PREFIXES + """
                SELECT DISTINCT ?p WHERE {
                  ?s ?p ?o .
                  FILTER(isIRI(?o))
                  FILTER(?p != rdf:type)
                }
                """;
        Set<String> propertyNames = new LinkedHashSet<>();
        for (Map<String, String> row : q.select(store.dataModel(), sparql)) {
            propertyNames.add(q.localName(row.get("p")));
        }
        return new ArrayList<>(propertyNames);
    }

    /**
     * Properties whose object is a URL/URI to an external object, not a graph node.
     *
     * <p>The canonical external-link property is {@code rdfs:seeAlso}; applications may
     * also add more specific URI-valued link properties (for example the bridge-level
     * {@code hasStorageLocation} used for Dropbox/file URLs). The ontology graph is not
     * guaranteed to contain a fully materialized {@code rdfs:seeAlso} declaration in
     * every deployment, so we retain the explicit fallback names here in addition to
     * the ontology-based subPropertyOf discovery.
     */
    private Set<String> externalLinkPropertyNames() {
        String sparql = Ns.PREFIXES + """
                SELECT DISTINCT ?p WHERE {
                  {
                    ?p rdfs:subPropertyOf* rdfs:seeAlso .
                  }
                  UNION {
                    VALUES ?p { rdfs:seeAlso }
                  }
                }
                """;
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, String> row : q.select(store.ontologyModel(), sparql)) {
            names.add(q.localName(row.get("p")));
        }
        names.add(q.localName(Ns.RDFS + "seeAlso"));
        names.add(q.localName(Ns.IM + "hasStorageLocation"));
        return names;
    }

    /** The subgraph reachable from {@code focusIri} within {@code depth} hops, in either direction. */
    public GraphData subgraph(String focusIri, int depth) {
        return subgraph(List.of(focusIri), depth);
    }

    /**
     * The union of the subgraphs reachable from each of {@code focusIris} within
     * {@code depth} hops, in either direction -- a single BFS seeded from every
     * given IRI at once rather than one call per IRI unioned afterward, so a node
     * reachable from more than one focus (or a focus reachable from another) is
     * still capped by the one shared {@link #MAX_NODES} budget instead of by a
     * separate one per call. Powers the "view as graph" button on the various
     * listing pages (entities/agents/records/...), graphing exactly the page of
     * results shown rather than the whole archive.
     */
    public GraphData subgraph(Collection<String> focusIris, int depth) {
        Map<String, String[]> triples = new LinkedHashMap<>(); // key: s|p|o
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        for (String focusIri : focusIris) {
            queue.add(focusIri);
            depths.add(Math.max(depth, 0));
        }

        while (!queue.isEmpty() && visited.size() < MAX_NODES) {
            String current = queue.poll();
            int remaining = depths.poll();
            if (!visited.add(current)) {
                continue;
            }

            // A node already at the requested radius should not expand its own
            // neighbors, otherwise depth=1 will quietly turn into depth=2 through
            // the current item's outgoing/incoming traversal. The focus node itself
            // remains eligible for inclusion when its edges were added earlier in the
            // queue, but no further hop should be crawled beyond the requested depth.
            if (remaining == 0) {
                continue;
            }

            String outSparql = Ns.PREFIXES + """
                    SELECT ?p ?o WHERE {
                      <%s> ?p ?o .
                      FILTER(isIRI(?o))
                      FILTER(?p != rdf:type)
                    }
                    """.formatted(current);
            for (Map<String, String> row : q.select(store.dataModel(), outSparql)) {
                String o = row.get("o");
                triples.put(current + "|" + row.get("p") + "|" + o, new String[]{current, row.get("p"), o});
                if (!visited.contains(o)) {
                    queue.add(o);
                    depths.add(remaining - 1);
                }
            }

            String inSparql = Ns.PREFIXES + """
                    SELECT ?p ?s WHERE {
                      ?s ?p <%s> .
                      FILTER(?p != rdf:type)
                    }
                    """.formatted(current);
            for (Map<String, String> row : q.select(store.dataModel(), inSparql)) {
                String s = row.get("s");
                triples.put(s + "|" + row.get("p") + "|" + current, new String[]{s, row.get("p"), current});
                if (!visited.contains(s)) {
                    queue.add(s);
                    depths.add(remaining - 1);
                }
            }
        }
        return fromTriples(triples.values(), visited.size() >= MAX_NODES);
    }

    private GraphData fromTriples(Collection<String[]> triples, boolean truncated) {
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        Map<String, Set<String>> propertiesByNode = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> externalLinkPropertyNames = externalLinkPropertyNames();
        // Only reach for the whole-store property vocabulary when this payload was
        // actually truncated -- otherwise every property below comes straight from
        // the triples this call is about to process, so the filter never offers a
        // property that belongs to some unrelated node elsewhere in the archive
        // (which would always match zero of the nodes actually on screen).
        Set<String> knownPropertyNames = truncated ? new LinkedHashSet<>(knownPropertyNames()) : new LinkedHashSet<>();
        for (String[] t : triples) {
            String s = t[0];
            String p = t[1];
            String o = t[2];
            String propertyLocal = q.localName(p);
            nodes.computeIfAbsent(s, iri -> makeNode(iri, List.of()));
            propertiesByNode.computeIfAbsent(s, k -> new LinkedHashSet<>()).add(propertyLocal);
            knownPropertyNames.add(propertyLocal);

            if (externalLinkPropertyNames.contains(propertyLocal)) {
                // A URI-valued external reference is metadata on this node, not a graph edge.
                continue;
            }

            nodes.computeIfAbsent(o, iri -> makeNode(iri, List.of()));
            propertiesByNode.computeIfAbsent(o, k -> new LinkedHashSet<>()).add(propertyLocal);
            boolean cross = !nodes.get(s).group().equals(nodes.get(o).group())
                    && !nodes.get(s).group().equals("other") && !nodes.get(o).group().equals("other");
            edges.add(new GraphEdge(s, o, propertyLocal, cross));
        }

        // Literal-valued properties (title/description/dates/external-link URLs...) never
        // become edges -- there's no second node to draw a line to -- but a node still
        // "has" them, and the client's property highlight filter should be able to select
        // on them the same way it does for resource-linking properties. Scoped to exactly
        // the nodes already in this payload, for the same reason knownPropertyNames() above
        // is scoped: an unrelated node's literal properties would never match anything here.
        Map<String, List<GraphLink>> linksByNode = new LinkedHashMap<>();
        if (!nodes.isEmpty()) {
            String values = nodes.keySet().stream().map(iri -> "<" + iri + ">").collect(Collectors.joining(" "));
            String literalSparql = Ns.PREFIXES + """
                    SELECT ?s ?p ?o WHERE {
                      VALUES ?s { %s }
                      ?s ?p ?o .
                      FILTER(isLiteral(?o))
                      FILTER(?p != rico:title && ?p != rico:name && ?p != rico:generalDescription)
                    }
                    """.formatted(values);
            for (Map<String, String> row : q.select(store.dataModel(), literalSparql)) {
                String propertyLocal = q.localName(row.get("p"));
                propertiesByNode.computeIfAbsent(row.get("s"), k -> new LinkedHashSet<>()).add(propertyLocal);
                knownPropertyNames.add(propertyLocal);
                String value = row.get("o");
                if (value != null && (value.startsWith("http://") || value.startsWith("https://"))) {
                    linksByNode.computeIfAbsent(row.get("s"), k -> new ArrayList<>()).add(new GraphLink(propertyLocal, value));
                }
            }

            // Queried independently, scoped to every node in this payload -- not read
            // back out of `triples` above, which (for a focused subgraph()) only holds
            // what the BFS actually crawled, and a node sitting right at the requested
            // depth boundary has its own outgoing edges deliberately never crawled (see
            // the "remaining == 0" comment in subgraph()). An external-link property
            // isn't a graph edge in the first place (a URI-valued reference is metadata
            // on the node, not a hop to another node -- see the check above), so there's
            // no reason its visibility should depend on how the BFS happened to reach
            // this node from wherever the graph view is centred.
            String externalLinkSparql = Ns.PREFIXES + """
                    SELECT ?s ?p ?o WHERE {
                      VALUES ?s { %s }
                      ?s ?p ?o .
                      FILTER(isIRI(?o))
                    }
                    """.formatted(values);
            for (Map<String, String> row : q.select(store.dataModel(), externalLinkSparql)) {
                String propertyLocal = q.localName(row.get("p"));
                String o = row.get("o");
                if (externalLinkPropertyNames.contains(propertyLocal) && (o.startsWith("http://") || o.startsWith("https://"))) {
                    linksByNode.computeIfAbsent(row.get("s"), k -> new ArrayList<>()).add(new GraphLink(propertyLocal, o));
                }
            }
        }

        Map<String, GraphNode> enrichedNodes = new LinkedHashMap<>();
        for (Map.Entry<String, GraphNode> entry : nodes.entrySet()) {
            String iri = entry.getKey();
            GraphNode node = entry.getValue();
            List<String> properties = new ArrayList<>(propertiesByNode.getOrDefault(iri, Set.of()));
            properties.sort(String.CASE_INSENSITIVE_ORDER);
            List<GraphLink> links = linksByNode.getOrDefault(iri, List.of());
            enrichedNodes.put(iri, new GraphNode(iri, node.label(), node.group(), node.types(), properties, node.title(),
                    node.detailUrl(), links, viewersFor(iri)));
        }
        List<String> propertyNames = new ArrayList<>(knownPropertyNames);
        propertyNames.sort(String.CASE_INSENSITIVE_ORDER);
        return new GraphData(new ArrayList<>(enrichedNodes.values()), edges, truncated, propertyNames);
    }

    /**
     * The applications this node's data can be viewed with -- for a Data
     * Object whose Representation Information network, followed through the
     * triples, leads to what each needs (see {@code DataObjectViewService#viewers}).
     */
    private List<info.oais.archive.manager.model.GraphViewer> viewersFor(String iri) {
        List<info.oais.archive.manager.service.format.DataObjectViewService.Viewer> found = views.viewers(iri);
        if (found.isEmpty()) {
            return List.of();
        }
        String id = archive.encodeId(iri);
        return found.stream().map(v -> new info.oais.archive.manager.model.GraphViewer(v.id(), v.label(), v.mtype(),
                v.clientName(), v.format(), "/api/data-objects/" + id + "/" + v.format(),
                "/api/data-objects/" + id + "/repinfo.ttl"))
                .toList();
    }

    private GraphNode makeNode(String iri, List<String> properties) {
        List<String> types = archive.types(iri);
        String group = groupFor(types);
        String label = archive.label(iri);
        String title = iri + (types.isEmpty() ? "" : " (" + String.join(", ", types) + ")");
        return new GraphNode(iri, label, group, types, properties, title, detailUrlFor(iri, types), List.of());
    }

    private String groupFor(List<String> typeLocalNames) {
        for (String t : typeLocalNames) {
            if (ricoClassLocalNames.contains(t)) {
                return "rico";
            }
            if (oaisClassLocalNames.contains(t)) {
                return "oais";
            }
        }
        return "other";
    }

    private String detailUrlFor(String iri, List<String> types) {
        for (String t : types) {
            if (recordResourceLocalNames.contains(t)) {
                return "/records/" + archive.encodeId(iri);
            }
            if (agentLocalNames.contains(t)) {
                return "/agents/" + archive.encodeId(iri);
            }
            if (eventLocalNames.contains(t)) {
                return "/activities/" + archive.encodeId(iri);
            }
            if (mandateLocalNames.contains(t)) {
                return "/mandates/" + archive.encodeId(iri);
            }
        }
        return "/resource/" + archive.encodeId(iri);
    }
}
