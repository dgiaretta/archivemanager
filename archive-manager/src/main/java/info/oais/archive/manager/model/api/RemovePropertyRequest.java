package info.oais.archive.manager.model.api;

/** Body for {@code DELETE /api/entities/{id}/properties}. Both fields identify the exact triple to remove. */
public record RemovePropertyRequest(String propertyIri, String value) {
}
