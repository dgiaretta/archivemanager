package info.oais.infomodel.structure.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.RepInfoManifest;

/**
 * A 4 x 3 image of unsigned 16-bit counts, opened through its Representation
 * Information manifest -- decoded with DFDL, or with DRB SDF when that's the
 * only description given -- and written as FITS.
 */
class OaisStructureImageTest {

	private static final long[][] TINY = { { 0, 1, 2, 3 }, { 100, 200, 300, 400 }, { 65535, 40000, 7, 8 } };

	private static DescribedData manifest(String name) throws Exception {
		URI location = OaisStructureImageTest.class.getResource("/" + name).toURI();
		return RepInfoManifest.read(location).select(null);
	}

	private static void assertTiny(DecodedImage image) {
		assertEquals(4, image.width());
		assertEquals(3, image.height());
		for (int r = 0; r < TINY.length; r++) {
			for (int c = 0; c < TINY[r].length; c++) {
				assertEquals(TINY[r][c], image.pixels()[r][c].longValue(), "pixel " + r + "," + c);
			}
		}
	}

	@Test
	void decodesWithDfdl() throws Exception {
		DecodedImage image = OaisStructureImage.open(manifest("tiny.ttl"));
		assertTiny(image);
		assertEquals("Tiny image", image.name());
		assertEquals("ADU", image.unit());
		assertTrue(image.description().startsWith("Counts:"), image.description());
	}

	@Test
	void decodesWithDrbSdf() throws Exception {
		assertTiny(OaisStructureImage.open(manifest("tiny-drb-only.ttl")));
	}

	@Test
	void writesFitsWithItsMeaningInTheHeader() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FitsImageWriter.write(OaisStructureImage.open(manifest("tiny.ttl")), out);
		byte[] fits = out.toByteArray();
		assertEquals(0, fits.length % 2880);
		String header = new String(fits, 0, 2880, StandardCharsets.US_ASCII);
		assertEquals(16, (int) card(header, "BITPIX"));
		assertEquals(4, (int) card(header, "NAXIS1"));
		assertEquals(3, (int) card(header, "NAXIS2"));
		assertEquals(32768, (int) card(header, "BZERO"));
		assertTrue(header.contains("BUNIT   = 'ADU     '"), header);
		assertTrue(header.contains("OBJECT  = 'Tiny image'"), header);
		assertTrue(header.contains("HISTORY Decoded from"), header);
		ByteBuffer data = ByteBuffer.wrap(fits, 2880, 4 * 3 * 2);
		for (long[] row : TINY) {
			for (long expected : row) {
				assertEquals(expected, data.getShort() + 32768);
			}
		}
	}

	@Test
	void explainsAMissingImageView() throws Exception {
		DescribedData tiny = manifest("tiny.ttl");
		DescribedData noView = new DescribedData(tiny.iri(), tiny.name(), tiny.data(), tiny.structures(), List.of(),
				tiny.meanings());
		IOException e = assertThrows(IOException.class, () -> OaisStructureImage.open(noView));
		assertTrue(e.getMessage().contains("im:viewKind \"image\""), e.getMessage());
	}

	@Test
	void convertsFromTheCommandLine(@TempDir Path dir) throws Exception {
		Path out = dir.resolve("tiny.fits");
		ManifestToFits.convert(Path.of(OaisStructureImageTest.class.getResource("/tiny.ttl").toURI()).toString(), out);
		assertEquals(2 * 2880, Files.size(out));
	}

	@Test
	void storesPixelsInTheSmallestTypeThatHoldsThem() {
		assertEquals(new FitsImageWriter.Storage(8, 0, null), storage(Integer.class, 0, 255));
		assertEquals(new FitsImageWriter.Storage(16, 0, null), storage(Integer.class, -1, 300));
		assertEquals(new FitsImageWriter.Storage(16, 32768, null), storage(Integer.class, 0, 65535));
		assertEquals(new FitsImageWriter.Storage(32, 0, null), storage(Long.class, -70000, 1));
		assertEquals(new FitsImageWriter.Storage(32, 1L << 31, null), storage(Long.class, 0, 4_000_000_000L));
		assertEquals(new FitsImageWriter.Storage(64, 0, null), storage(Long.class, -1, 5_000_000_000L));
		assertEquals(-32, FitsImageWriter.storage(image(Float.class, 1.5f, 2f)).bitpix());
		assertEquals(-64, FitsImageWriter.storage(image(Double.class, 1.5, 2.0)).bitpix());
		assertEquals(-64, FitsImageWriter.storage(image(Integer.class, 1, 2.5)).bitpix());
	}

	@Test
	void writesAMissingPixelAsBlank() throws IOException {
		DecodedImage image = image(Integer.class, 1, null);
		FitsImageWriter.Storage storage = FitsImageWriter.storage(image);
		assertEquals(new FitsImageWriter.Storage(16, 0, (long) Short.MIN_VALUE), storage);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FitsImageWriter.write(image, out);
		byte[] fits = out.toByteArray();
		String header = new String(fits, 0, 2880, StandardCharsets.US_ASCII);
		assertEquals(Short.MIN_VALUE, (int) card(header, "BLANK"));
		ByteBuffer data = ByteBuffer.wrap(fits, 2880, 4);
		assertArrayEquals(new short[] { 1, Short.MIN_VALUE }, new short[] { data.getShort(), data.getShort() });
	}

	@Test
	void readsDecodedValuesAsNumbers() {
		assertEquals(7L, OaisStructureImage.number("7"));
		assertEquals(2.5, OaisStructureImage.number(" 2.5 "));
		assertEquals(1, OaisStructureImage.number(true));
		assertNull(OaisStructureImage.number("n/a"));
		assertNull(OaisStructureImage.number(null));
	}

	private static FitsImageWriter.Storage storage(Class<?> type, long min, long max) {
		return FitsImageWriter.storage(image(type, min, max));
	}

	private static DecodedImage image(Class<?> type, Number a, Number b) {
		return new DecodedImage("test", new Number[][] { { a, b } }, type, null, null, List.of());
	}

	/** A header card's numeric value. */
	private static long card(String header, String keyword) {
		for (int i = 0; i < header.length(); i += 80) {
			String card = header.substring(i, i + 80);
			if (card.startsWith(String.format("%-8s=", keyword))) {
				return Long.parseLong(card.substring(10, 30).strip());
			}
		}
		throw new AssertionError(keyword + " isn't in the header");
	}
}
