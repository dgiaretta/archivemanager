package info.oais.infomodel.structure.semantic;

import info.oais.infomodel.interfaces.SemanticRepInfo;
import info.oais.infomodel.structure.ExecutableRepresentationInformation;
import info.oais.infomodel.structure.StructureNode;

/**
 * A {@link SemanticRepInfo} (oaisCore's marker interface for "the
 * Representation Information that further describes the meaning of the Data
 * Object ... beyond that provided by the Structure Representation
 * Information") which can actually be run against a {@link StructureNode} -
 * the tree an {@link info.oais.infomodel.structure.ExecutableStructureRepInfo}
 * already produced - to recover a more specific, standard shape for it.
 *
 * <p>This is the interoperability seam this project adds one level up from
 * {@code ExecutableStructureRepInfo}: two Digital Objects in completely
 * different formats, decoded by completely different engines (say a
 * DFDL-described sensor log and a Kaitai-described one), can each be given a
 * {@code TableSemanticRepInfo} whose mapping is written purely in terms of
 * {@link StructureNode} - no format- or engine-specific code at all - and
 * downstream code that only knows
 * {@link info.oais.infomodel.interfaces.utility.OaisIfTable} can treat both
 * the same way. The oaisCore shapes this specialises to - {@code OaisIfTable},
 * {@code OaisIfImage}, {@code OaisIfTimeSeries}, {@code OaisIfVector} - are
 * all defined in oaisCore itself, under
 * {@code info.oais.infomodel.interfaces.utility}; this interface only
 * supplies the "run it" capability, the same way
 * {@code ExecutableStructureRepInfo} does for {@code StructureRepInfo}.</p>
 *
 * <p>Because this extends {@link SemanticRepInfo} directly, anything that
 * implements it slots straight into the existing oaisCore object graph like
 * any other {@code SemanticRepInfo}: attachable via
 * {@link info.oais.infomodel.interfaces.RepresentationInformation#putSemanticRepInfo},
 * serialisable through the existing Jackson wiring, and combinable with
 * Structure/Other Representation Information the usual way.</p>
 *
 * @param <V> the specific oaisCore semantic shape this projects a
 *            {@link StructureNode} into, e.g. {@code OaisIfTable}
 */
public interface ExecutableSemanticRepInfo<V>
		extends SemanticRepInfo, ExecutableRepresentationInformation<StructureNode, V> {

	/**
	 * Project the given Structure Representation Information result into
	 * this Semantic Representation Information's shape.
	 *
	 * @param root the root of a StructureNode tree - typically the result of
	 *             calling {@code apply} on an {@code ExecutableStructureRepInfo}
	 * @return a view of {@code root} in this instance's semantic shape
	 * @throws info.oais.infomodel.structure.StructureInterpretationException
	 *         if {@code root} does not match the mapping this instance was
	 *         configured with (e.g. a named child the mapping expects is
	 *         missing)
	 */
	@Override
	V apply(StructureNode root);
}
