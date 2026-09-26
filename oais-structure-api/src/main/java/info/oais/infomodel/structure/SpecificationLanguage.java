package info.oais.infomodel.structure;

/**
 * Which formal language a {@link FormatSpecification} - and hence which
 * engine - a piece of executable Structure Representation Information is
 * expressed in / backed by.
 */
public enum SpecificationLanguage {

	/** OGF Data Format Description Language, executed here via Apache Daffodil. */
	DFDL,

	/** Kaitai Struct's {@code .ksy} language, executed here via a compiled Java parser class. */
	KAITAI_STRUCT,

	/** CNES/GAEL DRB format descriptors, executed here via DRB's node-factory API. */
	DRB,

	/** Anything else a further adapter module wants to register under. */
	OTHER
}
