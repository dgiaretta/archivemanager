package info.oais.archive.manager;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.KaitaiGenerator;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KaitaiGeneratorTest {

    private final KaitaiGenerator generator = new KaitaiGenerator();

    @Test
    void generatesWellFormedYamlWithFieldsInOrder() {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.addField(new FormatField("x", FieldType.INT32, null, null, "x ordinate", null));
        def.addField(new FormatField("y", FieldType.INT32, null, null, "y ordinate", null));
        def.addField(new FormatField("label", FieldType.ASCII_STRING, 20, null, "short label", null));

        String ksy = generator.generate(def);

        assertThat(ksy).contains("meta:").contains("seq:").contains("endian: be");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = new Yaml().load(ksy);
        assertThat(parsed).containsKey("meta").containsKey("seq");
    }

    @Test
    void returnsNullForLogicalTreeDefinitions() {
        assertThat(generator.generate(FormatTemplates.hdf5())).isNull();
    }

    @Test
    void fitsTemplateGeneratesParseableKsy() {
        String ksy = generator.generate(FormatTemplates.fits());
        Map<String, Object> parsed = new Yaml().load(ksy);
        assertThat(parsed).containsKey("seq");
    }
}
