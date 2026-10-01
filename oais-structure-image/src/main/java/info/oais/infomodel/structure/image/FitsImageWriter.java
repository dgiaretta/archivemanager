package info.oais.infomodel.structure.image;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a {@link DecodedImage} as a FITS file: a single primary HDU holding a
 * two-dimensional image, which Fiji/ImageJ, SAOImage DS9, Aladin and other
 * image viewers open directly -- with its meaning in the header ({@code BUNIT}
 * for the units, {@code COMMENT} cards for the description, {@code HISTORY}
 * cards for where it came from).
 *
 * <p>Integer pixels are stored in the smallest FITS type that holds them
 * exactly: unsigned 8-bit, 16- or 32-bit (offset with {@code BZERO} for
 * unsigned 16- and 32-bit values, the FITS convention), else 64-bit; floating
 * pixels as 32-bit floats when the image view declares {@code float}, else
 * 64-bit. A missing integer pixel is written as the {@code BLANK} value, a
 * missing floating one as NaN.</p>
 *
 * <p>Rows are written in the order the data holds them, so the first decoded
 * row is FITS row 1, which astronomical viewers (DS9, Aladin, and Fiji when it
 * reads FITS) show at the bottom. Nothing is flipped: the file is a faithful
 * copy of the decoded values.</p>
 */
public final class FitsImageWriter {

	private static final int BLOCK = 2880;
	private static final int CARD = 80;

	private FitsImageWriter() {
	}

	/** How the pixels are stored: FITS BITPIX, and the offset and blank value, if any. */
	record Storage(int bitpix, long bzero, Long blank) {
		boolean floating() {
			return bitpix < 0;
		}
	}

	/** Writes {@code image} to {@code out} as FITS; doesn't close {@code out}. */
	public static void write(DecodedImage image, OutputStream out) throws IOException {
		Storage storage = storage(image);
		DataOutputStream data = new DataOutputStream(new BufferedOutputStream(out, 64 * 1024));
		byte[] header = header(image, storage);
		data.write(header);
		long bytes = 0;
		int size = Math.abs(storage.bitpix()) / 8;
		for (Number[] row : image.pixels()) {
			for (Number pixel : row) {
				writePixel(data, pixel, storage);
				bytes += size;
			}
		}
		data.write(new byte[(int) ((BLOCK - bytes % BLOCK) % BLOCK)]);
		data.flush();
	}

	/** The smallest FITS storage that holds every pixel of {@code image} exactly. */
	static Storage storage(DecodedImage image) {
		boolean floating = isFloating(image.pixelClass());
		boolean missing = false;
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (Number[] row : image.pixels()) {
			for (Number p : row) {
				if (p == null) {
					missing = true;
				} else if (floating || !isIntegral(p)) {
					floating = true;
				} else {
					long v = p.longValue();
					min = Math.min(min, v);
					max = Math.max(max, v);
				}
			}
		}
		if (floating) {
			return new Storage(image.pixelClass() == Float.class ? -32 : -64, 0, null);
		}
		if (min == Long.MAX_VALUE) {
			// No pixel has a value: an image of blanks.
			return new Storage(16, 0, (long) Short.MIN_VALUE);
		}
		// With missing pixels, the storage's lowest value is reserved for BLANK, so the data must stay above it.
		if (!missing && min >= 0 && max <= 255) {
			return new Storage(8, 0, null);
		}
		long reserve = missing ? 1 : 0;
		if (min >= Short.MIN_VALUE + reserve && max <= Short.MAX_VALUE) {
			return new Storage(16, 0, missing ? (long) Short.MIN_VALUE : null);
		}
		if (min >= reserve && max <= 0xFFFFL) {
			return new Storage(16, 1L << 15, missing ? 0L : null);
		}
		if (min >= Integer.MIN_VALUE + reserve && max <= Integer.MAX_VALUE) {
			return new Storage(32, 0, missing ? (long) Integer.MIN_VALUE : null);
		}
		if (min >= reserve && max <= 0xFFFF_FFFFL) {
			return new Storage(32, 1L << 31, missing ? 0L : null);
		}
		return new Storage(64, 0, missing ? Long.MIN_VALUE : null);
	}

	private static boolean isFloating(Class<?> c) {
		return c == Float.class || c == Double.class || c == BigDecimal.class;
	}

	private static boolean isIntegral(Number n) {
		if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long) {
			return true;
		}
		return n instanceof BigInteger b && b.bitLength() < 64;
	}

	private static void writePixel(DataOutputStream out, Number p, Storage s) throws IOException {
		switch (s.bitpix()) {
			case -32 -> out.writeFloat(p == null ? Float.NaN : p.floatValue());
			case -64 -> out.writeDouble(p == null ? Double.NaN : p.doubleValue());
			case 8 -> out.writeByte((int) p.longValue());
			case 16 -> out.writeShort((int) stored(p, s));
			case 32 -> out.writeInt((int) stored(p, s));
			default -> out.writeLong(stored(p, s));
		}
	}

	/** The value as stored: the physical value less BZERO, or the BLANK value for a missing pixel. */
	private static long stored(Number p, Storage s) {
		if (p == null) {
			return s.blank() - s.bzero();
		}
		return p.longValue() - s.bzero();
	}

	static byte[] header(DecodedImage image, Storage storage) {
		List<String> cards = new ArrayList<>();
		cards.add(value("SIMPLE", "T", "conforms to the FITS standard"));
		cards.add(value("BITPIX", Integer.toString(storage.bitpix()), bitpixComment(storage.bitpix())));
		cards.add(value("NAXIS", "2", "an image"));
		cards.add(value("NAXIS1", Integer.toString(image.width()), "pixels per row"));
		cards.add(value("NAXIS2", Integer.toString(image.height()), "rows"));
		if (storage.bzero() != 0) {
			cards.add(value("BZERO", Long.toString(storage.bzero()), "offset for unsigned integers"));
			cards.add(value("BSCALE", "1", null));
		}
		if (storage.blank() != null) {
			cards.add(value("BLANK", Long.toString(storage.blank() - storage.bzero()), "stored value of a missing pixel"));
		}
		cards.add(string("OBJECT", image.name(), null));
		if (image.unit() != null) {
			cards.add(string("BUNIT", image.unit(), "units of the pixel values"));
		}
		cards.add(string("ORIGIN", "oais-structure-image", "OAIS Representation Information to FITS"));
		if (image.description() != null) {
			text("COMMENT", "Pixel values: " + image.description(), cards);
		}
		text("COMMENT", "Rows are in the order the data holds them: its first row is row 1 here.", cards);
		for (String line : image.history()) {
			text("HISTORY", line, cards);
		}
		cards.add(pad("END"));
		int blocks = (cards.size() * CARD + BLOCK - 1) / BLOCK;
		StringBuilder sb = new StringBuilder(blocks * BLOCK);
		cards.forEach(sb::append);
		while (sb.length() < blocks * BLOCK) {
			sb.append(' ');
		}
		return sb.toString().getBytes(StandardCharsets.US_ASCII);
	}

	private static String bitpixComment(int bitpix) {
		return switch (bitpix) {
			case 8 -> "8-bit unsigned integers";
			case -32 -> "32-bit floating point";
			case -64 -> "64-bit floating point";
			default -> bitpix + "-bit integers";
		};
	}

	/** A card with a number or logical value, right-justified in columns 11-30. */
	private static String value(String keyword, String value, String comment) {
		String card = String.format(Locale.ROOT, "%-8s= %20s", keyword, value);
		return pad(comment == null ? card : card + " / " + ascii(comment));
	}

	/** A card with a string value, quoted, its quotes doubled, cut to fit one card. */
	private static String string(String keyword, String value, String comment) {
		String text = ascii(value).replace("'", "''");
		if (text.length() > 68) {
			text = text.substring(0, 68);
			if (text.endsWith("'") && !text.endsWith("''")) {
				text = text.substring(0, 67);
			}
		}
		String card = String.format(Locale.ROOT, "%-8s= '%-8s'", keyword, text);
		return pad(comment == null || card.length() + 3 >= CARD ? card : card + " / " + ascii(comment));
	}

	/** COMMENT or HISTORY cards, wrapped at word boundaries to 72 characters each. */
	private static void text(String keyword, String text, List<String> cards) {
		String rest = ascii(text).strip();
		while (!rest.isEmpty()) {
			int cut = rest.length() <= 72 ? rest.length() : rest.lastIndexOf(' ', 72);
			if (cut <= 0) {
				cut = Math.min(72, rest.length());
			}
			cards.add(pad(String.format(Locale.ROOT, "%-8s%s", keyword, rest.substring(0, cut))));
			rest = rest.substring(cut).strip();
		}
	}

	/** Printable ASCII only, as FITS headers require. */
	private static String ascii(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (char c : s.toCharArray()) {
			sb.append(c >= 0x20 && c < 0x7F ? c : Character.isWhitespace(c) ? ' ' : '?');
		}
		return sb.toString();
	}

	private static String pad(String card) {
		return card.length() >= CARD ? card.substring(0, CARD) : card + " ".repeat(CARD - card.length());
	}
}
