package info.oais.infomodel.structure;

import java.util.Objects;

import info.oais.infomodel.implementation.StructureRepInfoRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;

/**
 * Convenience base for {@link ExecutableStructureRepInfo} implementations.
 *
 * <p>Extends oaisCore's own {@link StructureRepInfoRefImpl} rather than
 * implementing {@link info.oais.infomodel.interfaces.StructureRepInfo} from
 * scratch, so that every executable Structure RepInfo built on top of this
 * class remains a fully-fledged oaisCore {@code RepresentationInformation}:
 * it can carry a {@code RepInfoCategory}, participate in the
 * Structure/Semantic/Other sub-component wiring already defined on
 * {@code RepresentationInformation}, and serialise through the existing
 * Jackson module - all "for free" - while also being directly callable.</p>
 *
 * <p>Subclasses (see the {@code oais-structure-dfdl}, {@code oais-structure-kaitai}
 * and {@code oais-structure-drb} modules) implement {@link #doApply}, which
 * may throw anything; this class takes care of turning that into a
 * {@link StructureInterpretationException} with a consistent message.</p>
 */
public abstract class AbstractExecutableStructureRepInfo
		extends StructureRepInfoRefImpl
		implements ExecutableStructureRepInfo {

	private final FormatSpecification formatSpecification;

	protected AbstractExecutableStructureRepInfo(FormatSpecification formatSpecification) {
		super();
		this.formatSpecification = Objects.requireNonNull(formatSpecification, "formatSpecification");
	}

	@Override
	public FormatSpecification getFormatSpecification() {
		return formatSpecification;
	}

	@Override
	public final StructureNode apply(DigitalObject digitalObject) {
		if (digitalObject == null) {
			throw new IllegalArgumentException("digitalObject must not be null");
		}
		try {
			return doApply(digitalObject);
		} catch (StructureInterpretationException e) {
			throw e;
		} catch (Exception e) {
			throw new StructureInterpretationException(
					"Failed to interpret a DigitalObject as " + formatSpecification.getSpecificationLanguage()
							+ " using " + getClass().getSimpleName(),
					e);
		}
	}

	/**
	 * Engine-specific parsing. Free to throw any exception (including
	 * engine-specific checked exceptions); {@link #apply} wraps whatever
	 * comes out in a {@link StructureInterpretationException}, so
	 * implementations do not need their own top-level try/catch purely for
	 * that purpose.
	 *
	 * @param digitalObject the bitstream to interpret, never {@code null}
	 * @return the root of the resulting structure tree
	 * @throws Exception on any failure to interpret {@code digitalObject}
	 */
	protected abstract StructureNode doApply(DigitalObject digitalObject) throws Exception;
}
