package info.oais.archive.manager.model;

/**
 * One class-level correspondence declared in the bridging ontology,
 * e.g. {@code rico:Record  skos:broadMatch  im:InformationObject}, with its
 * documented rationale.
 */
public record BridgeMapping(String relationLocalName, String targetIri, String targetLocalName, String rationale) {
}
