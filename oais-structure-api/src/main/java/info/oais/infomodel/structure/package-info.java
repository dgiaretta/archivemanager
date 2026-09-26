/**
 * Engine-agnostic bridge between oaisCore's {@code RepresentationInformation}
 * model (in particular {@link info.oais.infomodel.interfaces.StructureRepInfo})
 * and concrete binary-parsing engines.
 *
 * <p>The central idea: {@link info.oais.infomodel.structure.ExecutableStructureRepInfo}
 * extends the existing, empty {@code StructureRepInfo} marker interface with
 * one capability - {@code apply(DigitalObject)} - producing a
 * {@link info.oais.infomodel.structure.StructureNode} tree. Three sibling
 * modules ({@code oais-structure-dfdl}, {@code oais-structure-kaitai},
 * {@code oais-structure-drb}) each provide one implementation, backed by
 * Apache Daffodil, a Kaitai Struct generated parser, and DRB respectively;
 * {@link info.oais.infomodel.structure.StructureInterpreterFactory} discovers
 * whichever of them are on the classpath via {@link java.util.ServiceLoader}.
 * Everything in this package depends only on oaisCore and the JDK, so
 * application code that programs against it never needs a compile-time
 * dependency on any particular parsing engine.</p>
 */
package info.oais.infomodel.structure;
