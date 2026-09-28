package info.oais.archive.manager;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
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
        TestFormats.addField(def, "x", PrimitiveType.INT32, null, null, null, "x ordinate", null);
        TestFormats.addField(def, "labelLen", PrimitiveType.UINT8, null, null, null, "length of label", null);
        TestFormats.addField(def, "label", PrimitiveType.STRING, 20, ByteOrder.BIG_ENDIAN, null, "short label", null);

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
