package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A description of one FITS file, from its headers (FITS Standard 4.0): what
 * a description of FITS in general can't say, since a FITS file describes
 * itself. Each HDU's header becomes a record of its keyword records, each
 * pointing to its keyword in the {@link FitsDictionary}; its data becomes
 * <ul>
 * <li>an image's rows -- a repeated record of NAXIS1 pixels, as BITPIX says,
 *     with BUNIT, BSCALE, BZERO and BLANK as their units, scale, offset and
 *     fill value -- and a one-dimensional array's values;</li>
 * <li>a binary table's rows, its columns typed by TFORMn and named, with
 *     their units, scale, offset, fill value and legal range, by TTYPEn,
 *     TUNITn, TSCALn, TZEROn, TNULLn, TLMINn and TLMAXn; then its heap;</li>
 * <li>an ASCII table's rows, its columns at TBCOLn as text, likewise;</li>
 * <li>random groups' parameters, named by PTYPEn, and arrays;</li>
 * </ul>
 * each followed by the fill to a whole number of 2880-byte blocks. All at the
 * top level, in file order, so the first image is the description's image view
 * and the first table its table view (see {@link ViewerBundle}).
 */
public final class FitsDescriber {

    /** FITS in PRONOM, as Representation Information gives it. */
    public static final String REGISTRY_ID = "PRONOM " + FitsHeaders.PRONOM_ID;

    private static final Pattern BINARY_FORM = Pattern.compile("\\s*(\\d*)([LXBIJKAEDCMPQ])(.*)");
    private static final Pattern ASCII_FORM = Pattern.compile("\\s*([AIFED])(\\d+)(?:\\.(\\d+))?\\s*");

    /** Names a generated description can't use: reserved words of Java and Python, which Kaitai Struct compiles to. */
    private static final Set<String> RESERVED = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "var", "yield", "record", "and", "as", "async",
            "await", "def", "del", "elif", "except", "from", "global", "in", "is", "lambda", "nonlocal", "not", "or",
            "pass", "raise", "with", "none");

    private FitsDescriber() {
    }

    /**
     * Describes the FITS file {@code reader} reads, from its headers.
     *
     * @param fileName what it's called, for the description's name
     * @throws IllegalArgumentException if it isn't a FITS file, or its headers say something this can't describe
     */
    public static FormatDefinition describe(String fileName, FitsHeaders.Reader reader) throws IOException {
        List<FitsHeaders.Hdu> hdus = FitsHeaders.read(reader);
        if (hdus.isEmpty()) {
            throw new IllegalArgumentException("This isn't a FITS file: it doesn't start with a primary header "
                    + "(SIMPLE = T ... END) in a 2880-byte block.");
        }
        FitsHeaders.Hdu last = hdus.get(hdus.size() - 1);
        long dataEnd = last.dataOffset() + last.dataBytes();
        if (last.dataBytes() > 0 && reader.read(dataEnd - 1, 1).length == 0) {
            throw new IllegalArgumentException("The file ends before the data of its last HDU does (at byte "
                    + dataEnd + ", as its header says).");
        }
        long padded = last.dataOffset() + blocks(last.dataBytes());
        boolean lastFilled = padded == dataEnd || reader.read(padded - 1, 1).length == 1;

        List<ElementDescription> top = new ArrayList<>();
        StringBuilder notes = new StringBuilder("Described from its headers, by archive-manager's RepInfo Tools "
                + "(FITS Standard 4.0, " + FitsDictionary.STANDARD_URL + "): ")
                .append(hdus.size()).append(hdus.size() == 1 ? " HDU" : " HDUs").append(".\n");
        for (int i = 0; i < hdus.size(); i++) {
            FitsHeaders.Hdu hdu = hdus.get(i);
            String prefix = "hdu" + (i + 1);
            String what = what(hdu, i + 1);
            notes.append("\n").append(prefix).append(": ").append(what);
            top.add(header(hdu, prefix, what));
            top.addAll(data(hdu, prefix, what));
            long fill = blocks(hdu.dataBytes()) - hdu.dataBytes();
            if (fill > 0 && (hdu != last || lastFilled)) {
                top.add(bytes(prefix + "_fill", fill, Semantics.of("Fill", "Fill after the data of " + what
                        + ", to a whole number of 2880-byte blocks.", null)));
            }
        }
        notes.append("\n\nEach header is a record of its keyword records, 80 characters each, with what each keyword "
                + "means from the FITS Standard's dictionary; each table's columns and each image's pixels are typed, "
                + "named and given units, scale, offset and fill value as the header says.");

        FormatDefinition def = new FormatDefinition();
        def.setName("FITS file " + fileName);
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.BIG_ENDIAN);
        def.setFileExtensions("fits, fit, fts");
        def.setNotes(notes.toString());
        def.setFormatRegistryIdentifier(REGISTRY_ID);
        def.setSemanticDictionary(FitsDictionary.SCHEME);
        def.setRoot(new RecordDescription(FormatDefinition.ROOT_ID, "format", top, null, Occurrence.ONCE,
                Semantics.NONE));
        return def;
    }

    private static long blocks(long bytes) {
        return (bytes + FitsHeaders.BLOCK - 1) / FitsHeaders.BLOCK * FitsHeaders.BLOCK;
    }

    /** E.g. "HDU 2, a binary table (EXTNAME 'SCI') of 1 row of 6 columns". */
    private static String what(FitsHeaders.Hdu hdu, int number) {
        String name = hdu.name() == null || hdu.name().isBlank() ? "" : " (EXTNAME '" + hdu.name() + "')";
        String shape = shape(hdu.axes(), hdu.groups() ? 1 : 0);
        return "HDU " + number + ", " + switch (hdu.type()) {
            case "PRIMARY" -> hdu.groups() ? "the primary HDU, random groups" + name + ": "
                    + hdu.keyword("GCOUNT") + " groups of " + hdu.keyword("PCOUNT") + " parameters and a " + shape
                    + " array"
                    : hdu.axes().length == 0 ? "the primary HDU" + name + ", with no data"
                    : "the primary HDU" + name + ", a " + shape + " array";
            case "IMAGE" -> "an image extension" + name + ", a " + shape + " array";
            case "BINTABLE" -> "a binary table" + name + " of " + rows(hdu) + " of " + hdu.keyword("TFIELDS")
                    + " columns";
            case "TABLE" -> "an ASCII table" + name + " of " + rows(hdu) + " of " + hdu.keyword("TFIELDS")
                    + " columns";
            default -> "a " + hdu.type() + " extension" + name;
        };
    }

    private static String rows(FitsHeaders.Hdu hdu) {
        long n = hdu.axes().length > 1 ? hdu.axes()[1] : 0;
        return n + (n == 1 ? " row" : " rows");
    }

    private static String shape(long[] axes, int from) {
        List<String> parts = new ArrayList<>();
        for (int i = from; i < axes.length; i++) {
            parts.add(Long.toString(axes[i]));
        }
        return parts.isEmpty() ? "empty" : String.join(" x ", parts);
    }

    /** The header: its keyword records, each with its keyword's meaning, and the blank records filling its last block. */
    private static RecordDescription header(FitsHeaders.Hdu hdu, String prefix, String what) {
        FitsDictionary dictionary = FitsDictionary.get();
        Names names = new Names();
        List<ElementDescription> records = new ArrayList<>();
        for (String record : hdu.records()) {
            String keyword = record.substring(0, 8).strip();
            java.util.Optional<FitsDictionary.Entry> entry = dictionary.lookup(keyword);
            String definition = entry.map(FitsDictionary.Entry::definition)
                    .orElse("A keyword the FITS Standard doesn't define.");
            String comment = FitsHeaders.recordComment(record);
            if (record.startsWith("= ", 8) && !comment.isEmpty()) {
                definition += " This record's comment: \"" + comment + "\"";
            }
            Semantics s = new Semantics(keyword.isEmpty() ? "(blank)" : keyword, definition, null, null,
                    entry.map(e -> URI.create(e.conceptIri())).orElse(null), Map.of(), null, null, null, null, null);
            records.add(new FieldDescription(ElementDescription.newId(), names.unique(snake(keyword, "blank")),
                    PrimitiveType.STRING, new Expression.IntLiteral(80), null, Occurrence.ONCE, s));
        }
        long fill = hdu.headerBytes() - hdu.records().size() * 80L;
        if (fill > 0) {
            records.add(bytes(names.unique("fill"), fill, Semantics.of("Fill", "Blank records after END, filling "
                    + "the header to a whole number of 2880-byte blocks.", null)));
        }
        return new RecordDescription(ElementDescription.newId(), prefix + "_header", records, null, Occurrence.ONCE,
                Semantics.of("Header of " + what, "The header of " + what + ": " + hdu.records().size()
                        + " keyword records of 80 ASCII characters, the last END, in " + hdu.headerBytes() / 2880
                        + (hdu.headerBytes() == 2880 ? " block." : " blocks."), null));
    }

    /** The data, as its header says it is laid out. */
    private static List<ElementDescription> data(FitsHeaders.Hdu hdu, String prefix, String what) {
        if (hdu.dataBytes() == 0) {
            return List.of();
        }
        long[] axes = hdu.axes();
        return switch (hdu.type()) {
            case "BINTABLE" -> binaryTable(hdu, prefix, what);
            case "TABLE" -> asciiTable(hdu, prefix, what);
            case "PRIMARY", "IMAGE" -> {
                PrimitiveType type = arrayType(hdu.bitpix());
                if (hdu.groups()) {
                    yield List.of(groups(hdu, prefix, what, type));
                }
                Semantics values = arraySemantics(hdu, what, hdu.isImage() ? "Pixel value" : "Array value",
                        axes.length > 1 ? "one row of " + axes[0] + " values per \"" + prefix + "_image\" record" : null);
                if (axes.length == 1) {
                    yield List.of(new FieldDescription(ElementDescription.newId(), prefix + "_data", type, null, null,
                            new Occurrence.Repeated(new Expression.IntLiteral(axes[0])), values));
                }
                long rows = 1;
                for (int i = 1; i < axes.length; i++) {
                    rows *= axes[i];
                }
                FieldDescription pixel = new FieldDescription(ElementDescription.newId(), "pixel", type, null, null,
                        new Occurrence.Repeated(new Expression.IntLiteral(axes[0])), values);
                yield List.of(new RecordDescription(ElementDescription.newId(), prefix + "_image", List.of(pixel), null,
                        new Occurrence.Repeated(new Expression.IntLiteral(rows)), Semantics.of("Image row",
                        "One row of the " + shape(axes, 0) + " array of " + what + ": " + axes[0] + " pixels along "
                                + "axis 1" + (axes.length > 2 ? "; the rows of each plane follow one another" : "")
                                + ".", null)));
            }
            default -> List.of(bytes(prefix + "_data", hdu.dataBytes(), Semantics.of("Data", "The data of " + what
                    + ", not described further: its type isn't one the FITS Standard defines.", null)));
        };
    }

    private static PrimitiveType arrayType(int bitpix) {
        return switch (bitpix) {
            case 8 -> PrimitiveType.UINT8;
            case 16 -> PrimitiveType.INT16;
            case 32 -> PrimitiveType.INT32;
            case 64 -> PrimitiveType.INT64;
            case -32 -> PrimitiveType.FLOAT32;
            case -64 -> PrimitiveType.FLOAT64;
            default -> throw new IllegalArgumentException("BITPIX = " + bitpix + " isn't one the FITS Standard allows.");
        };
    }

    private static String bitpixMeaning(int bitpix) {
        return switch (bitpix) {
            case 8 -> "8-bit unsigned integers";
            case 16, 32, 64 -> bitpix + "-bit two's complement integers";
            case -32 -> "IEEE single-precision floating point";
            default -> "IEEE double-precision floating point";
        };
    }

    /** What an array's values mean: BUNIT, BSCALE, BZERO, BLANK, and its axes' CTYPEn and CUNITn. */
    private static Semantics arraySemantics(FitsHeaders.Hdu hdu, String what, String name, String layout) {
        StringBuilder d = new StringBuilder("The values of the " + shape(hdu.axes(), hdu.groups() ? 1 : 0)
                + " array of " + what + ", " + bitpixMeaning(hdu.bitpix()) + " (BITPIX = " + hdu.bitpix() + ")");
        d.append(layout == null ? "." : ", " + layout + ".");
        for (int i = hdu.groups() ? 2 : 1; i <= hdu.axes().length; i++) {
            String type = hdu.keyword("CTYPE" + i);
            String unit = hdu.keyword("CUNIT" + i);
            if (type != null || unit != null) {
                d.append(" Axis ").append(i).append(": ").append(type == null ? "" : type)
                        .append(unit == null ? "" : " (" + unit + ")").append('.');
            }
        }
        if (hdu.keyword("BSCALE") != null || hdu.keyword("BZERO") != null) {
            d.append(" Physical value = BZERO + BSCALE x stored value.");
        }
        String blank = hdu.bitpix() > 0 ? hdu.keyword("BLANK") : null;
        if (blank != null) {
            d.append(" Undefined where the stored value is BLANK = ").append(blank).append('.');
        }
        String bunit = hdu.keyword("BUNIT");
        return new Semantics(name, d.toString(), bunit, null, null, Map.of(), number(hdu.keyword("BSCALE")),
                number(hdu.keyword("BZERO")), blank, null, null);
    }

    /** Random groups: GCOUNT groups of PCOUNT parameters, named by PTYPEn, and an array. */
    private static RecordDescription groups(FitsHeaders.Hdu hdu, String prefix, String what, PrimitiveType type) {
        long pcount = Long.parseLong(hdu.keyword("PCOUNT"));
        long gcount = Long.parseLong(hdu.keyword("GCOUNT"));
        Names names = new Names();
        List<ElementDescription> children = new ArrayList<>();
        for (int i = 1; i <= pcount; i++) {
            String ptype = hdu.keyword("PTYPE" + i);
            Semantics s = new Semantics(ptype == null ? "Parameter " + i : ptype, "Group parameter " + i + " of "
                    + what + (ptype == null ? "" : ", " + ptype) + "; physical value = PZERO" + i + " + PSCAL" + i
                    + " x stored value.", null, null, null, Map.of(), number(hdu.keyword("PSCAL" + i)),
                    number(hdu.keyword("PZERO" + i)), null, null, null);
            children.add(new FieldDescription(ElementDescription.newId(),
                    names.unique(snake(ptype, "param_" + i)), type, null, null, Occurrence.ONCE, s));
        }
        long elements = 1;
        for (int i = 1; i < hdu.axes().length; i++) {
            elements *= hdu.axes()[i];
        }
        if (elements > 0) {
            // In a record of its own: a group's array isn't a row of an image.
            FieldDescription values = new FieldDescription(ElementDescription.newId(), "value", type, null, null,
                    new Occurrence.Repeated(new Expression.IntLiteral(elements)),
                    arraySemantics(hdu, what, "Array value", "in each group"));
            children.add(new RecordDescription(ElementDescription.newId(), names.unique("array"), List.of(values),
                    null, Occurrence.ONCE, Semantics.of("Group array", "The group's " + shape(hdu.axes(), 1)
                    + " array.", null)));
        }
        return new RecordDescription(ElementDescription.newId(), prefix + "_group", children, null,
                new Occurrence.Repeated(new Expression.IntLiteral(gcount)), Semantics.of("Group", "One of the "
                + gcount + " random groups of " + what + ": its parameters, then its array.", null));
    }

    /** A binary table's rows (columns as TFORMn says) and its heap. */
    private static List<ElementDescription> binaryTable(FitsHeaders.Hdu hdu, String prefix, String what) {
        long width = hdu.axes()[0];
        long rows = hdu.axes()[1];
        int fields = Integer.parseInt(hdu.keyword("TFIELDS").strip());
        Names names = new Names();
        List<ElementDescription> columns = new ArrayList<>();
        long used = 0;
        for (int n = 1; n <= fields; n++) {
            String form = hdu.keyword("TFORM" + n);
            Matcher m = form == null ? null : BINARY_FORM.matcher(form);
            if (m == null || !m.matches()) {
                throw new IllegalArgumentException("TFORM" + n + " = '" + form + "' isn't a binary table column's "
                        + "format (rT, T one of L X B I J K A E D C M P Q).");
            }
            long r = m.group(1).isEmpty() ? 1 : Long.parseLong(m.group(1));
            char t = m.group(2).charAt(0);
            String name = names.unique(snake(hdu.keyword("TTYPE" + n), "col_" + n));
            Semantics s = columnSemantics(hdu, n, what, "binary table", "TFORM" + n + " = '" + form.strip() + "', "
                    + binaryMeaning(r, t), t != 'A' && t != 'L' && t != 'X');
            ElementDescription column = binaryColumn(name, r, t, s);
            long bytes = binaryWidth(r, t);
            used += bytes;
            if (column != null) {
                columns.add(column);
            }
        }
        if (used > width) {
            throw new IllegalArgumentException("The columns' formats (TFORMn) add up to " + used + " bytes a row, "
                    + "more than NAXIS1 = " + width + ".");
        }
        if (used < width) {
            columns.add(bytes(names.unique("spare"), width - used, Semantics.of("Spare", "Bytes at the end of each "
                    + "row that no column uses.", null)));
        }
        List<ElementDescription> out = new ArrayList<>();
        if (rows > 0 && width > 0) {
            out.add(new RecordDescription(ElementDescription.newId(), prefix + "_row", columns, null,
                    new Occurrence.Repeated(new Expression.IntLiteral(rows)), Semantics.of("Table row",
                    "One of the " + rows + " rows of " + what + ", " + width + " bytes (NAXIS1).", null)));
        }
        long pcount = Long.parseLong(hdu.keyword("PCOUNT").strip());
        if (pcount > 0) {
            out.add(bytes(prefix + "_heap", pcount, Semantics.of("Heap", "The supplemental data area of " + what
                    + " (PCOUNT = " + pcount + " bytes): the values of its variable-length array columns, found by "
                    + "their descriptors" + (hdu.keyword("THEAP") != null ? ", from THEAP = " + hdu.keyword("THEAP")
                    : "") + ".", null)));
        }
        return out;
    }

    private static long binaryWidth(long r, char t) {
        return switch (t) {
            case 'L', 'B', 'A' -> r;
            case 'X' -> (r + 7) / 8;
            case 'I' -> 2 * r;
            case 'J', 'E' -> 4 * r;
            case 'K', 'D', 'C', 'P' -> 8 * r;
            default -> 16 * r; // M, Q
        };
    }

    private static String binaryMeaning(long r, char t) {
        String each = switch (t) {
            case 'L' -> "logical (T or F)";
            case 'X' -> "bit";
            case 'B' -> "8-bit unsigned integer";
            case 'I' -> "16-bit integer";
            case 'J' -> "32-bit integer";
            case 'K' -> "64-bit integer";
            case 'A' -> "character";
            case 'E' -> "single-precision floating point";
            case 'D' -> "double-precision floating point";
            case 'C' -> "single-precision complex (real, imaginary)";
            case 'M' -> "double-precision complex (real, imaginary)";
            case 'P' -> "array descriptor (32-bit count and heap offset)";
            default -> "array descriptor (64-bit count and heap offset)";
        };
        if (t == 'A') {
            return r + (r == 1 ? " character" : " characters");
        }
        return r == 1 ? "a " + each : r + " of " + each;
    }

    /** A binary table column as its TFORMn says; null if it has no bytes. */
    private static ElementDescription binaryColumn(String name, long r, char t, Semantics s) {
        if (r == 0) {
            return null;
        }
        PrimitiveType type = switch (t) {
            case 'L' -> PrimitiveType.STRING;
            case 'B' -> PrimitiveType.UINT8;
            case 'I' -> PrimitiveType.INT16;
            case 'J' -> PrimitiveType.INT32;
            case 'K' -> PrimitiveType.INT64;
            case 'E', 'C' -> PrimitiveType.FLOAT32;
            case 'D', 'M' -> PrimitiveType.FLOAT64;
            case 'P' -> PrimitiveType.INT32;
            case 'Q' -> PrimitiveType.INT64;
            default -> null;
        };
        if (t == 'A') {
            return new FieldDescription(ElementDescription.newId(), name, PrimitiveType.STRING,
                    new Expression.IntLiteral(r), null, Occurrence.ONCE, s);
        }
        if (t == 'X') {
            return bytes(name, (r + 7) / 8, s);
        }
        if (t == 'L') {
            FieldDescription f = new FieldDescription(ElementDescription.newId(), name, type,
                    new Expression.IntLiteral(1), null, Occurrence.ONCE, s);
            return r == 1 ? f : f.withOccurrence(new Occurrence.Repeated(new Expression.IntLiteral(r)));
        }
        if (t == 'P' || t == 'Q') {
            List<ElementDescription> parts = List.of(
                    new FieldDescription(ElementDescription.newId(), "count", type, null, null, Occurrence.ONCE,
                            Semantics.of("Element count", "How many elements the array has.", null)),
                    new FieldDescription(ElementDescription.newId(), "heap_offset", type, null, null, Occurrence.ONCE,
                            Semantics.of("Heap offset", "Where its elements start, in bytes from the start of the "
                                    + "heap.", null)));
            RecordDescription descriptor = new RecordDescription(ElementDescription.newId(), name, parts, null,
                    Occurrence.ONCE, s);
            return r == 1 ? descriptor : descriptor.withOccurrence(new Occurrence.Repeated(new Expression.IntLiteral(r)));
        }
        long count = t == 'C' || t == 'M' ? 2 * r : r;
        return new FieldDescription(ElementDescription.newId(), name, type, null, null,
                count == 1 ? Occurrence.ONCE : new Occurrence.Repeated(new Expression.IntLiteral(count)), s);
    }

    /** An ASCII table's rows: its columns at TBCOLn, as text, with any characters between them. */
    private static List<ElementDescription> asciiTable(FitsHeaders.Hdu hdu, String prefix, String what) {
        long width = hdu.axes()[0];
        long rows = hdu.axes()[1];
        int fields = Integer.parseInt(hdu.keyword("TFIELDS").strip());
        record Column(int n, long start, long width, String form) {
        }
        List<Column> columns = new ArrayList<>();
        for (int n = 1; n <= fields; n++) {
            String form = hdu.keyword("TFORM" + n);
            String col = hdu.keyword("TBCOL" + n);
            Matcher m = form == null ? null : ASCII_FORM.matcher(form);
            if (m == null || !m.matches() || col == null) {
                throw new IllegalArgumentException("Column " + n + " of the ASCII table needs TBCOL" + n + " and a "
                        + "TFORM" + n + " of Aw, Iw, Fw.d, Ew.d or Dw.d (it has '" + form + "').");
            }
            columns.add(new Column(n, Long.parseLong(col.strip()) - 1, Long.parseLong(m.group(2)), form.strip()));
        }
        columns.sort(Comparator.comparingLong(Column::start));
        Names names = new Names();
        List<ElementDescription> children = new ArrayList<>();
        long at = 0;
        for (Column c : columns) {
            if (c.start() < at || c.start() + c.width() > width) {
                throw new IllegalArgumentException("Column " + c.n() + " of the ASCII table (TBCOL" + c.n() + ", TFORM"
                        + c.n() + ") overlaps another column or the end of the row.");
            }
            if (c.start() > at) {
                children.add(new FieldDescription(ElementDescription.newId(), names.unique("gap_" + c.n()),
                        PrimitiveType.STRING, new Expression.IntLiteral(c.start() - at), null, Occurrence.ONCE,
                        Semantics.of("Gap", "Characters before column " + c.n() + " that no column uses.", null)));
            }
            String kind = switch (c.form().charAt(0)) {
                case 'A' -> "characters";
                case 'I' -> "a decimal integer";
                case 'F' -> "a fixed-point decimal number";
                default -> "a decimal number with an exponent";
            };
            children.add(new FieldDescription(ElementDescription.newId(),
                    names.unique(snake(hdu.keyword("TTYPE" + c.n()), "col_" + c.n())), PrimitiveType.STRING,
                    new Expression.IntLiteral(c.width()), null, Occurrence.ONCE,
                    columnSemantics(hdu, c.n(), what, "ASCII table", "TFORM" + c.n() + " = '" + c.form() + "', "
                            + kind + " written in " + c.width() + " characters from character " + (c.start() + 1)
                            + " (TBCOL" + c.n() + ")", c.form().charAt(0) != 'A')));
            at = c.start() + c.width();
        }
        if (at < width) {
            children.add(new FieldDescription(ElementDescription.newId(), names.unique("spare"), PrimitiveType.STRING,
                    new Expression.IntLiteral(width - at), null, Occurrence.ONCE,
                    Semantics.of("Spare", "Characters at the end of each row that no column uses.", null)));
        }
        if (rows == 0 || width == 0) {
            return List.of();
        }
        return List.of(new RecordDescription(ElementDescription.newId(), prefix + "_row", children, null,
                new Occurrence.Repeated(new Expression.IntLiteral(rows)), Semantics.of("Table row",
                "One of the " + rows + " rows of " + what + ", " + width + " characters (NAXIS1).", null)));
    }

    /** A column's meaning: TTYPEn, TUNITn, TSCALn, TZEROn, TNULLn, TLMINn, TLMAXn, TDIMn. */
    private static Semantics columnSemantics(FitsHeaders.Hdu hdu, int n, String what, String table, String form,
                                             boolean numeric) {
        String ttype = hdu.keyword("TTYPE" + n);
        StringBuilder d = new StringBuilder("Column " + n + " of " + what + ": " + form + ".");
        String comment = commentOf(hdu, "TTYPE" + n);
        if (comment != null && !comment.isEmpty()) {
            d.append(" ").append(comment.substring(0, 1).toUpperCase(Locale.ROOT)).append(comment.substring(1))
                    .append(comment.endsWith(".") ? "" : ".");
        }
        if (hdu.keyword("TDIM" + n) != null) {
            d.append(" Its elements are an array of dimensions TDIM").append(n).append(" = ")
                    .append(hdu.keyword("TDIM" + n)).append(", the first varying fastest.");
        }
        BigDecimal scale = numeric ? number(hdu.keyword("TSCAL" + n)) : null;
        BigDecimal offset = numeric ? number(hdu.keyword("TZERO" + n)) : null;
        if (scale != null || offset != null) {
            d.append(" Physical value = TZERO").append(n).append(" + TSCAL").append(n).append(" x stored value.");
        }
        String nul = hdu.keyword("TNULL" + n);
        if (nul != null) {
            d.append(" Undefined where the stored value is TNULL").append(n).append(" = ").append(nul).append('.');
        }
        return new Semantics(ttype == null || ttype.isBlank() ? "Column " + n : ttype, d.toString(),
                hdu.keyword("TUNIT" + n), null, null, Map.of(), scale, offset, nul,
                number(hdu.keyword("TLMIN" + n)), number(hdu.keyword("TLMAX" + n)));
    }

    private static String commentOf(FitsHeaders.Hdu hdu, String keyword) {
        for (String record : hdu.records()) {
            if (record.substring(0, 8).strip().equals(keyword)) {
                return FitsHeaders.recordComment(record);
            }
        }
        return null;
    }

    private static FieldDescription bytes(String name, long length, Semantics s) {
        return new FieldDescription(ElementDescription.newId(), name, PrimitiveType.BYTES,
                new Expression.IntLiteral(length), null, Occurrence.ONCE, s);
    }

    /** A FITS number (with D or E exponents); null if absent or not a number. */
    static BigDecimal number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip().replace('D', 'E').replace('d', 'e'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A structural name for {@code text}: lower_snake_case, not a reserved word; {@code otherwise} if none. */
    static String snake(String text, String otherwise) {
        String s = text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (s.isEmpty()) {
            return otherwise;
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "k_" + s;
        }
        return RESERVED.contains(s) ? s + "_value" : s;
    }

    /** Names unique within one record: a second {@code comment} is {@code comment_2}. */
    private static final class Names {
        private final Set<String> used = new HashSet<>();

        String unique(String name) {
            if (used.add(name)) {
                return name;
            }
            for (int i = 2; ; i++) {
                if (used.add(name + "_" + i)) {
                    return name + "_" + i;
                }
            }
        }
    }
}
