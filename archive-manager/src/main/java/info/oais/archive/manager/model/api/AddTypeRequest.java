package info.oais.archive.manager.model.api;

/** Body for {@code POST /api/entities/{id}/types}. See {@link CreateEntityRequest} for the classIri/customClass convention. */
public record AddTypeRequest(String typeIri, String customType) {
}
