package info.oais.archive.manager;

import org.apache.jena.query.ReadWrite;
import info.oais.archive.manager.model.GraphData;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.GraphService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GraphServiceTest {

    @Autowired
    private GraphService graphService;

    @Autowired
    private RdfStore rdfStore;

    @Test
    void externalLinkPropertiesAreNotRenderedAsGraphEdges() {
        rdfStore.beginTransaction(ReadWrite.WRITE);
        try {
            String subjectIri = "http://example.org/subject";
            String externalUrl = "https://example.org/external";
            var model = rdfStore.dataModel();
            model.add(model.createResource(subjectIri), model.createProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso"), model.createResource(externalUrl));
            model.add(model.createResource(subjectIri), model.createProperty("https://oais.info/bridge#hasStorageLocation"), model.createResource(externalUrl));

            GraphData graph = graphService.fullGraph();

            assertThat(graph.propertyNames()).contains("seeAlso", "hasStorageLocation");
            assertThat(graph.edges()).noneMatch(edge -> "seeAlso".equals(edge.label()) || "hasStorageLocation".equals(edge.label()));
            assertThat(graph.nodes()).anySatisfy(node -> {
                if (subjectIri.equals(node.id())) {
                    assertThat(node.properties()).contains("seeAlso", "hasStorageLocation");
                    assertThat(node.links()).extracting("propertyLocalName").contains("seeAlso", "hasStorageLocation");
                }
            });

            model.removeAll(model.createResource(subjectIri), null, null);
        } finally {
            rdfStore.endTransaction(true);
        }
    }

    @Test
    void subgraphDepthOneDoesNotExpandSecondOrderNeighbors() {
        rdfStore.beginTransaction(ReadWrite.WRITE);
        try {
            String subjectIri = "http://example.org/subject";
            String directNeighborIri = "http://example.org/direct";
            String secondOrderIri = "http://example.org/second";
            var model = rdfStore.dataModel();
            model.add(model.createResource(subjectIri), model.createProperty("http://example.org/relatedTo"), model.createResource(directNeighborIri));
            model.add(model.createResource(directNeighborIri), model.createProperty("http://example.org/relatedTo"), model.createResource(secondOrderIri));

            GraphData graph = graphService.subgraph(subjectIri, 1);

            assertThat(graph.nodes()).extracting("id").contains(subjectIri, directNeighborIri).doesNotContain(secondOrderIri);
            assertThat(graph.edges()).extracting("from").contains(subjectIri).doesNotContain(directNeighborIri);
            assertThat(graph.edges()).extracting("to").contains(directNeighborIri).doesNotContain(secondOrderIri);

            model.removeAll(model.createResource(subjectIri), null, null);
            model.removeAll(model.createResource(directNeighborIri), null, null);
            model.removeAll(model.createResource(secondOrderIri), null, null);
        } finally {
            rdfStore.endTransaction(true);
        }
    }
}
