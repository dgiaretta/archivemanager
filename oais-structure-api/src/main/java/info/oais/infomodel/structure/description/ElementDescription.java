package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.UUID;

/**
 * One element of a {@link FormatDescription}: a {@link FieldDescription}
 * (a single value), a {@link RecordDescription} (a group of elements) or a
 * {@link ChoiceDescription} (one of several records, picked by a
 * discriminator). Engine-neutral: the same description generates Kaitai
 * Struct, DFDL and DRB descriptions, and is lined up against what any of
 * them decodes.
 */
public sealed interface ElementDescription extends Serializable
		permits FieldDescription, RecordDescription, ChoiceDescription {

	/** A stable identifier, unchanged by edits - what an editor refers to an element by. */
	String id();

	/** The structural name: a lower_snake_case identifier, unique among its siblings. */
	String name();

	Occurrence occurrence();

	Semantics semantics();

	/** A fresh {@link #id()} for a new element. */
	static String newId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
