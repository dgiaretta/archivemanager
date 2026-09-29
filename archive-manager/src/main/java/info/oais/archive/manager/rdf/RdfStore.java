package info.oais.archive.manager.rdf;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The archive's actual triple store: a TDB2 {@link Dataset} -- a real,
 * disk-backed, ACID-transactional RDF store, not an in-memory model with
 * hand-rolled file persistence. It holds two named graphs:
 *
 * <ul>
 *   <li>{@code ONTOLOGY_GRAPH} -- the OAIS Information Model schema, the
 *       bridging ontology, and the RiC-O vocabulary stub. Reloaded fresh
 *       from the bundled classpath resources on every startup (cleared
 *       first), so the ontology graph always matches whatever version of
 *       those files ships with the running code, never a stale copy left
 *       over from an earlier version of the app. Used to resolve
 *       human-readable labels and to drive every "what classes/properties
 *       exist" query in the app (see {@code OntologyService}) -- nothing
 *       about the RiC-O/OAIS class or property universe is hard-coded in
 *       Java; it's all read from this graph.</li>
 *   <li>{@code DATA_GRAPH} -- the archive's actual instance data. Seeded
 *       from the bundled {@code sample-data.ttl} only the very first time
 *       the store is empty; left alone (and persisted by TDB2 itself, not
 *       by this class) on every subsequent startup.</li>
 * </ul>
 *
 * <p><b>Transactions.</b> TDB2 requires every read or write to happen
 * inside an explicit transaction -- there is no implicit auto-commit mode.
 * This class does not manage per-request transactions itself; that's
 * {@code TransactionInterceptor}'s job (it opens one in {@code preHandle}
 * and commits/aborts it in {@code afterCompletion}, so every controller
 * method and everything it calls runs inside a single transaction without
 * needing to know that). The one place this class manages its own
 * transaction directly is {@link #init()}, which runs during application
 * startup, outside any HTTP request and therefore outside the
 * interceptor's reach.
 */
@Component
public class RdfStore {

    private static final Logger log = LoggerFactory.getLogger(RdfStore.class);

    private static final String ONTOLOGY_GRAPH = "urn:x-archive:ontology";
    private static final String DATA_GRAPH = "urn:x-archive:data";

    private final Path tdbLocation;
    private Dataset dataset;

    public RdfStore(@Value("${archive.tdb-location:data/tdb2}") String tdbLocationPath) {
        this.tdbLocation = Path.of(tdbLocationPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        log.info("Opening TDB2 store at {}", tdbLocation);
        Files.createDirectories(tdbLocation);
        dataset = TDB2Factory.connectDataset(tdbLocation.toString());

        boolean committed = false;
        dataset.begin(ReadWrite.WRITE);
        try {
            Model ontology = dataset.getNamedModel(ONTOLOGY_GRAPH);
            ontology.removeAll();
            readClasspathTurtle("rdf/oais_im_schema-sh-v5.ttl", ontology);
            readClasspathTurtle("rdf/oais-im-local-extensions.ttl", ontology);
            readClasspathTurtle("rdf/oais-ric-bridge.ttl", ontology);
            readClasspathTurtle("rdf/dc-vocabulary.ttl", ontology);
            readClasspathTurtle("rdf/nam-vocabulary.ttl", ontology);
            loadRicoVocabulary(ontology);

            Model data = dataset.getNamedModel(DATA_GRAPH);
            if (data.isEmpty()) {
                log.info("Data graph is empty; loading bundled sample data");
                readClasspathTurtle("rdf/sample-data.ttl", data);
                loadOptionalClasspathTurtle("rdf/sample-data-science.ttl", data);
                loadOptionalClasspathTurtle("rdf/sample-data-pds.ttl", data);
            } else {
                log.info("Data graph already has {} triples; leaving it as-is", data.size());
                int renamed = renameNamespace(data, OLD_OAIS_NAMESPACE, NEW_OAIS_NAMESPACE);
                if (renamed > 0) {
                    log.info("Moved {} triples from {} to {}", renamed, OLD_OAIS_NAMESPACE, NEW_OAIS_NAMESPACE);
                }
            }

            log.info("Ontology graph: {} triples. Data graph: {} triples.", ontology.size(), data.size());
            dataset.commit();
            committed = true;
        } finally {
            if (!committed) {
                dataset.abort();
            }
            dataset.end();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (dataset != null) {
            dataset.close();
        }
    }

    private void readClasspathTurtle(String classpath, Model target) throws IOException {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            RDFDataMgr.read(target, in, Lang.TURTLE);
        }
    }

    /**
     * Loads an additional Turtle file into the data graph if it's present on the
     * classpath, silently doing nothing otherwise -- used for optional bundled
     * example data (e.g. {@code sample-data-science.ttl}) that supplements but
     * isn't required alongside the primary {@code sample-data.ttl} seed.
     */
    private void loadOptionalClasspathTurtle(String classpath, Model target) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpath);
        if (resource.exists()) {
            log.info("Loading additional sample data from {}", classpath);
            readClasspathTurtle(classpath, target);
        }
    }

    /** Path a downloaded copy of the real RiC-O 1.1 OWL file should be placed at, if you have one. */
    private static final String RICO_FULL_RESOURCE = "rdf/RiC-O_1-1.rdf";
    /** Where the OAIS Information Model's IRIs were until they moved to {@link #NEW_OAIS_NAMESPACE}. */
    static final String OLD_OAIS_NAMESPACE = "http://ontology.oais.org/";
    static final String NEW_OAIS_NAMESPACE = "http://ontology.oais.info/";

    /**
     * Rewrites every IRI in {@code model} that starts with {@code from} to
     * start with {@code to} instead -- subjects, properties and objects -- so
     * data saved before a namespace moved still matches the ontology, which
     * is reloaded from the bundled files at every startup. Finds nothing to
     * do once it has run.
     *
     * @return how many triples were rewritten
     */
    static int renameNamespace(Model model, String from, String to) {
        java.util.List<org.apache.jena.rdf.model.Statement> old = new java.util.ArrayList<>();
        model.listStatements().forEachRemaining(s -> {
            if (s.getSubject().isURIResource() && s.getSubject().getURI().startsWith(from)
                    || s.getPredicate().getURI().startsWith(from)
                    || s.getObject().isURIResource() && s.getObject().asResource().getURI().startsWith(from)) {
                old.add(s);
            }
        });
        for (org.apache.jena.rdf.model.Statement s : old) {
            model.remove(s);
            model.add(renamed(model, s.getSubject(), from, to).asResource(),
                    model.createProperty(renamedIri(s.getPredicate().getURI(), from, to)),
                    renamed(model, s.getObject(), from, to));
        }
        return old.size();
    }

    private static org.apache.jena.rdf.model.RDFNode renamed(Model model, org.apache.jena.rdf.model.RDFNode node,
                                                             String from, String to) {
        return node.isURIResource() ? model.createResource(renamedIri(node.asResource().getURI(), from, to)) : node;
    }

    private static String renamedIri(String iri, String from, String to) {
        return iri.startsWith(from) ? to + iri.substring(from.length()) : iri;
    }

    private static final String RICO_STUB_RESOURCE = "rdf/rico-vocabulary.ttl";

    /**
     * Loads the real RiC-O 1.1 ontology (107 classes, 75 datatype properties, 480
     * object properties, full rdfs:subClassOf hierarchy, multilingual labels) if a
     * copy has been placed at {@value #RICO_FULL_RESOURCE} -- download it from
     * https://raw.githubusercontent.com/ICA-EGAD/RiC-O/master/ontology/current-version/RiC-O_1-1.rdf
     * (CC BY 4.0) and add it under {@code src/main/resources/} at exactly that path.
     * Falls back to the bundled {@code rico-vocabulary.ttl} stub -- all 107 class
     * names and a curated set of properties, but not the full property set or
     * hierarchy -- if no such file is present, so the app works either way.
     *
     * <p>The real file is RDF/XML, not Turtle (note the different {@link Lang}),
     * and it's large enough (several MB) that reparsing it from scratch on every
     * startup, as this app's small ontology files already do for simplicity and
     * always-fresh-from-source correctness, will add some real (if not benchmarked
     * here) seconds to startup. If that becomes a problem, the fix is to stop
     * calling {@code ontology.removeAll()} before this and only load the real file
     * once, the same way the *data* graph in {@link #init()} is seeded once and
     * then left alone -- at the cost of no longer picking up a re-downloaded, newer
     * copy of the file automatically on restart.
     */
    private void loadRicoVocabulary(Model ontology) throws IOException {
        ClassPathResource full = new ClassPathResource(RICO_FULL_RESOURCE);
        if (full.exists()) {
            log.info("Loading the full RiC-O 1.1 ontology from {}", RICO_FULL_RESOURCE);
            try (InputStream in = full.getInputStream()) {
                RDFDataMgr.read(ontology, in, Lang.RDFXML);
            }
        } else {
            log.info("{} not found; using the bundled rico-vocabulary.ttl stub "
                    + "(all 107 class names, a curated property set, partial hierarchy -- "
                    + "see README 'Is the real RiC-O ontology loaded?')", RICO_FULL_RESOURCE);
            readClasspathTurtle(RICO_STUB_RESOURCE, ontology);
        }
    }

    /** Read-only union of the ontology and data graphs, for SPARQL SELECT/ASK queries that need both. */
    public Model queryModel() {
        return dataset.getUnionModel();
    }

    /** The ontology graph alone (schema classes/properties + bridge mappings). */
    public Model ontologyModel() {
        return dataset.getNamedModel(ONTOLOGY_GRAPH);
    }

    /** The mutable instance-data graph. Callers add/remove triples here directly. */
    public Model dataModel() {
        return dataset.getNamedModel(DATA_GRAPH);
    }

    /** Where the TDB2 database lives on disk, for display/diagnostics. */
    public Path storageLocation() {
        return tdbLocation;
    }

    /**
     * Begins a transaction on the underlying dataset. Called by
     * {@code TransactionInterceptor} at the start of every request that
     * reaches a controller; not normally called directly elsewhere.
     */
    public void beginTransaction(ReadWrite mode) {
        dataset.begin(mode);
    }

    /**
     * Ends whatever transaction is currently open, committing on success or
     * aborting on failure. Safe to call even if no transaction is open (a
     * no-op in that case). Called by {@code TransactionInterceptor} once a
     * request has finished, including view rendering.
     */
    public void endTransaction(boolean success) {
        if (!dataset.isInTransaction()) {
            return;
        }
        try {
            if (success) {
                dataset.commit();
            } else {
                dataset.abort();
            }
        } finally {
            dataset.end();
        }
    }
}
