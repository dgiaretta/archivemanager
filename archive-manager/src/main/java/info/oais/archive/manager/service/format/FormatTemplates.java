package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.Hdf5Node;
import info.oais.archive.manager.model.format.Hdf5NodeKind;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;

import java.util.ArrayList;
import java.util.List;

/**
 * Starting points for the format description editor. Real standard
 * semantics where a real fixed standard exists (FITS); a worked example plus
 * an explanation of why there's nothing fixed to template where it doesn't
 * (HDF5 -- see {@link #hdf5()}).
 */
public final class FormatTemplates {

    private FormatTemplates() {
    }

    /**
     * The FITS primary header's required keyword cards (FITS Standard 4.0,
     * https://fits.gsfc.nasa.gov/fits_standard.html), each an 80-byte ASCII
     * card by the standard's own fixed layout, followed by {@code END} and
     * the data array. Real, sourced semantics per keyword -- not placeholders.
     */
    public static FormatDefinition fits() {
        FormatDefinition def = new FormatDefinition();
        def.setName("FITS primary header + data unit");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.setFileExtensions("fits, fit, fts");
        def.setNotes("""
                Every FITS header keyword record ("card") is exactly 80 ASCII bytes, \
                padded with spaces; the header ends at an END card and the whole header \
                is itself padded with further blank cards to a multiple of 2880 bytes \
                (one FITS "block"). NAXIS1/NAXIS2 below cover a 2-D image; NAXIS itself \
                says how many NAXISn cards actually follow -- add or remove NAXISn cards \
                to match before generating, and remove NAXIS2 (etc.) entirely for NAXIS=1 \
                data. After the padded header, the data array itself follows immediately: \
                its total size is (|BITPIX| / 8) * NAXIS1 * NAXIS2 * ... bytes, again \
                padded to a 2880-byte boundary. Values are big-endian throughout, per the \
                standard -- not implementation-defined the way it is for most binary \
                formats.""");

        List<ElementDescription> cards = new ArrayList<>();
        cards.add(card("SIMPLE", "Logical value 'T' if this file conforms to the FITS standard; "
                + "always the first card in the primary header."));
        cards.add(card("BITPIX", "Bits per data value, and its representation: 8 (unsigned byte), "
                + "16/32/64 (signed integer), or -32/-64 (IEEE floating point, the negative sign "
                + "is how FITS distinguishes float from int width here)."));
        cards.add(card("NAXIS", "Number of axes in the data array (0 if this HDU has no data array "
                + "at all, common for a primary HDU used only to hold header metadata)."));
        cards.add(card("NAXIS1", "Length of the first (fastest-varying) axis, e.g. image width in pixels."));
        cards.add(card("NAXIS2", "Length of the second axis, e.g. image height in pixels. "
                + "Remove this card if NAXIS < 2, or add NAXIS3.. for higher dimensionality."));
        cards.add(card("EXTEND", "Logical value 'T' if the file may contain FITS extension HDUs "
                + "after this primary one."));
        cards.add(card("BSCALE", "Linear scaling factor: physical_value = BSCALE * array_value + BZERO. "
                + "Defaults to 1.0 if the card is absent."));
        cards.add(card("BZERO", "Linear scaling zero-point offset (see BSCALE). Defaults to 0.0 if absent; "
                + "commonly 32768 to store unsigned 16-bit data in a signed BITPIX=16 array."));
        cards.add(card("BUNIT", "Physical units of the array values after BSCALE/BZERO scaling is applied "
                + "(e.g. 'Jy/beam', 'counts', 'K')."));
        cards.add(card("END", "Marks the end of the header keyword cards; carries no value itself. "
                + "The header is then padded with blank cards to the next 2880-byte boundary."));
        def.setRoot(new RecordDescription(FormatDefinition.ROOT_ID, "format", cards, null, Occurrence.ONCE,
                Semantics.NONE));
        return def;
    }

    /** One 80-byte header card, named after its keyword, with the keyword as its semantic name. */
    private static FieldDescription card(String keyword, String meaning) {
        return new FieldDescription(ElementDescription.newId(), keyword.toLowerCase(), PrimitiveType.STRING,
                new Expression.IntLiteral(80), null, Occurrence.ONCE, Semantics.of(keyword, meaning, null));
    }

    /**
     * A small made-up spacecraft telemetry stream that uses every structural
     * feature of the editor: a count that sizes a repeat, a coded packet type
     * that chooses the packet's body, a length-prefixed text, a field present
     * only under a condition, arithmetic in a length, and scaled physical
     * values with units, valid ranges, fill values and code lists.
     */
    public static FormatDefinition telemetry() {
        FormatDefinition def = new FormatDefinition();
        def.setName("Telemetry packets (example)");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.setFileExtensions("tlm, bin");
        def.setNotes("""
                A worked example, not a real standard: a packet count, then that many \
                packets. Each packet starts with a type code that selects its body -- \
                housekeeping (a scaled bus voltage, a status code and a status message) \
                or science (a scaled temperature and a run of detector samples) -- and \
                ends with a checksum present only for science packets. Replace it with \
                your own layout, or use it to see how counts, choices, conditions and \
                semantics come out in each generated description.""");

        FieldDescription count = field("packet_count", PrimitiveType.UINT16, null,
                Semantics.of("Packet count", "Number of packets that follow in this file.", null));
        FieldDescription type = field("packet_type", PrimitiveType.UINT8, null,
                new Semantics("Packet type", "Selects the layout of the packet body.", null, null, null,
                        codes("1", "housekeeping", "2", "science"), null, null, null, null, null));

        RecordDescription housekeeping = RecordDescription.of("housekeeping", List.of(
                field("bus_voltage", PrimitiveType.UINT16, null,
                        new Semantics("Bus voltage", "Main power bus voltage.", "V",
                                java.net.URI.create("http://qudt.org/vocab/unit/V"), null, java.util.Map.of(),
                                new java.math.BigDecimal("0.001"), null, null, new java.math.BigDecimal("0"),
                                new java.math.BigDecimal("40"))),
                field("status", PrimitiveType.UINT8, null,
                        new Semantics("Subsystem status", null, null, null, null,
                                codes("0", "nominal", "1", "warning", "2", "fault"), null, null, null, null, null)),
                field("message_length", PrimitiveType.UINT8, null,
                        Semantics.of(null, "Length in bytes of the status message.", "byte")),
                field("message", PrimitiveType.STRING, Expression.parse("message_length"),
                        Semantics.of("Status message", "Free-text status report (ASCII).", null))));

        RecordDescription science = RecordDescription.of("science", List.of(
                field("temperature", PrimitiveType.UINT16, null,
                        new Semantics("Detector temperature", "Temperature of the detector focal plane.", "K",
                                java.net.URI.create("http://qudt.org/vocab/unit/K"), null, java.util.Map.of(),
                                new java.math.BigDecimal("0.01"), new java.math.BigDecimal("0"), "65535",
                                new java.math.BigDecimal("0"), new java.math.BigDecimal("400"))),
                field("sample_pairs", PrimitiveType.UINT8, null,
                        Semantics.of(null, "Number of (x, y) sample pairs that follow.", null)),
                field("samples", PrimitiveType.INT16, null,
                        Semantics.of("Detector samples", "Raw detector counts, x and y interleaved.", "count"))
                        .withOccurrence(new Occurrence.Repeated(Expression.parse("sample_pairs * 2")))));

        ChoiceDescription body = new ChoiceDescription(ElementDescription.newId(), "body",
                Expression.parse("packet_type"),
                List.of(new ChoiceDescription.Branch("1", housekeeping), new ChoiceDescription.Branch("2", science)),
                Occurrence.ONCE, Semantics.NONE);
        FieldDescription checksum = field("checksum", PrimitiveType.UINT16, null,
                Semantics.of("Checksum", "CRC-16 of the science body; present only in science packets.", null))
                .withOccurrence(new Occurrence.Optional(Expression.parse("packet_type = 2")));

        RecordDescription packet = RecordDescription.of("packet", List.of(type, body, checksum))
                .withOccurrence(new Occurrence.Repeated(Expression.parse("packet_count")))
                .withSemantics(Semantics.of("Telemetry packet", "One housekeeping or science packet.", null));
        def.setRoot(new RecordDescription(FormatDefinition.ROOT_ID, "format", List.of(count, packet), null,
                Occurrence.ONCE, Semantics.NONE));
        return def;
    }

    /**
     * A delimited-text table: one comma-separated row per line, repeated to
     * the end of the file, with units and meanings on its columns.
     */
    public static FormatDefinition csv() {
        FormatDefinition def = new FormatDefinition();
        def.setName("Weather station readings (CSV example)");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.setFileExtensions("csv");
        def.setNotes("""
                A worked example of delimited text: no header line, one reading per line, \
                fields separated by commas and each line ended by a newline. Change the \
                columns to match your files; numbers are read as text and converted.""");
        RecordDescription reading = RecordDescription.of("reading", List.of(
                field("station", PrimitiveType.STRING, null,
                        Semantics.of("Station identifier", "Code of the station that took the reading.", null)),
                field("day", PrimitiveType.INT32, null,
                        Semantics.of("Day of year", "Day of the year the reading was taken (1 = 1 January).", "d")),
                field("temperature", PrimitiveType.INT32, null,
                        new Semantics("Air temperature", "Air temperature 2 m above ground, in tenths of a degree.",
                                "Cel", java.net.URI.create("http://qudt.org/vocab/unit/DEG_C"), null,
                                java.util.Map.of(), new java.math.BigDecimal("0.1"), null, "-9999",
                                new java.math.BigDecimal("-90"), new java.math.BigDecimal("60")))))
                .withText(RecordDescription.TextLayout.CSV)
                .withOccurrence(new Occurrence.UntilEnd())
                .withSemantics(Semantics.of("Reading", "One station reading.", null));
        def.setRoot(new RecordDescription(FormatDefinition.ROOT_ID, "format", List.of(reading), null,
                Occurrence.ONCE, Semantics.NONE));
        return def;
    }

    private static FieldDescription field(String name, PrimitiveType type, Expression length, Semantics semantics) {
        return new FieldDescription(ElementDescription.newId(), name, type, length, null, Occurrence.ONCE, semantics);
    }

    /** Code-to-meaning pairs, in the order given. */
    private static java.util.Map<String, String> codes(String... pairs) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    /**
     * HDF5 has no single fixed schema the way FITS's primary header does --
     * every file's actual group/dataset/attribute layout is domain-specific.
     * This is a worked example to replace with the real tree, not a standard
     * to reproduce.
     */
    public static FormatDefinition hdf5() {
        FormatDefinition def = new FormatDefinition();
        def.setName("HDF5 logical schema (example)");
        def.setKind(FormatDefinitionKind.LOGICAL_TREE);
        def.setFileExtensions("h5, hdf5");
        def.setNotes("""
                HDF5 is a self-describing container format -- there is no fixed universal \
                schema to template against the way there is for FITS's primary header. \
                The rows below are a worked example (one Group, one Dataset inside it, one \
                Attribute on that Dataset); replace them with the actual group/dataset/attribute \
                tree your files use. Byte-level Kaitai/DFDL descriptions don't apply here -- \
                describe a dataset's own raw array layout as a separate byte-layout definition \
                if you specifically need one.""");
        def.addNode(new Hdf5Node(Hdf5NodeKind.GROUP, "/observations", null, null,
                "Observations", "Top-level group holding one instrument's observations.", null));
        def.addNode(new Hdf5Node(Hdf5NodeKind.DATASET, "/observations/temperature", "float64", java.util.List.of(100, 200),
                "Temperature", "Measured temperature, one value per (time, sensor) cell.", "K"));
        def.addNode(new Hdf5Node(Hdf5NodeKind.ATTRIBUTE, "/observations/temperature@units", "string", null,
                "Units attribute", "Physical units of the temperature dataset's values, e.g. 'K'.", null));
        return def;
    }
}
