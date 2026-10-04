package info.oais.archive.manager;

import info.oais.archive.manager.service.transform.InfosetBuilder;
import info.oais.archive.manager.service.transform.PropertyCheck;
import info.oais.archive.manager.service.transform.PropertyCheck.Meaning;
import info.oais.archive.manager.service.transform.TransformationException;
import info.oais.archive.manager.service.transform.TransformationMapping;
import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline.SchemaElement;
import info.oais.infomodel.structure.dfdl.DfdlStructureRepInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TransformationMappingTest {

    @TempDir
    Path dir;

    @Test
    void readsAndWritesTheMappingText() {
        TransformationMapping m = TransformationMapping.parse("""
                # positions from the catalogue
                count = count(star)
                for star in star        # one each
                star.ra_rad = star.ra * 0.0174532925 - 1.5
                star.label = "a \\"quoted\\" # not a comment"
                """);
        assertThat(m.rules()).hasSize(4);
        assertThat(m.forEach("star")).contains(new TransformationMapping.ForEach("star", "star"));
        TransformationMapping.Copy copy = (TransformationMapping.Copy) m.value("star.ra_rad").orElseThrow();
        assertThat(copy.scale()).isEqualByComparingTo("0.0174532925");
        assertThat(copy.offset()).isEqualByComparingTo("-1.5");
        assertThat(((TransformationMapping.Constant) m.value("star.label").orElseThrow()).text())
                .isEqualTo("a \"quoted\" # not a comment");
        assertThat(TransformationMapping.parse(m.text())).isEqualTo(m);
        assertThat(m.mapsWithin("star")).isTrue();
        assertThat(m.mapsWithin("sta")).isFalse();

        assertThatThrownBy(() -> TransformationMapping.parse("count = count(star)\nwhat is this"))
                .hasMessageContaining("Line 2");
        assertThatThrownBy(() -> TransformationMapping.parse("a = b\na = c")).hasMessageContaining("second rule");
    }

    @Test
    void transformsTheCatalogueIntoPositionsAndChecksWhatWasKept() throws Exception {
        DfdlStructureRepInfo stars = repInfo("stars", TransformFixtures.STARS);
        DfdlStructureRepInfo positions = repInfo("positions", TransformFixtures.POSITIONS);
        SchemaElement target = DfdlSchemaOutline.read(TransformFixtures.POSITIONS);
        StructureNode source = stars.apply(new DigitalObjectRefImpl(new ByteArrayInputStream(
                TransformFixtures.catalogue())));
        TransformationMapping mapping = TransformationMapping.parse(TransformFixtures.MAPPING);

        InfosetBuilder.Result built = InfosetBuilder.build(target, source, mapping);
        byte[] written = positions.encode(built.infoset());

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(written));
        assertThat(in.readInt()).isEqualTo(3);
        for (Object[] star : TransformFixtures.CATALOGUE) {
            byte[] name = in.readNBytes(12);
            assertThat(new String(name, StandardCharsets.US_ASCII).strip()).isEqualTo(star[0]);
            assertThat(in.readDouble()).isCloseTo(Math.toRadians((Double) star[1]), within(1e-12));
            assertThat(in.readFloat()).isCloseTo(((Double) star[2]).floatValue(), within(1e-6f));
        }
        assertThat(in.available()).isZero();

        StructureNode reread = positions.apply(new DigitalObjectRefImpl(new ByteArrayInputStream(written)));
        Map<String, Meaning> from = Map.of("star.ra", new Meaning(null, "Right ascension", null, null, "deg"),
                "star.dec", new Meaning(null, "Declination", null, null, "deg"));
        Map<String, Meaning> to = Map.of("star.ra_rad", new Meaning(null, "Right ascension", null, null, "rad"),
                "star.dec", new Meaning(null, "Declination", null, null, "deg"));

        PropertyCheck dec = PropertyCheck.check("star.dec", null, mapping, source, reread, target, from, to);
        assertThat(dec.outcome()).isEqualTo(PropertyCheck.Outcome.PRESERVED);
        assertThat(dec.compared()).isEqualTo(3);
        assertThat(dec.detail()).contains("agree within 0.000001 of the value");
        assertThat(dec.label()).isEqualTo("Declination (deg)");

        PropertyCheck exactDec = PropertyCheck.check("star.dec", BigDecimal.ZERO, mapping, source, reread, target,
                from, to);
        assertThat(exactDec.outcome()).isEqualTo(PropertyCheck.Outcome.CHANGED);
        assertThat(exactDec.detail()).matches("Value \\d differs: .*");

        PropertyCheck ra = PropertyCheck.check("star.ra", null, mapping, source, reread, target, from, to);
        assertThat(ra.outcome()).isEqualTo(PropertyCheck.Outcome.NOT_CHECKED);
        assertThat(ra.detail()).contains("units differ");

        PropertyCheck vmag = PropertyCheck.check("star.vmag", null, mapping, source, reread, target, from, to);
        assertThat(vmag.outcome()).isEqualTo(PropertyCheck.Outcome.CHANGED);
        assertThat(vmag.targetPath()).isNull();

        PropertyCheck name = PropertyCheck.check("star.name", null, mapping, source, reread, target, from, to);
        assertThat(name.outcome()).isEqualTo(PropertyCheck.Outcome.PRESERVED);
        assertThat(name.detail()).isEqualTo("All 3 values are the same.");
    }

    @Test
    void saysWhatTheNewFormatIsMissing() throws Exception {
        SchemaElement target = DfdlSchemaOutline.read(TransformFixtures.POSITIONS);
        StructureNode source = repInfo("stars", TransformFixtures.STARS).apply(new DigitalObjectRefImpl(
                new ByteArrayInputStream(TransformFixtures.catalogue())));

        assertThatThrownBy(() -> InfosetBuilder.build(target, source,
                TransformationMapping.parse(TransformFixtures.MAPPING.replace("count = count(star)\n", ""))))
                .isInstanceOf(TransformationException.class)
                .hasMessageContaining("Nothing is mapped to count");
        assertThatThrownBy(() -> InfosetBuilder.build(target, source,
                TransformationMapping.parse(TransformFixtures.MAPPING.replace("for star in star\n", ""))))
                .hasMessageContaining("occurs 3 times here");
        assertThatThrownBy(() -> InfosetBuilder.build(target, source,
                TransformationMapping.parse(TransformFixtures.MAPPING.replace("star.dec = star.dec",
                        "star.dec = star.name"))))
                .hasMessageContaining("isn't a number");
    }

    @Test
    void roundsToFitWholeNumbers() throws Exception {
        SchemaElement target = DfdlSchemaOutline.read(TransformFixtures.POSITIONS);
        StructureNode source = repInfo("stars", TransformFixtures.STARS).apply(new DigitalObjectRefImpl(
                new ByteArrayInputStream(TransformFixtures.catalogue())));
        InfosetBuilder.Result built = InfosetBuilder.build(target, source, TransformationMapping.parse(
                TransformFixtures.MAPPING.replace("count = count(star)", "count = \"2.6\"")));
        assertThat(built.infoset().getDocumentElement().getFirstChild().getTextContent()).isEqualTo("3");
        assertThat(built.notes()).containsExactly("count: values were rounded to whole numbers to fit the new format");
    }

    private DfdlStructureRepInfo repInfo(String name, String schema) throws Exception {
        Path file = Files.writeString(dir.resolve(name + ".dfdl.xsd"), schema);
        return new DfdlStructureRepInfo(new DfdlFormatSpecification(file.toUri()));
    }
}
