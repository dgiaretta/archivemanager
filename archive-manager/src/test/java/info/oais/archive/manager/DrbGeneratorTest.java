package info.oais.archive.manager;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.FormatTemplates;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DrbGeneratorTest {

    private final DrbGenerator generator = new DrbGenerator();

    @Test
    void generatesPythonByteLayoutScaffoldWithFieldAccessors() {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.addField(new FormatField("x", FieldType.INT32, null, null, null, "x ordinate", null));

        String py = generator.generate(def, DrbTarget.PYTHON);

        assertThat(py).contains("class PointRecordNode(WrappedNode)").contains("class PointRecordFactory(DrbFactory)")
                .contains("(\"x\", \"int32\", 4, \"big\", None, \"x ordinate\", None),");
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
    void generatesJavaByteLayoutFieldReference() {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.addField(new FormatField("x", FieldType.INT32, null, null, null, "x ordinate", null));

        String java = generator.generate(def, DrbTarget.JAVA);

        assertThat(java).contains("class PointRecordDrbFields").contains("fr.gael.drb")
                .contains("import info.oais.infomodel.structure.drb.DrbFormatSpecification;")
                .contains("public static final String X = \"x\";")
                .contains("defaultSpecification()");
    }

    @Test
    void generatesJavaLogicalTreeReferenceForHdf5Template() {
        String java = generator.generate(FormatTemplates.hdf5(), DrbTarget.JAVA);

        assertThat(java).contains("class Hdf5LogicalSchemaExampleDrbSchema")
                .contains("/observations/temperature").contains("fr.gael.drb");
    }
}
