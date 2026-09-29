package info.oais.archive.manager.service;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import info.oais.archive.manager.rdf.IdCodec;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All write operations the entity editor performs, direct against the
 * mutable data graph via the Jena API (no SPARQL UPDATE needed at this
 * scale). This is intentionally the only class in the app that mutates
 * {@link RdfStore#dataModel()}, besides the older, narrower
 * {@code ArchiveService.createRecordResource}.
 *
 * <p>Unlike the file-backed version of this app, there is no explicit
 * "save" call here: the data graph is a named graph inside TDB2, a real
 * transactional store, and every request that reaches these methods is
 * already running inside a write transaction opened by
 * {@code TransactionInterceptor} before the controller method ran and
 * committed after it returns (see that class). These methods just make
 * changes to the model; the commit -- the actual persistence to disk -- is
 * the interceptor's job, not this class's. Java-level {@code synchronized}
 * isn't needed either: TDB2 only ever allows one write transaction across
 * the whole dataset at a time, so mutual exclusion is already enforced at
 * the storage layer by the time a request reaches these methods.
 */
@Service
public class EditService {

    private final RdfStore store;
    private final QueryRunner q;

    public EditService(RdfStore store, QueryRunner q) {
        this.store = store;
        this.q = q;
    }

    /** Creates a brand-new individual of the given class. Returns its IRI. */
    public String createEntity(String classIri) {
        Model m = store.dataModel();
        String localSeed = classIri.contains("#") ? classIri.substring(classIri.lastIndexOf('#') + 1)
                : classIri.substring(classIri.lastIndexOf('/') + 1);
        String iri = Ns.EX + slug(localSeed) + "-" + UUID.randomUUID().toString().substring(0, 8);
        m.createResource(iri).addProperty(RDF.type, m.createResource(classIri));
        return iri;
    }

    /**
     * Creates an individual with a given, stable IRI unless it already
     * exists -- for shared individuals found by their IRI rather than looked
     * up, e.g. the software a kind of description needs.
     *
     * @return whether it was created (false: it was already there, and is left as it is)
     */
    public boolean createEntityIfAbsent(String iri, String classIri) {
        Model m = store.dataModel();
        Resource resource = m.getResource(iri);
        if (m.listStatements(resource, null, (org.apache.jena.rdf.model.RDFNode) null).hasNext()) {
            return false;
        }
        resource.addProperty(RDF.type, m.createResource(classIri));
        return true;
    }

    public void addType(String iri, String typeIri) {
        Model m = store.dataModel();
        m.getResource(iri).addProperty(RDF.type, m.createResource(typeIri));
    }

    public void removeType(String iri, String typeIri) {
        Model m = store.dataModel();
        m.removeAll(m.getResource(iri), RDF.type, m.createResource(typeIri));
    }

    public void addLiteral(String iri, String propertyIri, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Model m = store.dataModel();
        Resource subject = m.getResource(iri);
        Property property = m.createProperty(propertyIri);
        subject.addProperty(property, value);
    }

    public void removeLiteral(String iri, String propertyIri, String value) {
        Model m = store.dataModel();
        Resource subject = m.getResource(iri);
        Property property = m.createProperty(propertyIri);
        Literal literal = m.createLiteral(value);
        m.removeAll(subject, property, literal);
    }

    public void addRelationship(String subjectIri, String propertyIri, String objectIri) {
        Model m = store.dataModel();
        Resource subject = m.getResource(subjectIri);
        Property property = m.createProperty(propertyIri);
        Resource object = m.createResource(objectIri);
        subject.addProperty(property, object);
    }

    public void removeRelationship(String subjectIri, String propertyIri, String objectIri) {
        Model m = store.dataModel();
        m.removeAll(m.getResource(subjectIri), m.createProperty(propertyIri), m.getResource(objectIri));
    }

    /** Deletes every triple in which {@code iri} appears, as subject or as object. */
    public void deleteResource(String iri) {
        Model m = store.dataModel();
        Resource resource = m.getResource(iri);
        m.removeAll(resource, null, null);
        m.removeAll(null, null, resource);
    }

    /**
     * Deletes an accession AND every resource {@link CatalogueImportService}
     * created alongside it in that same import: the records themselves, plus
     * their full OAIS counterpart structure (ContentInformation/InformationObject,
     * AIP, PreservationDescriptionInformation, and whichever
     * Provenance/Fixity/AccessRights/Reference/Representation Information
     * sub-individuals each row happened to have). Deliberately does *not*
     * touch Agent individuals ({@code findOrCreateCorporateBody}) -- those are
     * shared/reused across imports by name, so one created during this batch
     * might also be referenced by records from a completely different batch;
     * bulk-deleting it could silently orphan those too.
     *
     * <p>This exists specifically because plain {@link #deleteResource} does
     * NOT cascade -- deleting just the accession leaves its records (and
     * their OAIS structure) fully intact in the catalogue, only missing the
     * provenance link back to the now-deleted accession. That's easy to miss
     * when testing/cleaning up: a "delete and reimport" cycle looks like a
     * fresh start but isn't one, since the old records are still there,
     * still findable via search or a previously-visited link, silently
     * alongside the new ones.
     *
     * <p>Works by IRI pattern, not graph traversal: every resource this
     * import service creates is named {@code ex:record-<batchSlug>-...}
     * (including its OAIS counterparts, which extend that same prefix
     * further) or {@code ex:<batchSlug>-date}, where {@code batchSlug} is
     * the accession's own local name (e.g. "acc-217add5f") -- a random
     * 8-hex-character UUID fragment, safe from any realistic false-positive
     * match. Finding every subject whose IRI contains that slug and removing
     * every triple it appears in (as subject or object) reaches everything
     * from that one import without needing to walk the OAIS/bridge
     * structure explicitly.
     *
     * @return how many resources were deleted, for a confirmation message.
     */
    public int deleteAccessionCascade(String accessionIri) {
        Model m = store.dataModel();
        String batchSlug = accessionIri.substring(accessionIri.lastIndexOf('/') + 1);
        String sparql = Ns.PREFIXES + """
                SELECT DISTINCT ?s WHERE {
                  ?s ?p ?o .
                  FILTER(CONTAINS(STR(?s), "%s"))
                }
                """.formatted(Ns.escapeSparqlLiteral(batchSlug));
        List<Map<String, String>> rows = q.select(m, sparql);
        int count = 0;
        for (Map<String, String> row : rows) {
            Resource r = m.getResource(row.get("s"));
            m.removeAll(r, null, null);
            m.removeAll(null, null, r);
            count++;
        }
        return count;
    }

    /**
     * Runs {@link #deleteAccessionCascade} across every accession in the
     * data graph, for clearing out accumulated test/duplicate imports in
     * one step (e.g. several upload-test-delete-reupload cycles where the
     * old, non-cascading delete left records behind each time -- exactly
     * the scenario that made diagnosing an unrelated encoding issue
     * confusing, since old and new imports were sitting side by side).
     *
     * @return how many accessions were deleted (not the total resource count across all of them).
     */
    public int deleteAllAccessionsCascade() {
        String sparql = Ns.PREFIXES + """
                SELECT ?s WHERE {
                  ?s a nam:Accession .
                }
                """;
        List<Map<String, String>> rows = q.select(store.dataModel(), sparql);
        int count = 0;
        for (Map<String, String> row : rows) {
            deleteAccessionCascade(row.get("s"));
            count++;
        }
        return count;
    }

    /**
     * The more thorough cleanup {@link #deleteAllAccessionsCascade} can't
     * do on its own: that method only finds accessions that still exist as
     * a {@code nam:Accession} entity, so it can't reach records whose
     * accession was already removed by the older, non-cascading
     * {@code deleteResource} before this cascading delete existed --
     * exactly what happened repeatedly during this project's own encoding
     * debugging, leaving thousands of orphaned records with no surviving
     * accession to clean them up from.
     *
     * <p>This works from IRI structure instead, which survives regardless
     * of whether the accession entity itself still exists: every resource
     * {@link CatalogueImportService} creates is named either
     * {@code ex:record-<batchSlug>-...} (records and their full OAIS
     * counterpart structure, which extends that same prefix further) or
     * {@code ex:<batchSlug>-...} (the accession itself and its date
     * individual), where {@code batchSlug} always starts with
     * {@code "acc-"}. Deleting every resource under either prefix reaches
     * everything any import has ever created, orphaned or not, in one
     * pass. Deliberately still leaves Agent individuals alone (named
     * {@code ex:agent-...}, a different prefix, reused by name across
     * imports) for the same reason {@link #deleteAccessionCascade} does.
     *
     * @return how many resources were deleted.
     */
    public int deleteAllCatalogueImportData() {
        Model m = store.dataModel();
        String sparql = Ns.PREFIXES + """
                SELECT DISTINCT ?s WHERE {
                  ?s ?p ?o .
                  FILTER(STRSTARTS(STR(?s), "%s") || STRSTARTS(STR(?s), "%s"))
                }
                """.formatted(Ns.escapeSparqlLiteral(Ns.EX + "record-"), Ns.escapeSparqlLiteral(Ns.EX + "acc-"));
        List<Map<String, String>> rows = q.select(m, sparql);
        int count = 0;
        for (Map<String, String> row : rows) {
            Resource r = m.getResource(row.get("s"));
            m.removeAll(r, null, null);
            m.removeAll(null, null, r);
            count++;
        }
        return count;
    }

    private String slug(String s) {
        String withHyphens = s.replaceAll("(?<!^)(?=[A-Z])", "-").toLowerCase();
        return withHyphens.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
    }

    public String encodeId(String iri) {
        return IdCodec.encode(iri);
    }
}
