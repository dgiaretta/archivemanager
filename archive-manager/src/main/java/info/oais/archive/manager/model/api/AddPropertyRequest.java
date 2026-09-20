package info.oais.archive.manager.model.api;

/** Body for {@code POST /api/entities/{id}/properties} (a literal/datatype property). */
public record AddPropertyRequest(String property, String customProperty, String value) {
}
