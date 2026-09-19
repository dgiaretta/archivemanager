package info.oais.archive.manager.model;

import java.util.List;

/**
 * One row of a flattened, pre-order walk of an {@link OaisNode} tree, with
 * indentation already computed as a ready-to-use CSS value. Rendering a flat
 * list with a plain (non-recursive) {@code th:each} avoids Thymeleaf's
 * recursive parameterized-fragment mechanism, which turned out to be
 * unreliable for this self-referencing tree fragment.
 */
public record OaisFlatNode(
        String iri,
        String typeLabel,
        String comment,
        List<String> ricDescriptionIris,
        int depth,
        String indentStyle) {
}
