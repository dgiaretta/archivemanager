package info.oais.archive.manager.model.api;

/**
 * Body for {@code POST /api/entities}: create a new individual of any RiC-O
 * or OAIS class -- the REST equivalent of {@code EntityController}'s
 * {@code POST /entities} form. Exactly one of the two fields should be set;
 * if both are, {@code customClass} wins, same as the HTML form.
 */
public record CreateEntityRequest(String classIri, String customClass) {
}
