package info.oais.archive.manager.model;

import java.util.List;

/**
 * One node in the OAIS structural breakdown reached by walking {@code im:}
 * object properties from a record's linked OAIS counterpart individual --
 * both outgoing ({@code has*} properties, e.g. Information Object -> Data
 * Object -> Bit) and incoming (e.g. an ArchivalInformationPackage that
 * points AT this node via {@code hasContentInformation} rather than the
 * other way around; OAIS's own containment direction there is AIP-contains-
 * ContentInformation, so it can only be discovered from the content side by
 * walking backwards).
 *
 * @param connectingProperty the local name of the {@code im:} property that connects this node
 *                           to its parent (null for the root, which has none); {@code incoming}
 *                           says which direction it runs, since the same field name is used for both.
 * @param incoming           true if {@code connectingProperty} runs FROM this node TO its parent
 *                           (the parent doesn't "have" this node -- this node points at the parent).
 */
public record OaisNode(
        String iri,
        String typeLocalName,
        String comment,
        List<String> ricDescriptionIris,
        String connectingProperty,
        boolean incoming,
        List<OaisNode> children) {
}
