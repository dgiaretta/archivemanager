package info.oais.archive.manager;

import org.apache.jena.query.ReadWrite;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.OntologyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OntologyServiceTest {

    @Autowired
    private OntologyService ontologyService;

    @Autowired
    private RdfStore rdfStore;

    @Test
    void exposesRdfsSeeAlsoAsLinkableProperty() {
        rdfStore.beginTransaction(ReadWrite.READ);
        try {
            assertThat(ontologyService.listObjectProperties())
                    .anySatisfy(property -> {
                        assertThat(property.iri()).isEqualTo(Ns.RDFS + "seeAlso");
                        assertThat(property.source()).isEqualTo("rdfs");
                    });
        } finally {
            rdfStore.endTransaction(true);
        }
    }

    @Test
    void exposesBridgeHasStorageLocationAsSelectableProperty() {
        rdfStore.beginTransaction(ReadWrite.READ);
        try {
            assertThat(ontologyService.listObjectProperties())
                    .anySatisfy(property -> {
                        assertThat(property.iri()).isEqualTo(Ns.BRIDGE + "hasStorageLocation");
                        assertThat(property.source()).isEqualTo("bridge");
                    });
        } finally {
            rdfStore.endTransaction(true);
        }
    }
}
