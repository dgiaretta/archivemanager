package info.oais.infomodel.structure.east;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;

/**
 * An EAST Data Description Record (CCSDS 644.0-B-3): its text, and where it
 * came from if that's known. EAST is ASCII (the first 128 characters of ISO
 * 8859-1); the text is read as UTF-8, which is the same for those.
 */
public final class EastFormatSpecification implements FormatSpecification {

	private final String text;
	private final URI location;

	private EastFormatSpecification(String text, URI location) {
		this.text = Objects.requireNonNull(text, "text");
		this.location = location;
	}

	/** A description given as text. */
	public static EastFormatSpecification ofText(String text) {
		return new EastFormatSpecification(text, null);
	}

	/** A description read from a location now. */
	public static EastFormatSpecification at(URI location) {
		try (InputStream in = location.toURL().openStream()) {
			return new EastFormatSpecification(new String(in.readAllBytes(), StandardCharsets.UTF_8), location);
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read the EAST description at " + location, e);
		}
	}

	public String text() {
		return text;
	}

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.EAST;
	}

	@Override
	public Optional<URI> getSpecificationLocation() {
		return Optional.ofNullable(location);
	}

	@Override
	public String toString() {
		return location == null ? "EastFormatSpecification{text}" : "EastFormatSpecification{" + location + "}";
	}
}
