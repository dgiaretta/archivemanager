package info.oais.archive.manager.model.api;

/**
 * Body for {@code POST /api/entities/{id}/relationships} (an object property to another resource).
 *
 * @param targetId  the short encoded id of an existing resource (as returned in {@code ResourceSummary.id()}).
 * @param customTarget a raw target IRI, for a resource {@code targetId} can't reach (e.g. outside the first 200
 *                     entities the picker offers) -- wins over {@code targetId} if both are set, same as the HTML form.
 */
public record AddRelationshipRequest(String property, String customProperty, String targetId, String customTarget) {
}
