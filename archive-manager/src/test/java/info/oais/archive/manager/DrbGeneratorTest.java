package info.oais.archive.manager;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.FormatTemplates;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DrbGeneratorTest {

    private final DrbGenerator generator = new DrbGenerator();

    @Test
    void generatesPythonDriverAsTheInterpreterPlusTheDescriptionAsData() {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        TestFormats.addField(def, "x", PrimitiveType.INT32, null, null, null, "x ordinate", null);

        String py = generator.generate(def, DrbTarget.PYTHON);

        assertThat(py).contains("class _FormatNode(WrappedNode)").contains("class PointRecordFactory(DrbFactory)")
                .contains("DESCRIPTION = {\"name\": \"point record\", \"order\": \"big\"")
                .contains("{\"kind\": \"field\", \"name\": \"x\", \"type\": \"int32\", \"length\": None")
                .contains("\"sem\": {\"definition\": \"x ordinate\"}");
    }

    @Test
    void packagesAByteLayoutAsAnInstallableDriverMatchingItsFileExtensions() {
        Map<String, String> pkg = generator.pythonDriverPackage(FormatTemplates.fits());

        assertThat(pkg).containsOnlyKeys("pyproject.toml", "README.md",
                "drb/drivers/am_fits_primary_header/__init__.py",
                "drb/topics/am_fits_primary_header/__init__.py",
                "drb/topics/am_fits_primary_header/cortex.ttl");
        assertThat(pkg.get("pyproject.toml"))
                .contains("name = \"drb-driver-am-fits-primary-header\"")
                .contains("am_fits_primary_header = \"drb.drivers.am_fits_primary_header:FitsPrimaryHeaderDataUnitFactory\"");
        assertThat(pkg.get("drb/topics/am_fits_primary_header/cortex.ttl"))
                .contains("drb:nameMatch \"(?i).+[.](fits|fit|fts)$\"");
        assertThat(generator.pythonDriverPackage(FormatTemplates.hdf5())).isNull();
    }

    @Test
    void registersTheAddOnsAndCarriesTheHandWrittenAddIn() {
        FormatDefinition def = FormatTemplates.fits();
        String id = "am_fits_primary_header";
        Map<String, String> pkg = generator.pythonDriverPackage(def);
        assertThat(pkg.get("pyproject.toml")).contains("[project.entry-points.\"drb.addon\"]")
                .contains(id + "_semantics = \"drb.drivers." + id + ":SemanticsAddon\"")
                .contains(id + "_metadata = \"drb.drivers." + id + ":MetadataAddon\"")
                .contains(id + "_checks = \"drb.drivers." + id + ":ChecksAddon\"");
        String topicId = pkg.get("drb/topics/" + id + "/cortex.ttl").replaceAll("(?s).*drb:id \"([^\"]+)\".*", "$1");
        assertThat(pkg.get("drb/drivers/" + id + "/__init__.py")).contains("ADDON_PREFIX = \"" + id + "\"")
                .contains("TOPIC_ID = \"" + topicId + "\"");
        assertThat(pkg).doesNotContainKey("drb/drivers/" + id + "/addin.py");

        def.setHandWritten(info.oais.infomodel.structure.description.DescriptionLanguage.DRB_PYTHON,
                "def check(root):\n    return []\n", null);
        pkg = generator.pythonDriverPackage(def);
        assertThat(pkg).containsEntry("drb/drivers/" + id + "/addin.py", "def check(root):\n    return []\n");
        assertThat(pkg.get("README.md")).contains("## Hand-written add-in");
    }

    @Test
    void escapesUserTextAsPlainAsciiLiterals() {
        assertThat(DrbGenerator.quotedLiteral("a\"b\\c\ndé😀"))
                .isEqualTo("\"a\\\"b\\\\c\\nd\\u00e9\\U0001f600\"");
    }

    @Test
    void generatesPythonLogicalTreeScaffoldForHdf5Template() {
        String py = generator.generate(FormatTemplates.hdf5(), DrbTarget.PYTHON);

        assertThat(py).contains("SCHEMA = [").contains("\"kind\": \"group\"").contains("\"kind\": \"dataset\"")
                .contains("no HDF5 driver for drb-python is published");
    }

    @Test
    void generatesAWellFormedDrbSdfSchemaForAByteLayout() throws Exception {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.LITTLE_ENDIAN);
        TestFormats.addField(def, "x", PrimitiveType.INT32, null, null, null, "x < y & \"ordinate\"", null);
        TestFormats.addField(def, "raw", PrimitiveType.BYTES, 3, null, null, null, null);

        String xsd = generator.generate(def, DrbTarget.JAVA);

        assertThat(xsd).contains("xmlns:sdf=\"http://www.gael.fr/2004/12/drb/sdf\"")
                .contains("<xs:element name=\"point_record\">")
                .contains("<sdf:length>4</sdf:length><sdf:byteOrder>LSB</sdf:byteOrder>")
                .contains("<xs:documentation>x &lt; y &amp; &quot;ordinate&quot;</xs:documentation>")
                .contains("<xs:element name=\"raw\" type=\"xs:unsignedByte\" maxOccurs=\"unbounded\">")
                .contains("<sdf:occurrence>3</sdf:occurrence>");
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xsd)));
    }

    @Test
    void generatesJavaLogicalTreeReferenceForHdf5Template() {
        String java = generator.generate(FormatTemplates.hdf5(), DrbTarget.JAVA);

        assertThat(java).contains("class Hdf5LogicalSchemaExampleDrbSchema")
                .contains("/observations/temperature").contains("DRB 2.5 has no HDF5 implementation");
    }
}
