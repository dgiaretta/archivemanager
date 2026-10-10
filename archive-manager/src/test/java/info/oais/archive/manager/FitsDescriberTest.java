package info.oais.archive.manager;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.DfdlSampleRunner;
import info.oais.archive.manager.service.format.FitsDescriber;
import info.oais.archive.manager.service.format.FitsDictionary;
import info.oais.archive.manager.service.format.FitsHeaders;
import info.oais.archive.manager.service.format.KaitaiGenerator;
import info.oais.archive.manager.service.format.KaitaiSampleRunner;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.archive.manager.service.format.ViewerBundle;
import info.oais.infomodel.structure.description.DescriptionValidator;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static info.oais.archive.manager.FitsDescriptionsTest.card;
import static info.oais.archive.manager.FitsDescriptionsTest.data;
import static info.oais.archive.manager.FitsDescriptionsTest.header;
import static info.oais.archive.manager.FitsDescriptionsTest.stringCard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Descriptions of FITS files in particular, from their headers (see
 * {@link FitsDescriber}), and the FITS keyword dictionary they point to.
 */
class FitsDescriberTest {

    private static FitsHeaders.Reader reader(byte[] bytes) {
        return (offset, length) -> Arrays.copyOfRange(bytes, (int) Math.min(offset, bytes.length),
                (int) Math.min(offset + length, bytes.length));
    }

    private static FormatDescription describe(byte[] fits) throws IOException {
        FormatDefinition def = FitsDescriber.describe("test.fits", reader(fits));
        FormatDescription format = def.toFormatDescription();
        assertThat(DescriptionValidator.validate(format)).isEmpty();
        return format;
    }

    private static ElementDescription child(ElementDescription parent, String name) {
        return ((RecordDescription) parent).children().stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + name + " in " + parent.name()));
    }

    private static List<String> decoded(SampleDecodeResult result) {
        assertThat(result.error()).isNull();
        return result.rows().stream().map(r -> r.name().replaceAll("\\[\\d+]$", "") + "=" + r.value()).toList();
    }

    @Test
    void theDictionaryHasTheStandardsKeywordsAndMatchesIndexedOnes() {
        FitsDictionary d = FitsDictionary.get();
        assertThat(d.entries()).hasSizeGreaterThan(140);
        assertThat(d.lookup("BITPIX").orElseThrow().section()).isEqualTo("4.4.1.1");
        assertThat(d.lookup("NAXIS").orElseThrow().keyword()).isEqualTo("NAXIS");
        assertThat(d.lookup("NAXIS3").orElseThrow().keyword()).isEqualTo("NAXISn");
        assertThat(d.lookup("CD1_2").orElseThrow().keyword()).isEqualTo("CDi_j");
        assertThat(d.lookup("RADESYS").orElseThrow().keyword()).isEqualTo("RADESYSa");
        assertThat(d.lookup("DATE-OBS").orElseThrow().conceptIri()).isEqualTo(FitsDictionary.CONCEPTS + "DATE-OBS");
        assertThat(d.lookup("").orElseThrow().label()).isEqualTo("(blank)");
        assertThat(d.lookup("FILTNAM1")).isEmpty();
        assertThat(d.entries()).allSatisfy(e -> assertThat(e.definition()).isNotBlank());

        org.apache.jena.rdf.model.Model m = d.model();
        assertThat(m.contains(m.getResource(FitsDictionary.CONCEPTS + "NAXISn"),
                m.getProperty("http://www.w3.org/2004/02/skos/core#inScheme"),
                m.getResource(FitsDictionary.SCHEME))).isTrue();
    }

    /** Writes the dictionary as Turtle to the file {@code -Dfits.dictionary.out=...} names, e.g. to import elsewhere. */
    @Test
    void writesTheDictionaryAsTurtle() throws Exception {
        String out = System.getProperty("fits.dictionary.out");
        org.junit.jupiter.api.Assumptions.assumeTrue(out != null);
        try (var stream = Files.newOutputStream(Path.of(out))) {
            FitsDictionary.get().model().write(stream, "TURTLE");
        }
    }

    @Test
    void describesImagesArraysAndAsciiTablesFromTheirHeaders() throws Exception {
        byte[] fits = FitsDescriptionsTest.multiExtension();
        FormatDescription format = describe(fits);
        assertThat(format.root().children()).extracting(ElementDescription::name).containsExactly(
                "hdu1_header", "hdu1_image", "hdu1_fill", "hdu2_header", "hdu2_data", "hdu2_fill",
                "hdu3_header", "hdu3_row", "hdu3_fill");

        // Each keyword record, with its keyword's meaning from the dictionary.
        RecordDescription header = (RecordDescription) child(format.root(), "hdu1_header");
        assertThat(header.children()).extracting(ElementDescription::name).startsWith("simple", "bitpix", "naxis",
                "naxis1", "naxis2", "object", "extend", "comment", "comment_2").contains("end", "fill");
        FieldDescription naxis1 = (FieldDescription) child(header, "naxis1");
        assertThat(naxis1.semantics().semanticName()).isEqualTo("NAXIS1");
        assertThat(naxis1.semantics().conceptUri().toString()).isEqualTo(FitsDictionary.CONCEPTS + "NAXISn");

        // The image's rows of pixels, as BITPIX says; the one-dimensional array's values, with BUNIT.
        FieldDescription pixel = (FieldDescription) child(child(format.root(), "hdu1_image"), "pixel");
        assertThat(pixel.type()).isEqualTo(PrimitiveType.INT16);
        FieldDescription values = (FieldDescription) child(format.root(), "hdu2_data");
        assertThat(values.type()).isEqualTo(PrimitiveType.FLOAT32);
        assertThat(values.semantics().units()).isEqualTo("Jy");

        // The ASCII table's columns at TBCOLn, named by TTYPEn.
        assertThat(((RecordDescription) child(format.root(), "hdu3_row")).children())
                .extracting(ElementDescription::name).containsExactly("name", "gap_2", "mag");
        assertThat(((RecordDescription) child(format.root(), "hdu3_row")).children().get(1).semantics().isEmpty()).isTrue();

        assertThat(ViewerBundle.imageView(format)).contains("name=\"hdu1_image\"", "name=\"pixel\"");
        // The image's rows have no columns, so the table's rows are the table view's.
        assertThat(ViewerBundle.tableView(format)).contains("name=\"hdu3_row\"", "<column name=\"name\"",
                "<column name=\"mag\"");

        List<String> dfdl = decoded(new DfdlSampleRunner().run(format, new DfdlGenerator().generate(format), fits));
        assertThat(dfdl).containsSubsequence("pixel=1", "pixel=-2", "pixel=300", "pixel=4", "pixel=5", "pixel=-32768",
                "hdu2_data=1.5", "hdu2_data=-2.25", "hdu2_data=NaN", "name=Vega ", "mag= 0.03", "name=Deneb",
                "mag= 1.25");
        List<String> kaitai = decoded(new KaitaiSampleRunner(120).run(format, new KaitaiGenerator().generate(format),
                fits));
        assertThat(kaitai).anyMatch(r -> r.endsWith("=-32768")).anyMatch(r -> r.contains("Deneb"));
    }

    @Test
    void namesAndTypesABinaryTablesColumnsFromItsHeader() throws Exception {
        byte[] fits = FitsDescriptionsTest.binaryTable();
        FormatDescription format = describe(fits);
        RecordDescription row = (RecordDescription) child(format.root(), "hdu2_row");
        assertThat(row.children()).extracting(ElementDescription::name).containsExactly("name", "flux", "count");
        assertThat(row.children()).extracting(e -> ((FieldDescription) e).type())
                .containsExactly(PrimitiveType.STRING, PrimitiveType.FLOAT64, PrimitiveType.INT32);
        assertThat(row.children().get(1).semantics().semanticName()).isEqualTo("FLUX");
        assertThat(ViewerBundle.tableView(format)).contains("name=\"hdu2_row\"", "<column name=\"flux\" type=\"double\"");

        List<String> dfdl = decoded(new DfdlSampleRunner().run(format, new DfdlGenerator().generate(format), fits));
        assertThat(dfdl).containsSubsequence("name=Vega", "flux=1.5", "count=7", "name=Deneb", "flux=-0.25", "count=8",
                "name=Altair", "flux=3", "count=9");
    }

    @Test
    void namesRandomGroupsParametersByPtype() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(header(List.of(card("SIMPLE", "T", null), card("BITPIX", "-32", null), card("NAXIS", "3", null),
                card("NAXIS1", "0", null), card("NAXIS2", "2", null), card("NAXIS3", "1", null),
                card("GROUPS", "T", null), card("PCOUNT", "2", null), card("GCOUNT", "2", null),
                stringCard("PTYPE1", "UU", null), card("PSCAL1", "2.0", null), stringCard("PTYPE2", "VV", null))));
        ByteBuffer groups = ByteBuffer.allocate(32);
        for (float v : new float[] {1, 2, 10, 11, 3, 4, 12, 13}) {
            groups.putFloat(v);
        }
        out.writeBytes(data(groups.array()));
        byte[] fits = out.toByteArray();
        FormatDescription format = describe(fits);
        RecordDescription group = (RecordDescription) child(format.root(), "hdu1_group");
        assertThat(group.children()).extracting(ElementDescription::name).containsExactly("uu", "vv", "array");
        assertThat(group.children().get(0).semantics().scale()).isEqualByComparingTo("2");
        assertThat(decoded(new DfdlSampleRunner().run(format, new DfdlGenerator().generate(format), fits)))
                .containsSubsequence("uu=1", "vv=2", "value=10", "value=11", "uu=3", "vv=4", "value=12", "value=13");
        assertThat(ViewerBundle.imageView(format)).isNull();
        assertThat(ViewerBundle.tableView(format)).contains("name=\"hdu1_group\"", "<column name=\"uu\"");
    }

    @Test
    void saysWhyWhenItIsNotFits() {
        assertThatThrownBy(() -> FitsDescriber.describe("x", reader("not FITS".getBytes())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("isn't a FITS file");
        byte[] truncated = Arrays.copyOf(FitsDescriptionsTest.multiExtension(), 2880 * 2 + 4);
        assertThatThrownBy(() -> FitsDescriber.describe("x", reader(truncated)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ends before");
    }

    /**
     * Describes the FITS files in the folder {@code -Dfits.samples=...} names,
     * e.g. the FITS Support Office's samples, and decodes the smaller ones
     * with DFDL and Kaitai Struct. Skipped without it.
     */
    @Test
    void describesSampleFiles() throws Exception {
        String folder = System.getProperty("fits.samples");
        org.junit.jupiter.api.Assumptions.assumeTrue(folder != null);
        try (var files = Files.list(Path.of(folder))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".fits")).sorted().toList()) {
                byte[] fits = Files.readAllBytes(p);
                FormatDescription format = describe(fits);
                StringBuilder sb = new StringBuilder(p.getFileName() + ": ");
                format.root().children().forEach(e -> sb.append(e.name()).append(' '));
                String table = ViewerBundle.tableView(format);
                sb.append("\n  table: ").append(table == null ? "none" : table.replaceAll("(?s).*<rows[^>]*name=\"([^\"]+)\".*", "$1"));
                sb.append(" image: ").append(ViewerBundle.imageView(format) != null);
                String dfdl = new DfdlGenerator().generate(format);
                String ksy = new KaitaiGenerator().generate(format);
                if (fits.length < Long.getLong("fits.samples.max", 1_000_000)) {
                    SampleDecodeResult d = new DfdlSampleRunner().run(format, dfdl, fits);
                    sb.append("\n  DFDL: ").append(d.error() == null ? "ok, trailing " + d.trailingBytes() : d.error());
                    SampleDecodeResult k = new KaitaiSampleRunner(300).run(format, ksy, fits);
                    sb.append("\n  Kaitai: ").append(k.error() == null ? "ok" : k.error());
                }
                System.out.println(sb);
            }
        }
    }
}
