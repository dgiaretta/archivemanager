package info.oais.archive.manager.model.api;

/** @param direction {@code "outgoing"} or {@code "incoming"} -- see {@code EntityController.removeRelationship} for why it matters. */
public record RemoveRelationshipRequest(String propertyIri, String otherIri, String direction) {
}
