package info.oais.infomodel.structure;

import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.interfaces.StructureRepInfo;

/**
 * A {@link StructureRepInfo} (oaisCore's marker interface for "the
 * Representation Information that imparts information about the arrangement
 * ... of the parts or elements of the Data Object") which can actually be
 * run against a {@link DigitalObject} to recover that arrangement, producing
 * a navigable {@link StructureNode} tree.
 *
 * <p>Because this extends {@link StructureRepInfo} directly, anything that
 * implements it slots straight into the existing oaisCore object graph
 * exactly like any other {@code StructureRepInfo}: it can be attached via
 * {@link info.oais.infomodel.interfaces.RepresentationInformation#putStructureRepInfo},
 * serialised through the existing Jackson wiring
 * (via {@link info.oais.infomodel.implementation.StructureRepInfoRefImpl},
 * which {@link AbstractExecutableStructureRepInfo} extends), and combined
 * with Semantic/Other Representation Information the usual way. The only
 * thing new is that it can also be {@link #apply(DigitalObject) applied}.</p>
 *
 * <p>The three adapter modules in this project - {@code oais-structure-dfdl},
 * {@code oais-structure-kaitai} and {@code oais-structure-drb} - each provide
 * one concrete implementation, backed by Apache Daffodil, a Kaitai Struct
 * generated parser, and DRB respectively. Application code that only depends
 * on {@code oais-structure-api} (and, through it, oaisCore) never needs to
 * know which one it is holding.</p>
 *
 * <p><b>Resource lifetime:</b> callers remain responsible for closing the
 * {@link DigitalObject}'s underlying resource once they are done navigating
 * the returned {@link StructureNode}, regardless of which adapter produced
 * it. Some adapters read the whole object up front and close it before
 * {@link #apply} even returns; others (DRB, in particular) navigate lazily
 * and may still be reading from it while the returned tree is walked - see
 * the specific adapter's documentation if this matters to you. Treating the
 * resource as still potentially open until you are finished with the result
 * is the one assumption that holds for every adapter.</p>
 */
public interface ExecutableStructureRepInfo
		extends StructureRepInfo, ExecutableRepresentationInformation<DigitalObject, StructureNode> {

	/**
	 * Parse the given Digital Object according to this Structure
	 * Representation Information.
	 *
	 * @param digitalObject the bitstream to interpret
	 * @return the root of the resulting structure tree
	 * @throws StructureInterpretationException if {@code digitalObject}
	 *                                           does not match the format this Representation Information describes
	 */
	@Override
	StructureNode apply(DigitalObject digitalObject);

	/**
	 * The engine- and format-specific configuration (schema location,
	 * generated parser class, DRB protocol id, ...) that this instance was
	 * built from.
	 *
	 * @return the format specification
	 */
	FormatSpecification getFormatSpecification();
}
