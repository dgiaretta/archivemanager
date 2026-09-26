package info.oais.archive.manager;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.format.FormatDescriptionRdfService;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the per-field Semantic Representation Information structure
 * {@link FormatDescriptionRdfService#saveToArchive} builds: one
 * {@code im:SemanticRepresentationInformation} individual per field (label,
 * definition, unit), fanned out from the one overall Semantic RI via
 * {@code interpretedUsingRecurse}, with units shared across fields that use
 * the same unit string.
 *
 * <p>The data graph is a persistent TDB2 store shared across test runs (see
 * {@code RdfStore}'s own doc comment) rather than reset per test, so -- the
 * same convention {@code GraphServiceTest} already follows -- every resource
 * this test creates is removed again in a {@code finally} block, regardless
 * of whether the assertions pass.
 */
@SpringBootTest
class FormatDescriptionRdfServiceTest {

    @Autowired
    private FormatDescriptionRdfService rdfService;

    @Autowired
    private RdfStore store;

    @Autowired
    private QueryRunner q;

    @Test
    void savesOnePerFieldSemanticRepresentationInformationWithSharedUnits() {
        FormatDefinition def = new FormatDefinition();
        def.setName("rdf service test format");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.addField(new FormatField("t", FieldType.FLOAT32, null, null,
                "Temperature", "Measured temperature.", "K"));
        def.addField(new FormatField("p", FieldType.FLOAT32, null, null,
                null, "Pressure reading.", "K"));
        def.addField(new FormatField("raw", FieldType.BYTES, 4, null, null, null, null));

        String dataObject;
        store.beginTransaction(ReadWrite.WRITE);
        try {
            dataObject = rdfService.saveToArchive(def, null, Map.of("Test Format", "dummy generated text"));
        } finally {
            store.endTransaction(true);
        }

        Set<String> toClean = new LinkedHashSet<>();
        toClean.add(dataObject);
        try {
            store.beginTransaction(ReadWrite.READ);
            try {
                List<Map<String, String>> top = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?repInfo ?structureRi ?container WHERE {
                          <%s> im:interpretedUsing ?repInfo .
                          ?repInfo im:hasStructureRepresentationInformation ?structureRi ;
                                   im:hasSemanticRepresentationInformation ?container .
                        }
                        """.formatted(dataObject));
                assertThat(top).hasSize(1);
                String container = top.get(0).get("container");
                toClean.add(top.get(0).get("repInfo"));
                toClean.add(top.get(0).get("structureRi"));
                toClean.add(container);

                List<Map<String, String>> perField = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?field ?label ?definition WHERE {
                          <%s> im:interpretedUsingRecurse ?field .
                          ?field rdfs:label ?label .
                          OPTIONAL { ?field skos:definition ?definition . }
                        }
                        """.formatted(container));
                perField.forEach(row -> toClean.add(row.get("field")));
                assertThat(perField).hasSize(3);
                assertThat(perField).extracting(row -> row.get("label"))
                        .containsExactlyInAnyOrder("Temperature", "p", "raw");

                Map<String, String> temperature = perField.stream()
                        .filter(row -> "Temperature".equals(row.get("label"))).findFirst().orElseThrow();
                assertThat(temperature.get("definition")).isEqualTo("Measured temperature.");

                Map<String, String> raw = perField.stream()
                        .filter(row -> "raw".equals(row.get("label"))).findFirst().orElseThrow();
                assertThat(raw.get("definition")).isNull();

                List<Map<String, String>> units = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?field ?unit ?unitLabel WHERE {
                          <%s> im:interpretedUsingRecurse ?field .
                          ?field rico:hasUnitOfMeasurement ?unit .
                          ?unit rdfs:label ?unitLabel .
                        }
                        """.formatted(container));
                units.forEach(row -> toClean.add(row.get("unit")));
                assertThat(units).hasSize(2);
                assertThat(units).extracting(row -> row.get("unitLabel")).containsOnly("K");
                List<String> distinctUnitIris = units.stream().map(row -> row.get("unit")).distinct().toList();
                assertThat(distinctUnitIris)
                        .as("two fields sharing the same unit string should share one UnitOfMeasurement individual")
                        .hasSize(1);
            } finally {
                store.endTransaction(true);
            }
        } finally {
            store.beginTransaction(ReadWrite.WRITE);
            try {
                Model m = store.dataModel();
                for (String iri : toClean) {
                    if (iri == null) {
                        continue;
                    }
                    Resource r = m.createResource(iri);
                    m.removeAll(r, null, null);
                    m.removeAll(null, null, r);
                }
            } finally {
                store.endTransaction(true);
            }
        }
    }
}
