package info.oais.archive.manager;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
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

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the per-element Semantic Representation Information structure
 * {@link FormatDescriptionRdfService#saveToArchive} builds: one
 * {@code im:SemanticRepresentationInformation} individual per element (label,
 * structural path, definition, unit, scaling, valid range, fill value, concept
 * and code list), nested to mirror the description via
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
        TestFormats.addField(def, "t", PrimitiveType.FLOAT32, null, null,
                "Temperature", "Measured temperature.", "K");
        TestFormats.addField(def, "p", PrimitiveType.FLOAT32, null, null,
                null, "Pressure reading.", "K");
        TestFormats.addField(def, "raw", PrimitiveType.BYTES, 4, null, null, null, null);

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
                          FILTER NOT EXISTS { ?field a im:ViewSpecification }
                        }
                        """.formatted(container));
                perField.forEach(row -> toClean.add(row.get("field")));
                assertThat(perField).hasSize(3);

                // Alongside the fields' semantics: the table view, as a view specification with its text.
                List<Map<String, String>> views = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?view ?kind ?text WHERE {
                          <%s> im:interpretedUsingRecurse ?view .
                          ?view a im:ViewSpecification ; im:viewKind ?kind ; im:specificationText ?text .
                        }
                        """.formatted(container));
                views.forEach(row -> toClean.add(row.get("view")));
                assertThat(views).singleElement().satisfies(v -> {
                    assertThat(v.get("kind")).isEqualTo("table");
                    assertThat(v.get("text")).contains("<rows select=\"self\"/>").contains("name=\"t\" type=\"float\"");
                });
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
            removeAll(toClean);
        }
    }

    @Test
    void nestsSemanticsToMirrorTheDescriptionAndKeepsCodesScalingAndVocabularyLinks() {
        FormatDefinition def = new FormatDefinition();
        def.setName("nested semantics test format");
        FieldDescription kind = new FieldDescription("kind", "kind", PrimitiveType.UINT8, null, null, Occurrence.ONCE,
                new Semantics("Packet kind", null, null, null, null, Map.of("2", "science"), null, null, null, null, null));
        FieldDescription temp = new FieldDescription("temp", "temp", PrimitiveType.UINT16, null, null, Occurrence.ONCE,
                new Semantics("Temperature", "Sensor temperature", "K", URI.create("https://qudt.org/vocab/unit/K"),
                        URI.create("https://example.org/concept/temperature"), Map.of(), new BigDecimal("0.01"),
                        new BigDecimal("200"), "65535", new BigDecimal("150"), new BigDecimal("400")));
        ChoiceDescription body = new ChoiceDescription("body", "body", Expression.parse("kind"),
                List.of(new ChoiceDescription.Branch("2", RecordDescription.of("science", List.of(temp)))),
                Occurrence.ONCE, Semantics.NONE);
        RecordDescription packet = new RecordDescription("packet", "packet", List.of(kind, body), null,
                new Occurrence.Repeated(Expression.parse("1")), Semantics.of("Packet", "One telemetry packet", null));
        def.setRoot(new RecordDescription(FormatDefinition.ROOT_ID, "format", List.of(packet), null, Occurrence.ONCE,
                Semantics.NONE));

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
                List<Map<String, String>> tempRows = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?repInfo ?structureRi ?container ?packetRi ?bodyRi ?branchRi ?tempRi ?scale ?offset ?fill
                               ?min ?max ?concept ?unit ?unitMatch WHERE {
                          <%s> im:interpretedUsing ?repInfo .
                          ?repInfo im:hasStructureRepresentationInformation ?structureRi ;
                                   im:hasSemanticRepresentationInformation ?container .
                          ?container im:interpretedUsingRecurse ?packetRi .
                          ?packetRi bridge:structuralPath "packet" ; im:interpretedUsingRecurse ?bodyRi .
                          ?bodyRi bridge:structuralPath "packet.body" ; im:interpretedUsingRecurse ?branchRi .
                          ?branchRi bridge:structuralPath "packet.body.science" ; im:interpretedUsingRecurse ?tempRi .
                          ?tempRi rdfs:label "Temperature" ; bridge:structuralPath "packet.body.science.temp" ;
                                  bridge:scaleFactor ?scale ; bridge:addOffset ?offset ; bridge:fillValue ?fill ;
                                  bridge:validMin ?min ; bridge:validMax ?max ; bridge:representsConcept ?concept ;
                                  rico:hasUnitOfMeasurement ?unit .
                          ?unit skos:exactMatch ?unitMatch .
                        }
                        """.formatted(dataObject));
                assertThat(tempRows).hasSize(1);
                Map<String, String> t = tempRows.get(0);
                t.forEach((k, v) -> {
                    if (!k.equals("scale") && !k.equals("offset") && !k.equals("fill") && !k.equals("min")
                            && !k.equals("max") && !k.equals("concept") && !k.equals("unitMatch")) {
                        toClean.add(v);
                    }
                });
                assertThat(t.get("scale")).isEqualTo("0.01");
                assertThat(t.get("offset")).isEqualTo("200");
                assertThat(t.get("fill")).isEqualTo("65535");
                assertThat(t.get("min")).isEqualTo("150");
                assertThat(t.get("max")).isEqualTo("400");
                assertThat(t.get("concept")).isEqualTo("https://example.org/concept/temperature");
                assertThat(t.get("unitMatch")).isEqualTo("https://qudt.org/vocab/unit/K");

                List<Map<String, String>> codes = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT ?kindRi ?scheme ?concept ?notation ?meaning WHERE {
                          <%s> im:interpretedUsingRecurse ?kindRi .
                          ?kindRi bridge:structuralPath "packet.kind" ; bridge:hasCodeList ?scheme .
                          ?concept skos:inScheme ?scheme ; skos:notation ?notation ; skos:prefLabel ?meaning .
                        }
                        """.formatted(t.get("packetRi")));
                codes.forEach(row -> {
                    toClean.add(row.get("kindRi"));
                    toClean.add(row.get("scheme"));
                    toClean.add(row.get("concept"));
                });
                assertThat(codes).extracting(row -> row.get("notation") + " = " + row.get("meaning"))
                        .containsExactly("2 = science");
            } finally {
                store.endTransaction(true);
            }
        } finally {
            removeAll(toClean);
        }
    }

    private void removeAll(Set<String> toClean) {
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
