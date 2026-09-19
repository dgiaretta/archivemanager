package info.oais.archive.manager.model;

/** A property (by local name) and its value, plus whether the value is itself a browsable resource. */
public record PropertyValue(String propertyLocalName, String value, boolean isResource, String valueIri) {
}
