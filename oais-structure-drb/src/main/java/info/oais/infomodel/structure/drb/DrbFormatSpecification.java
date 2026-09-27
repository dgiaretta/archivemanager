package info.oais.infomodel.structure.drb;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;

/**
 * Tells a {@link DrbStructureRepInfo} how DRB should interpret a Digital
 * Object - one of two ways:
 *
 * <ul>
 *   <li>{@link #autoDetect(String)}: let DRB pick one of its built-in
 *       implementations (XML, ...) itself - which DRB does from the file name
 *       extension, so the Digital Object's usual extension must be given;</li>
 *   <li>{@link #DrbFormatSpecification(URI)}: apply a DRB "SDF" (Structured
 *       Data File) schema - an ordinary XML Schema whose elements carry
 *       {@code sdf:block} annotations (length, byte order, encoding,
 *       occurrence, delimiter, ...) describing a binary or text layout,
 *       DRB's declarative counterpart of a DFDL schema.</li>
 * </ul>
 */
public final class DrbFormatSpecification implements FormatSpecification {

	private final URI sdfSchemaLocation;
	private final String fileExtension;

	/** Apply the DRB SDF schema at {@code sdfSchemaLocation} (a local file). */
	public DrbFormatSpecification(URI sdfSchemaLocation) {
		this.sdfSchemaLocation = Objects.requireNonNull(sdfSchemaLocation, "sdfSchemaLocation");
		this.fileExtension = null;
	}

	private DrbFormatSpecification(String fileExtension) {
		this.sdfSchemaLocation = null;
		this.fileExtension = fileExtension;
	}

	/**
	 * Let DRB recognise the format itself, as it would a file with this
	 * extension (e.g. {@code "xml"}) - DRB's resolver goes by extension.
	 */
	public static DrbFormatSpecification autoDetect(String fileExtension) {
		String ext = Objects.requireNonNull(fileExtension, "fileExtension").replaceFirst("^\\.", "");
		if (!ext.matches("[A-Za-z0-9]+")) {
			throw new IllegalArgumentException("Not a file extension: " + fileExtension);
		}
		return new DrbFormatSpecification(ext);
	}

	/** @return the SDF schema to apply, or empty to let DRB recognise the format itself */
	public Optional<URI> sdfSchemaLocation() {
		return Optional.ofNullable(sdfSchemaLocation);
	}

	/** @return for {@link #autoDetect}, the extension DRB recognises the format by */
	public Optional<String> fileExtension() {
		return Optional.ofNullable(fileExtension);
	}

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.DRB;
	}

	@Override
	public Optional<URI> getSpecificationLocation() {
		return sdfSchemaLocation();
	}

	@Override
	public String toString() {
		return sdfSchemaLocation == null
				? "DrbFormatSpecification{autoDetect=." + fileExtension + "}"
				: "DrbFormatSpecification{sdfSchema=" + sdfSchemaLocation + "}";
	}
}
