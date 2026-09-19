package info.oais.archive.manager;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.FormatTemplates;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DfdlGeneratorTest {

    private final DfdlGenerator generator = new DfdlGenerator();

    @Test
    void generatesWellFormedXmlWithDfdlAnnotations() throws Exception {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.LITTLE_ENDIAN);
        def.addField(new FormatField("x", FieldType.INT32, null, null, "x ordinate", null));
        def.addField(new FormatField("labelLen", FieldType.UINT8, null, null, "length of label", null));
        def.addField(new FormatField("label", FieldType.ASCII_STRING, 20, ByteOrder.BIG_ENDIAN, "short label", null));

        String xsd = generator.generate(def);

        assertThat(xsd).contains("dfdl:format").contains("dfdl:length").contains("littleEndian");
        assertThatCode(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xsd))))
                .doesNotThrowAnyException();
    }

    @Test
    void returnsNullForLogicalTreeDefinitions() {
        assertThat(generator.generate(FormatTemplates.hdf5())).isNull();
    }

    @Test
    void fitsTemplateGeneratesWellFormedXml() throws Exception {
        String xsd = generator.generate(FormatTemplates.fits());
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xsd)));
    }
}
