package info.oais.infomodel.structure;

/**
 * Representation Information that is not merely descriptive but is itself
 * directly usable: given the input it describes, it can produce the more
 * meaningful representation that the Representation Information promises.
 *
 * <p>[OAIS]: "The information that maps a Data Object into more meaningful
 * concepts so that the Data Object may be understood ... Representation
 * Information in digital forms needs additional Representation Information
 * so its digital forms can be understood over the Long Term."</p>
 *
 * <p>A compiled DFDL schema, a Kaitai-Struct-generated parser class, and a
 * DRB format descriptor are all, in exactly this sense, pieces of
 * Representation Information that happen to be executable: each is a formal
 * description of a bitstream format that a matching engine can run against
 * an actual bitstream to recover its structure. This interface is the seam
 * that lets oaisCore's descriptive {@code RepresentationInformation} model
 * also carry that capability, without oaisCore needing to know anything
 * about DRB, Kaitai Struct or Daffodil.</p>
 *
 * <p>Kept generic (rather than hard-wired to Structure Representation
 * Information) because the same shape fits other kinds of executable
 * Representation Information one might add later - a Semantic Representation
 * Information data dictionary lookup, or an Other Representation Information
 * rendering tool - even though {@link ExecutableStructureRepInfo} is the only
 * specialisation this project provides.</p>
 *
 * @param <I> the type this Representation Information is applied to
 * @param <O> the type of the more meaningful representation it produces
 */
public interface ExecutableRepresentationInformation<I, O> {

	/**
	 * Apply this Representation Information to the given input.
	 *
	 * @param input the input to interpret, e.g. a Digital Object
	 * @return the resulting, more meaningful representation
	 * @throws StructureInterpretationException if the input could not be
	 *                                           interpreted according to this Representation Information
	 */
	O apply(I input);

	/**
	 * Whether this Representation Information is applicable to the given
	 * input at all (e.g. non-null, of a supported concrete type). Adapters
	 * are not required to sniff the actual bytes here - that is
	 * {@link #apply}'s job, and it is expected to fail with a
	 * {@link StructureInterpretationException} if the bytes do not in fact
	 * match.
	 *
	 * @param input the candidate input
	 * @return true if {@link #apply} is worth attempting
	 */
	default boolean supports(I input) {
		return input != null;
	}
}
