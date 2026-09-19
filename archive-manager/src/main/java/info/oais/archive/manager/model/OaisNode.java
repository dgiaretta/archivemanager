package info.oais.archive.manager.model;

import java.util.List;

/**
 * One node in the OAIS structural breakdown reached by walking
 * {@code has*} object properties from a record's linked OAIS
 * counterpart individual (e.g. Information Object -> Data Object -> Bit,
 * or Preservation Description Information -> Provenance Information).
 */
public record OaisNode(
        String iri,
        String typeLocalName,
        String comment,
        List<String> ricDescriptionIris,
        List<OaisNode> children) {
}
