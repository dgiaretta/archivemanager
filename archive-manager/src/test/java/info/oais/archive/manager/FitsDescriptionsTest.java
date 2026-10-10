package info.oais.archive.manager;

import info.oais.archive.manager.service.format.DfdlSampleRunner;
import info.oais.archive.manager.service.format.HandWrittenDescriptions;
import info.oais.archive.manager.service.format.KaitaiSampleRunner;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.archive.manager.service.format.WriteBackResult;
import org.junit.jupiter.api.Test;
import uk.ac.starlink.fits.FitsTableWriter;
import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.RowListStarTable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generic FITS descriptions (FITS Standard 4.0) among RepInfo Tools'
 * worked examples, on FITS files written here by hand to the Standard's
 * layout, and by STIL (the table library TOPCAT uses).
 */
class FitsDescriptionsTest {

    private static String example(String id) {
        return HandWrittenDescriptions.examples().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow()
                .text();
    }

    // ---- FITS files, laid out as the Standard says ----

    /** A keyword record with a fixed-format value: right-justified in bytes 11 to 30 (numbers, logicals). */
    static String card(String keyword, String value, String comment) {
        String c = String.format("%-8s= %20s", keyword, value) + (comment == null ? "" : " / " + comment);
        return String.format("%-80s", c);
    }

    /** A keyword record with a string value, starting in byte 11. */
    static String stringCard(String keyword, String value, String comment) {
        String quoted = String.format("'%-8s'", value);
        String c = String.format("%-8s= %-20s", keyword, quoted) + (comment == null ? "" : " / " + comment);
        return String.format("%-80s", c);
    }

    /** A header: its records, END, and blank records to the end of a 2880-byte block. */
    static byte[] header(List<String> cards) {
        StringBuilder sb = new StringBuilder();
        cards.forEach(sb::append);
        sb.append(String.format("%-80s", "END"));
        while (sb.length() % 2880 != 0) {
            sb.append(' ');
        }
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** Data, and zeros to the end of a 2880-byte block. */
    static byte[] data(byte[] data) {
        return java.util.Arrays.copyOf(data, (data.length + 2879) / 2880 * 2880);
    }

    /**
     * A primary 3 x 2 image of 16-bit integers, with 40 COMMENT records so its
     * header takes two blocks; an IMAGE extension of four 32-bit floats; and an
     * ASCII TABLE extension of two rows.
     */
    public static byte[] multiExtension() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.util.List<String> primary = new java.util.ArrayList<>(List.of(card("SIMPLE", "T", "conforms to FITS"),
                card("BITPIX", "16", "16-bit integers"), card("NAXIS", "2", null), card("NAXIS1", "3", null),
                card("NAXIS2", "2", null), stringCard("OBJECT", "Cygnus X-1", null), card("EXTEND", "T", null)));
        for (int i = 0; i < 40; i++) {
            primary.add(String.format("%-80s", "COMMENT   record " + i));
        }
        out.writeBytes(header(primary));
        ByteBuffer pixels = ByteBuffer.allocate(12);
        for (short v : new short[] {1, -2, 300, 4, 5, Short.MIN_VALUE}) {
            pixels.putShort(v);
        }
        out.writeBytes(data(pixels.array()));

        out.writeBytes(header(List.of(stringCard("XTENSION", "IMAGE", "image extension"), card("BITPIX", "-32", null),
                card("NAXIS", "1", null), card("NAXIS1", "4", null), card("PCOUNT", "0", null),
                card("GCOUNT", "1", null), stringCard("BUNIT", "Jy", null))));
        ByteBuffer floats = ByteBuffer.allocate(16);
        for (float v : new float[] {1.5f, -2.25f, Float.NaN, 1e10f}) {
            floats.putFloat(v);
        }
        out.writeBytes(data(floats.array()));

        out.writeBytes(header(List.of(stringCard("XTENSION", "TABLE", "ASCII table"), card("BITPIX", "8", null),
                card("NAXIS", "2", null), card("NAXIS1", "11", null), card("NAXIS2", "2", null),
                card("PCOUNT", "0", null), card("GCOUNT", "1", null), card("TFIELDS", "2", null),
                stringCard("TTYPE1", "NAME", null), card("TBCOL1", "1", null), stringCard("TFORM1", "A5", null),
                stringCard("TTYPE2", "MAG", null), card("TBCOL2", "7", null), stringCard("TFORM2", "F5.2", null))));
        out.writeBytes(data("Vega   0.03Deneb  1.25".getBytes(StandardCharsets.US_ASCII)));
        return out.toByteArray();
    }

    /** A binary table, written by STIL: an empty primary HDU, then a BINTABLE of three rows. */
    public static byte[] binaryTable() throws Exception {
        RowListStarTable table = new RowListStarTable(new ColumnInfo[] {
                new ColumnInfo("NAME", String.class, "star"), new ColumnInfo("FLUX", Double.class, "flux"),
                new ColumnInfo("COUNT", Integer.class, "count")});
        table.addRow(new Object[] {"Vega", 1.5, 7});
        table.addRow(new Object[] {"Deneb", -0.25, 8});
        table.addRow(new Object[] {"Altair", 3.0, 9});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new FitsTableWriter().writeStarTable(table, out);
        return out.toByteArray();
    }

    // ---- What a FITS file holds, from its headers ----

    @Test
    void readsWhatEachHduHoldsFromTheHeadersAlone() throws Exception {
        byte[] fits = multiExtension();
        java.util.List<Long> reads = new java.util.ArrayList<>();
        List<info.oais.archive.manager.service.format.FitsHeaders.Hdu> hdus =
                info.oais.archive.manager.service.format.FitsHeaders.read((offset, length) -> {
                    reads.add(offset);
                    return java.util.Arrays.copyOfRange(fits, (int) Math.min(offset, fits.length),
                            (int) Math.min(offset + length, fits.length));
                });
        assertThat(hdus).extracting(info.oais.archive.manager.service.format.FitsHeaders.Hdu::type)
                .containsExactly("PRIMARY", "IMAGE", "TABLE");
        assertThat(hdus.get(0).isImage()).isTrue();
        assertThat(hdus.get(0).dataBytes()).isEqualTo(12);
        assertThat(hdus.get(1).isImage()).isFalse(); // one axis: not an image
        assertThat(hdus.get(2).isTable()).isTrue();
        // Only header blocks are read -- two for the primary header, one for each extension's, then the end of
        // the file -- the data blocks between them (at 5760 and 11520) are jumped over.
        assertThat(reads).containsExactly(0L, 2880L, 8640L, 14400L, 20160L);

        List<info.oais.archive.manager.service.format.FitsHeaders.Hdu> table =
                info.oais.archive.manager.service.format.FitsHeaders.read((offset, length) -> {
                    byte[] b = assertBinary();
                    return java.util.Arrays.copyOfRange(b, (int) Math.min(offset, b.length),
                            (int) Math.min(offset + length, b.length));
                });
        assertThat(table).extracting(info.oais.archive.manager.service.format.FitsHeaders.Hdu::type)
                .containsExactly("PRIMARY", "BINTABLE");
        assertThat(table.get(1).isTable()).isTrue();
        assertThat(info.oais.archive.manager.service.format.FitsHeaders.read((o, l) -> "not FITS".getBytes())).isEmpty();
    }

    // ---- DFDL ----

    @Test
    void dfdlReadsEveryHduOfAnyFitsFileAndWritesItBack() {
        String dfdl = example("dfdl-fits");
        byte[] fits = multiExtension();
        SampleDecodeResult result = new DfdlSampleRunner().run(dfdl, fits);
        assertThat(result.error()).isNull();
        List<String> rows = result.rows().stream().map(r -> r.name() + "=" + r.value()).toList();
        assertThat(rows).containsSubsequence("keyword=SIMPLE", "keyword=BITPIX", "value=16", "keyword=NAXIS", "value=2",
                "value=3", "value=2", "kind=PRIMARY", "elements=6", "data_bytes=12", "layout=array16",
                "value=1", "value=-2", "value=300", "value=4", "value=5", "value=-32768");
        assertThat(rows).containsSubsequence("kind=IMAGE", "layout=array-32", "value=1.5", "value=-2.25", "value=NaN",
                "value=10000000000");
        assertThat(rows).containsSubsequence("kind=TABLE", "row=Vega   0.03", "row=Deneb  1.25");
        assertThat(result.trailingBytes()).isIn(null, 0L);

        WriteBackResult written = new DfdlSampleRunner().write(dfdl, fits, Map.of());
        assertThat(written.error()).isNull();
        assertThat(written.roundTrip().describe()).startsWith("Identical");

        SampleDecodeResult table = new DfdlSampleRunner().run(dfdl, assertBinary());
        assertThat(table.error()).isNull();
        assertThat(table.rows().stream().map(r -> r.name() + "=" + r.value()).toList())
                .containsSubsequence("kind=PRIMARY", "elements=0", "kind=BINTABLE", "elements=54",
                        // NAME (6A), FLUX (D, 1.5 is 3ff8...), COUNT (J)
                        "row=0x5665676100003ff800000000000000000007", "row=0x44656e656200bfd000000000000000000008",
                        "row=0x416c74616972400800000000000000000009", "heap=0x");
    }

    private static byte[] assertBinary() {
        try {
            return binaryTable();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Kaitai Struct ----

    @Test
    void kaitaiReadsTheSameHdusAndWritesThemBack() {
        String ksy = example("kaitai-fits");
        KaitaiSampleRunner runner = new KaitaiSampleRunner(120);
        byte[] fits = multiExtension();
        SampleDecodeResult result = runner.run(null, ksy, "fits", fits);
        assertThat(result.error()).isNull();
        List<String> values = result.rows().stream().map(SampleDecodeResult.TreeRow::value).toList();
        assertThat(values).containsSubsequence("SIMPLE  ", "BITPIX  ", "16", "NAXIS   ", "2", "1", "-2", "300", "4", "5", "-32768",
                "XTENSION", "1.5", "-2.25", "NaN", "10000000000", "XTENSION", "Vega   0.03", "Deneb  1.25");

        WriteBackResult written = runner.write(ksy, "fits", fits, Map.of());
        assertThat(written.error()).isNull();
        assertThat(written.roundTrip().describe()).startsWith("Identical");

        SampleDecodeResult table = runner.run(null, ksy, "fits", assertBinary());
        assertThat(table.error()).isNull();
        assertThat(table.rows().stream().map(SampleDecodeResult.TreeRow::value).toList())
                .contains("0x5665676100003ff800000000000000000007", "0x416c74616972400800000000000000000009");
    }

    @Test
    void dfdlRefusesRandomGroupsSayingWhy() {
        byte[] groups = java.util.Arrays.copyOf(header(List.of(card("SIMPLE", "T", null), card("BITPIX", "8", null),
                card("NAXIS", "2", null), card("NAXIS1", "0", null), card("NAXIS2", "4", null),
                card("GROUPS", "T", null), card("PCOUNT", "1", null), card("GCOUNT", "1", null))), 2880 * 2);
        SampleDecodeResult result = new DfdlSampleRunner().run(example("dfdl-fits"), groups);
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("random groups");
    }
}
