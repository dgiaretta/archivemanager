package info.oais.archive.manager.model;

/**
 * An external, clickable URL found as a literal property value on a graph node
 * (e.g. {@code rdfs:seeAlso}) -- surfaced separately from {@link GraphNode#properties()}
 * so the client's right-click menu can offer it as something to actually open.
 */
public record GraphLink(String propertyLocalName, String url) {
}
