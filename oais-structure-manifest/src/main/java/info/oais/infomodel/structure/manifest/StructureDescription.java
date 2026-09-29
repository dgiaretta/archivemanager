package info.oais.infomodel.structure.manifest;

import java.net.URI;
import java.util.List;

/**
 * Structure Representation Information naming a machine-readable description
 * of the data's structure, in a specification language an engine applies.
 *
 * @param iri                its IRI in the manifest
 * @param language           {@link #DFDL}, {@link #KAITAI}, {@link #DRB_SDF} or {@link #DRB}
 * @param location           the description file; null for {@link #DRB}, which recognises the format itself
 * @param generatedClassName for {@link #KAITAI}: the compiled class generated from the {@code .ksy}, which must be on
 *                           the classpath; otherwise null
 */
public record StructureDescription(String iri, String language, URI location, String generatedClassName) {

	/** A DFDL schema, applied by a DFDL processor such as Apache Daffodil. */
	public static final String DFDL = "DFDL";
	/** A Kaitai Struct description, applied through the class compiled from it. */
	public static final String KAITAI = "Kaitai Struct";
	/** A DRB SDF schema, applied by GAEL's Java DRB. */
	public static final String DRB_SDF = "DRB SDF";
	/** No description: GAEL's Java DRB recognises the format itself, from the data file's extension. */
	public static final String DRB = "DRB";

	/** The languages this manifest format knows, in a sensible default order of preference. */
	public static final List<String> LANGUAGES = List.of(DFDL, DRB_SDF, KAITAI, DRB);
}
