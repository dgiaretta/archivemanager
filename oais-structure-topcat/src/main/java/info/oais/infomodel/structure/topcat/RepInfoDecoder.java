package info.oais.infomodel.structure.topcat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import io.kaitai.struct.KaitaiStruct;
import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureInterpreterFactory;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.drb.DrbFormatSpecification;
import info.oais.infomodel.structure.kaitai.KaitaiFormatSpecification;
import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.StructureDescription;

/**
 * Decodes the data a Representation Information manifest describes into a
 * {@link StructureNode} tree, with the first of its structure descriptions
 * whose engine is available -- in the order DFDL, DRB SDF, Kaitai Struct
 * (whose generated class must be on the classpath), DRB's own format
 * recognition. What the tree is then viewed as -- a table
 * ({@link OaisStructureTableBuilder}), an image (oais-structure-image) -- is
 * up to the caller.
 */
public final class RepInfoDecoder {

	/** The order structure alternatives are tried in. */
	static final List<String> PREFERENCE = StructureDescription.LANGUAGES;

	private RepInfoDecoder() {
	}

	/**
	 * Decodes {@code data}'s bits with its first usable structure description.
	 *
	 * @throws IOException if none can be used here, or reading or decoding fails
	 */
	public static StructureNode decode(DescribedData data, List<Path> temporary) throws IOException {
		FormatSpecification spec = formatSpecification(data, temporary);
		try (InputStream in = data.data().toURL().openStream()) {
			return new StructureInterpreterFactory().create(spec).apply(new DigitalObjectRefImpl(in));
		} catch (StructureInterpretationException | IllegalStateException e) {
			throw new IOException("Failed to interpret " + data.data() + " via " + spec + ": " + e.getMessage(), e);
		}
	}

	/** The first usable structure alternative, as the engine's format specification. */
	static FormatSpecification formatSpecification(DescribedData data, List<Path> temporary) throws IOException {
		Set<SpecificationLanguage> engines = new StructureInterpreterFactory().availableLanguages();
		StructureDescription chosen = data.structure(PREFERENCE, s -> usable(s, engines)).orElseThrow(() ->
				new IOException("None of the structure descriptions the manifest gives for " + data.name()
						+ " can be used here (" + String.join(", ", data.structures().stream()
								.map(s -> s.language() + (s.language().equals(StructureDescription.KAITAI)
										? " " + s.generatedClassName() : "")).toList())
						+ "): add one in a language whose engine is installed, or put the Kaitai Struct class on the "
						+ "classpath"));
		switch (chosen.language()) {
			case StructureDescription.DFDL:
				return new DfdlFormatSpecification(local(chosen.location(), ".dfdl.xsd", temporary).toUri());
			case StructureDescription.DRB_SDF:
				return new DrbFormatSpecification(local(chosen.location(), ".drb.xsd", temporary).toUri());
			case StructureDescription.KAITAI:
				return new KaitaiFormatSpecification(kaitaiClass(chosen.generatedClassName()));
			default:
				String path = data.data().getPath();
				int dot = path == null ? -1 : path.lastIndexOf('.');
				if (dot < 0 || dot == path.length() - 1) {
					throw new IOException("DRB recognises formats by file extension, and " + data.data()
							+ " has none: give a DRB SDF schema instead");
				}
				return DrbFormatSpecification.autoDetect(path.substring(dot + 1));
		}
	}

	private static boolean usable(StructureDescription s, Set<SpecificationLanguage> engines) {
		switch (s.language()) {
			case StructureDescription.DFDL:
				return engines.contains(SpecificationLanguage.DFDL) && s.location() != null;
			case StructureDescription.DRB_SDF:
				return engines.contains(SpecificationLanguage.DRB) && s.location() != null;
			case StructureDescription.DRB:
				return engines.contains(SpecificationLanguage.DRB);
			case StructureDescription.KAITAI:
				if (!engines.contains(SpecificationLanguage.KAITAI_STRUCT) || s.generatedClassName() == null) {
					return false;
				}
				try {
					kaitaiClass(s.generatedClassName());
					return true;
				} catch (IOException e) {
					return false;
				}
			default:
				return false;
		}
	}

	private static Class<? extends KaitaiStruct> kaitaiClass(String name) throws IOException {
		try {
			return Class.forName(name).asSubclass(KaitaiStruct.class);
		} catch (ClassNotFoundException e) {
			throw new IOException("The Kaitai Struct class '" + name + "' isn't on the classpath", e);
		} catch (ClassCastException e) {
			throw new IOException("'" + name + "' isn't a class generated by Kaitai Struct", e);
		}
	}

	/**
	 * {@code location} as a local file: itself if it is one, else a temporary
	 * copy, added to {@code temporary} for the caller to delete afterwards.
	 */
	public static Path local(URI location, String suffix, List<Path> temporary) throws IOException {
		if ("file".equals(location.getScheme())) {
			return Path.of(location);
		}
		Path copy = Files.createTempFile("oais-repinfo-", suffix);
		temporary.add(copy);
		try (InputStream in = location.toURL().openStream()) {
			Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
		}
		return copy;
	}

	/** Deletes the temporary copies {@link #decode} and {@link #local} made. */
	public static void deleteAll(List<Path> temporary) throws IOException {
		for (Path p : temporary) {
			Files.deleteIfExists(p);
		}
	}
}
