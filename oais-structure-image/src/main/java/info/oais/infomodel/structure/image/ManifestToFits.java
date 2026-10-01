package info.oais.infomodel.structure.image;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.RepInfoManifest;

/**
 * Writes the image a Representation Information manifest describes as FITS,
 * for an image viewer to open -- Fiji/ImageJ, SAOImage DS9, Aladin, ...:
 *
 * <pre>
 * java -cp ... info.oais.infomodel.structure.image.ManifestToFits manifest.ttl[#name] image.fits
 * </pre>
 *
 * <p>The manifest is a file or a URL; a manifest describing several Data
 * Objects is given the one to use after a {@code #}.</p>
 */
public final class ManifestToFits {

	private ManifestToFits() {
	}

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Usage: ManifestToFits <manifest.ttl[#name] or URL> <output.fits>");
			System.exit(2);
		}
		try {
			Path out = Path.of(args[1]);
			convert(args[0], out);
			System.out.println("Wrote " + out.toAbsolutePath());
		} catch (IOException | RuntimeException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	/** Writes the image {@code manifest} (a path or URL, with an optional {@code #name}) describes to {@code out}. */
	public static void convert(String manifest, Path out) throws IOException {
		int hash = manifest.indexOf('#');
		String location = hash < 0 ? manifest : manifest.substring(0, hash);
		String which = hash < 0 ? null : manifest.substring(hash + 1);
		URI uri = location.matches("(?i)(https?|file):.*") ? URI.create(location) : Path.of(location).toUri();
		DescribedData data = RepInfoManifest.read(uri).select(which);
		DecodedImage image = OaisStructureImage.open(data);
		try (OutputStream os = Files.newOutputStream(out)) {
			FitsImageWriter.write(image, os);
		}
	}
}
