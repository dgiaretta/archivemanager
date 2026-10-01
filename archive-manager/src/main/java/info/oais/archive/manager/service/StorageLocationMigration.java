package info.oais.archive.manager.service;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renames {@code bridge:hasStorageLocation} to {@code im:hasStorageLocation}
 * in the data graph. The term moved from the RiC bridge ontology to the OAIS
 * Information Model's local extensions ({@code oais-im-local-extensions.ttl});
 * every statement using the old name is replaced by the same statement with
 * the new one. Runs once at startup, and finds nothing to do after that.
 */
@Component
public class StorageLocationMigration {

    private static final Logger log = LoggerFactory.getLogger(StorageLocationMigration.class);

    private final RdfStore store;

    public StorageLocationMigration(RdfStore store) {
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        boolean success = false;
        store.beginTransaction(ReadWrite.WRITE);
        try {
            int renamed = migrate();
            if (renamed > 0) {
                log.info("Renamed {} bridge:hasStorageLocation statement(s) to im:hasStorageLocation", renamed);
            }
            success = true;
        } finally {
            store.endTransaction(success);
        }
    }

    /**
     * Replaces every {@code bridge:hasStorageLocation} statement in the data
     * graph with an {@code im:hasStorageLocation} one; needs a write transaction.
     *
     * @return how many statements were renamed
     */
    public int migrate() {
        Model m = store.dataModel();
        Property old = m.createProperty(Ns.BRIDGE + "hasStorageLocation");
        Property renamed = m.createProperty(Ns.IM + "hasStorageLocation");
        List<Statement> found = m.listStatements((Resource) null, old, (RDFNode) null).toList();
        for (Statement s : found) {
            m.add(s.getSubject(), renamed, s.getObject());
        }
        m.remove(found);
        return found.size();
    }
}
