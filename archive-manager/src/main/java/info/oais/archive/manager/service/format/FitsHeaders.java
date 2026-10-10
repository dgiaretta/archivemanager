package info.oais.archive.manager.service.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the HDUs of a FITS file hold, from their headers alone (FITS Standard
 * 4.0): each header is read in 2880-byte blocks up to its END record, and its
 * data jumped over, sized as the Standard's Eqs. 1, 2 and 4 say -- so only a
 * few blocks of a large file are read. A FITS file describes itself: whether
 * it holds tables or images is in its headers, not in a description of FITS.
 */
public final class FitsHeaders {

    /** FITS's identifier in PRONOM. */
    public static final String PRONOM_ID = "x-fmt/383";

    static final int BLOCK = 2880;
    private static final int MAX_HEADER_BLOCKS = 1000;
    private static final int MAX_HDUS = 1000;

    private FitsHeaders() {
    }

    /** Reads {@code length} bytes from {@code offset}; fewer at the end of the file. */
    public interface Reader {
        byte[] read(long offset, int length) throws IOException;
    }

    /**
     * One HDU.
     *
     * @param type      {@code PRIMARY}, or the XTENSION value: {@code IMAGE}, {@code BINTABLE}, {@code TABLE}, ...
     * @param axes      NAXIS1 ... NAXISn
     * @param groups    whether it holds random groups (GROUPS = T, NAXIS1 = 0)
     * @param name      its EXTNAME, if it has one
     * @param dataBytes the size of its data, without fill
     */
    public record Hdu(String type, int bitpix, long[] axes, boolean groups, String name, long dataBytes) {

        /** A table with at least one row. */
        public boolean isTable() {
            return (type.equals("BINTABLE") || type.equals("TABLE")) && axes.length == 2 && axes[1] > 0;
        }

        /** An image: a primary or IMAGE array of at least two non-empty axes. */
        public boolean isImage() {
            if (groups || !(type.equals("PRIMARY") || type.equals("IMAGE")) || axes.length < 2) {
                return false;
            }
            for (long n : axes) {
                if (n <= 0) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The HDUs of the FITS file {@code reader} reads; empty if it isn't one
     * (it doesn't start with SIMPLE = T). Stops at the end of the file, or at
     * whatever follows the last HDU that isn't another.
     */
    public static List<Hdu> read(Reader reader) throws IOException {
        List<Hdu> hdus = new ArrayList<>();
        long offset = 0;
        while (hdus.size() < MAX_HDUS) {
            Map<String, String> keywords = new LinkedHashMap<>();
            String first = null;
            int blocks = 0;
            boolean ended = false;
            while (!ended && blocks < MAX_HEADER_BLOCKS) {
                byte[] block = reader.read(offset + (long) blocks * BLOCK, BLOCK);
                if (block.length < BLOCK) {
                    return hdus;
                }
                blocks++;
                String text = new String(block, StandardCharsets.US_ASCII);
                for (int i = 0; i < BLOCK; i += 80) {
                    String record = text.substring(i, i + 80);
                    String keyword = record.substring(0, 8).strip();
                    if (first == null) {
                        first = keyword;
                        if (!(keyword.equals("SIMPLE") && hdus.isEmpty()) && !(keyword.equals("XTENSION") && !hdus.isEmpty())) {
                            return hdus;
                        }
                    }
                    if (keyword.equals("END")) {
                        ended = true;
                        break;
                    }
                    if (record.startsWith("= ", 8)) {
                        keywords.putIfAbsent(keyword, value(record.substring(10)));
                    }
                }
            }
            if (!ended) {
                return hdus;
            }
            Hdu hdu = hdu(first, keywords);
            if (hdu == null) {
                return hdus;
            }
            hdus.add(hdu);
            offset += (long) blocks * BLOCK + (hdu.dataBytes() + BLOCK - 1) / BLOCK * BLOCK;
        }
        return hdus;
    }

    private static Hdu hdu(String first, Map<String, String> k) {
        try {
            int bitpix = Integer.parseInt(k.get("BITPIX"));
            int naxis = Integer.parseInt(k.get("NAXIS"));
            long[] axes = new long[naxis];
            for (int i = 0; i < naxis; i++) {
                axes[i] = Long.parseLong(k.get("NAXIS" + (i + 1)));
            }
            boolean extension = first.equals("XTENSION");
            boolean groups = !extension && naxis > 0 && axes[0] == 0 && "T".equals(k.get("GROUPS"));
            long pcount = extension || groups ? Long.parseLong(k.getOrDefault("PCOUNT", "0")) : 0;
            long gcount = extension || groups ? Long.parseLong(k.getOrDefault("GCOUNT", "1")) : 1;
            long elements = naxis == 0 ? 0 : 1;
            for (int i = groups ? 1 : 0; i < naxis; i++) {
                elements *= axes[i];
            }
            long dataBytes = Math.abs(bitpix) / 8 * gcount * (pcount + elements);
            String type = extension ? k.getOrDefault("XTENSION", "").strip() : "PRIMARY";
            return new Hdu(type, bitpix, axes, groups, k.get("EXTNAME"), dataBytes);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A keyword's value: a string without its quotes, or the text before any comment. */
    private static String value(String field) {
        String v = field.strip();
        if (v.startsWith("'")) {
            int end = 1;
            StringBuilder sb = new StringBuilder();
            while (end < v.length()) {
                char c = v.charAt(end);
                if (c == '\'') {
                    if (end + 1 < v.length() && v.charAt(end + 1) == '\'') {
                        sb.append('\'');
                        end += 2;
                        continue;
                    }
                    break;
                }
                sb.append(c);
                end++;
            }
            return sb.toString().strip();
        }
        int slash = v.indexOf('/');
        return (slash < 0 ? v : v.substring(0, slash)).strip();
    }
}
