package info.oais.archive.manager.service;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Renames terms that moved into the OAIS Information Model's local extensions
 * ({@code oais-im-local-extensions.ttl}) so that OAIS Representation
 * Information depends on neither RiC-O nor the RiC bridge:
 * <ul>
 *   <li>{@code bridge:hasStorageLocation}, {@code bridge:structuralPath},
 *       {@code bridge:scaleFactor}, {@code bridge:addOffset},
 *       {@code bridge:fillValue}, {@code bridge:validMin},
 *       {@code bridge:validMax}, {@code bridge:hasCodeList} and
 *       {@code bridge:representsConcept} become the same-named {@code im:}
 *       properties, wherever they are used;</li>
 *   <li>{@code rico:hasUnitOfMeasurement} becomes {@code im:hasUnitOfMeasurement}
 *       on OAIS individuals (anything with an {@code im:} type), and is left
 *       alone on RiC-O ones (an Extent's unit). A {@code rico:UnitOfMeasurement}
 *       becomes an {@code im:UnitOfMeasurement} unless RiC-O still uses it --
 *       including a unit nothing uses any more, since RepInfo Tools made all
 *       of this archive's units; one used by both keeps both types.</li>
 * </ul>
 * Runs once at startup, and finds nothing to do after that.
 */
@Component
public class LocalExtensionsMigration {

    private static final Logger log = LoggerFactory.getLogger(LocalExtensionsMigration.class);

    /** Properties that were in the RiC bridge, under the same local names. */
    static final List<String> FROM_BRIDGE = List.of("hasStorageLocation", "structuralPath", "scaleFactor",
            "addOffset", "fillValue", "validMin", "validMax", "hasCodeList", "representsConcept");

    private final RdfStore store;

    public LocalExtensionsMigration(RdfStore store) {
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        boolean success = false;
        store.beginTransaction(ReadWrite.WRITE);
        try {
            int renamed = migrate();
            if (renamed > 0) {
                log.info("Renamed {} statement(s) using RiC bridge or RiC-O terms to the OAIS local extensions",
                        renamed);
            }
            success = true;
        } finally {
            store.endTransaction(success);
        }
    }

    /**
     * Renames the moved terms in the data graph; needs a write transaction.
     *
     * @return how many statements were renamed
     */
    public int migrate() {
        Model m = store.dataModel();
        int renamed = 0;
        for (String name : FROM_BRIDGE) {
            renamed += rename(m, m.listStatements(null, m.createProperty(Ns.BRIDGE + name), (RDFNode) null).toList(),
                    m.createProperty(Ns.IM + name));
        }

        Property ricUnit = m.createProperty(Ns.RICO + "hasUnitOfMeasurement");
        List<Statement> onOais = m.listStatements(null, ricUnit, (RDFNode) null).filterKeep(s -> isOais(s.getSubject()))
                .toList();
        Property imUnit = m.createProperty(Ns.IM + "hasUnitOfMeasurement");
        renamed += rename(m, onOais, imUnit);
        Resource ricUnitClass = m.createResource(Ns.RICO + "UnitOfMeasurement");
        Resource imUnitClass = m.createResource(Ns.IM + "UnitOfMeasurement");
        for (Resource unit : m.listSubjectsWithProperty(RDF.type, ricUnitClass).toList()) {
            boolean usedByRic = m.contains(null, ricUnit, unit);
            if (!usedByRic) {
                m.remove(unit, RDF.type, ricUnitClass);
            }
            if (!usedByRic || m.contains(null, imUnit, unit)) {
                unit.addProperty(RDF.type, imUnitClass);
                renamed++;
            }
        }
        return renamed;
    }

    private static int rename(Model m, List<Statement> found, Property renamed) {
        for (Statement s : found) {
            m.add(s.getSubject(), renamed, s.getObject());
        }
        m.remove(found);
        return found.size();
    }

    private static boolean isOais(Resource r) {
        return r.listProperties(RDF.type).toList().stream()
                .anyMatch(t -> t.getObject().isURIResource() && t.getObject().asResource().getURI().startsWith(Ns.IM));
    }
}
