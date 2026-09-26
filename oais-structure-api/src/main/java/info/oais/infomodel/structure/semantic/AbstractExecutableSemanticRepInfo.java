package info.oais.infomodel.structure.semantic;

import info.oais.infomodel.implementation.SemanticRepInfoRefImpl;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;

/**
 * Convenience base for {@link ExecutableSemanticRepInfo} implementations,
 * mirroring
 * {@link info.oais.infomodel.structure.AbstractExecutableStructureRepInfo}
 * one layer up: extends oaisCore's own {@link SemanticRepInfoRefImpl} rather
 * than implementing {@link info.oais.infomodel.interfaces.SemanticRepInfo}
 * from scratch, so every executable Semantic RepInfo built on top of this
 * class remains a fully-fledged oaisCore {@code RepresentationInformation}.
 *
 * @param <V> the semantic shape this instance projects a {@link StructureNode}
 *            into
 */
public abstract class AbstractExecutableSemanticRepInfo<V>
		extends SemanticRepInfoRefImpl
		implements ExecutableSemanticRepInfo<V> {

	protected AbstractExecutableSemanticRepInfo() {
		super();
	}

	@Override
	public final V apply(StructureNode root) {
		if (root == null) {
			throw new IllegalArgumentException("root must not be null");
		}
		try {
			return doApply(root);
		} catch (StructureInterpretationException e) {
			throw e;
		} catch (Exception e) {
			throw new StructureInterpretationException(
					"Failed to project a StructureNode using " + getClass().getSimpleName(), e);
		}
	}

	/**
	 * Mapping-specific projection. Free to throw any exception; {@link #apply}
	 * wraps whatever comes out in a {@link StructureInterpretationException}.
	 *
	 * @param root the StructureNode tree to project, never {@code null}
	 * @return the resulting view
	 * @throws Exception on any failure to project {@code root}
	 */
	protected abstract V doApply(StructureNode root) throws Exception;
}
