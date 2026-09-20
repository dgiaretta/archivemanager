package info.oais.archive.manager.model.api;

/** Body for {@code POST /api/records} -- the REST equivalent of {@code RecordController}'s {@code POST /records} form. */
public record CreateRecordRequest(String type, String title, String description, String creatorId, String parentId) {
}
