package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.model.format.Hdf5Node;
import info.oais.archive.manager.model.format.Hdf5NodeKind;

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

        def.addField(card("SIMPLE", "Logical value 'T' if this file conforms to the FITS standard; "
                + "always the first card in the primary header."));
        def.addField(card("BITPIX", "Bits per data value, and its representation: 8 (unsigned byte), "
                + "16/32/64 (signed integer), or -32/-64 (IEEE floating point, the negative sign "
                + "is how FITS distinguishes float from int width here)."));
        def.addField(card("NAXIS", "Number of axes in the data array (0 if this HDU has no data array "
                + "at all, common for a primary HDU used only to hold header metadata)."));
        def.addField(card("NAXIS1", "Length of the first (fastest-varying) axis, e.g. image width in pixels."));
        def.addField(card("NAXIS2", "Length of the second axis, e.g. image height in pixels. "
                + "Remove this card if NAXIS < 2, or add NAXIS3.. for higher dimensionality."));
        def.addField(card("EXTEND", "Logical value 'T' if the file may contain FITS extension HDUs "
                + "after this primary one."));
        def.addField(card("BSCALE", "Linear scaling factor: physical_value = BSCALE * array_value + BZERO. "
                + "Defaults to 1.0 if the card is absent."));
        def.addField(card("BZERO", "Linear scaling zero-point offset (see BSCALE). Defaults to 0.0 if absent; "
                + "commonly 32768 to store unsigned 16-bit data in a signed BITPIX=16 array."));
        def.addField(card("BUNIT", "Physical units of the array values after BSCALE/BZERO scaling is applied "
                + "(e.g. 'Jy/beam', 'counts', 'K')."));
        def.addField(card("END", "Marks the end of the header keyword cards; carries no value itself. "
                + "The header is then padded with blank cards to the next 2880-byte boundary."));
        return def;
    }

    private static FormatField card(String keyword, String meaning) {
        return new FormatField(keyword, FieldType.ASCII_STRING, 80, null, null, meaning, null);
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
