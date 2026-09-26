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

        assertThat(py).contains("class PointRecordNode").contains("FIELDS = [").contains("def x(self)")
                .contains("drb-python").contains("STARTING SCAFFOLD");
    }

    @Test
    void generatesPythonLogicalTreeScaffoldForHdf5Template() {
        String py = generator.generate(FormatTemplates.hdf5(), DrbTarget.PYTHON);

        assertThat(py).contains("SCHEMA = [").contains("\"kind\": \"group\"").contains("\"kind\": \"dataset\"")
                .contains("drb-driver-hdf5");
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
