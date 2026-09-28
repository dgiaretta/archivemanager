package info.oais.archive.manager;

import info.oais.archive.manager.service.format.DfdlSampleRunner;
import info.oais.archive.manager.service.format.DrbSampleRunner;
import info.oais.archive.manager.service.format.HandWrittenDescriptions;
import info.oais.archive.manager.service.format.KaitaiSampleRunner;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.infomodel.structure.description.DescriptionLanguage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hand-writing examples, each run by its real engine on sample data (so
 * what the editor shows is known to work), and the checks that keep a
 * hand-written description to describing data.
 */
class HandWrittenDescriptionsTest {

    private static final String STANZAS = "name: A\nage: 3\n\nname: B\nage: 4\n";

    private static String example(String id) {
        return HandWrittenDescriptions.examples().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow().text();
    }

    private static List<String> values(SampleDecodeResult result) {
        assertThat(result.error()).isNull();
        return result.rows().stream().map(SampleDecodeResult.TreeRow::value).toList();
    }

    @Test
    void everyExamplePassesTheChecks() {
        for (HandWrittenDescriptions.Example e : HandWrittenDescriptions.examples()) {
            assertThat(HandWrittenDescriptions.check(e.language(), e.text())).as(e.id()).isEmpty();
        }
    }

    @Test
    void kaitaiExamplesDecryptCheckAndReadMultiLineRecords() {
        KaitaiSampleRunner runner = new KaitaiSampleRunner(120);
        byte[] note = {'N', 'O', 0x2A, 5, 'h' ^ 0x2A, 'e' ^ 0x2A, 'l' ^ 0x2A, 'l' ^ 0x2A, 'o' ^ 0x2A};
        String ksy = example("kaitai-encrypted-and-checked");
        assertThat(values(runner.run(null, ksy, HandWrittenDescriptions.kaitaiId(ksy), note))).contains("hello");

        byte[] tooLong = note.clone();
        tooLong[3] = 100;
        assertThat(runner.run(null, ksy, "secret_note", tooLong).ok()).isFalse();

        String stanzas = example("kaitai-stanzas");
        assertThat(values(runner.run(null, stanzas, "stanzas", STANZAS.getBytes(StandardCharsets.US_ASCII))))
                .contains("name: A", "age: 3", "name: B", "age: 4");
    }

    @Test
    void dfdlExamplesCheckAChecksumDecompressAndReadMultiLineRecords() throws IOException {
        DfdlSampleRunner runner = new DfdlSampleRunner();
        String checksum = example("dfdl-checksum");
        assertThat(values(runner.run(checksum, new byte[] {'H', 'D', 'R', 1, 2, 3, 6}))).contains("6");
        SampleDecodeResult bad = runner.run(checksum, new byte[] {'H', 'D', 'R', 1, 2, 3, 7});
        assertThat(bad.ok()).isFalse();
        assertThat(bad.error()).contains("The header checksum doesn't match.");

        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write("hello gzip".getBytes(StandardCharsets.US_ASCII));
        }
        byte[] compressed = gz.toByteArray();
        byte[] packed = ByteBuffer.allocate(4 + compressed.length + 1).putInt(compressed.length).put(compressed)
                .put((byte) 9).array();
        SampleDecodeResult unpacked = runner.run(example("dfdl-gzip"), packed);
        assertThat(values(unpacked)).contains("hello gzip", "9");
        assertThat(unpacked.trailingBytes()).isZero();

        assertThat(values(runner.run(example("dfdl-stanzas"), STANZAS.getBytes(StandardCharsets.US_ASCII))))
                .containsSubsequence("name: A", "age: 3", "name: B", "age: 4");
    }

    @Test
    void drbExampleReadsRecordsOfSeveralLines() {
        SampleDecodeResult result = new DrbSampleRunner().run(example("drb-multiline"),
                "Ann\nParis\nBob\nRome\n".getBytes(StandardCharsets.US_ASCII));
        assertThat(values(result)).contains("Ann", "Paris", "Bob", "Rome");
    }

    @Test
    void refusesWhatReachesBeyondDescribingData() {
        String drbJava = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:sdf="http://www.gael.fr/2004/12/drb/sdf">
                  <xs:element name="x" type="xs:string"><xs:annotation><xs:appinfo><sdf:block>
                    <sdf:occurrence query="declare namespace s = '&amp;#106;ava:java.lang.System'; s:exit(0)"/>
                  </sdf:block></xs:appinfo></xs:annotation></xs:element>
                </xs:schema>""";
        assertThat(HandWrittenDescriptions.check(DescriptionLanguage.DRB, drbJava))
                .contains("DRB queries can't call Java ('java:' namespaces) here.");
        String drbDoc = drbJava.replace("declare namespace s = '&amp;#106;ava:java.lang.System'; s:exit(0)",
                "doc ('file:///etc/passwd')");
        assertThat(HandWrittenDescriptions.check(DescriptionLanguage.DRB, drbDoc))
                .anyMatch(p -> p.startsWith("DRB queries can't read files"));

        String doctype = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";
        assertThat(HandWrittenDescriptions.check(DescriptionLanguage.DFDL, doctype))
                .anyMatch(p -> p.startsWith("This isn't well-formed XML"));
        String include = example("dfdl-checksum").replace("/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd",
                "file:///etc/other.xsd");
        assertThat(HandWrittenDescriptions.check(DescriptionLanguage.DFDL, include))
                .anyMatch(p -> p.startsWith("xs:include of 'file:///etc/other.xsd' isn't allowed"));

        String custom = example("kaitai-encrypted-and-checked").replace("process: xor(key)", "process: my_cipher(key)")
                .replace("  endian: be", "  endian: be\n  imports:\n    - /etc/other") + "\n# */";
        List<String> kaitai = HandWrittenDescriptions.check(DescriptionLanguage.KAITAI,
                custom.replace("doc: The XOR key", "doc: The */ XOR key"));
        assertThat(kaitai).anyMatch(p -> p.startsWith("'process: my_cipher(key)' names a custom routine"));
        assertThat(kaitai).anyMatch(p -> p.startsWith("'meta/imports' reads other files"));
        assertThat(kaitai).anyMatch(p -> p.startsWith("'*/' can't appear"));

        assertThat(HandWrittenDescriptions.check(DescriptionLanguage.DRB_PYTHON, "import os"))
                .anyMatch(p -> p.contains("can't be written by hand"));
    }
}
