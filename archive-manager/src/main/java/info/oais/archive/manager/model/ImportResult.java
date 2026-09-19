package info.oais.archive.manager.model;

import java.util.List;

public record ImportResult(String accessionIri, int recordCount, List<String> warnings) {
}
