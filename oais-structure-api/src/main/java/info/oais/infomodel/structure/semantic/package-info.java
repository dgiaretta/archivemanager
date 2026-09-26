/**
 * Semantic Representation Information built on top of this project's
 * {@link info.oais.infomodel.structure.StructureNode} - the interoperability
 * layer described in the {@code oais-structure-adapters} root README: given a
 * StructureNode tree (already engine-agnostic - the same shape whether DFDL,
 * Kaitai Struct or DRB produced it), an
 * {@link info.oais.infomodel.structure.semantic.ExecutableSemanticRepInfo}
 * projects it into one of oaisCore's own semantic shapes
 * ({@code OaisIfTable}, {@code OaisIfImage}, {@code OaisIfTimeSeries},
 * {@code OaisIfVector}, all under {@code info.oais.infomodel.interfaces.utility}),
 * via a declarative mapping ({@link info.oais.infomodel.structure.semantic.TableMapping}
 * and friends) written purely against {@code StructureNode}.
 *
 * <p>Because that mapping never mentions DFDL, Kaitai Struct or DRB, the same
 * mapping - and therefore the same downstream code, which only needs to know
 * the resulting {@code OaisIfTable}/{@code OaisIfImage}/... shape - works
 * unchanged no matter which engine actually decoded the underlying bytes, as
 * long as the mapping's row/pixel/feature selectors match how that
 * particular tree represents repetition (see
 * {@link info.oais.infomodel.structure.StructureNodeKind}'s Javadoc on
 * ARRAY vs. repeated same-named COMPOSITE siblings). That is this package's
 * purpose: interoperability between different Digital Objects that happen to
 * share a semantic shape, regardless of how differently they are structured
 * at the bit level.</p>
 */
package info.oais.infomodel.structure.semantic;
