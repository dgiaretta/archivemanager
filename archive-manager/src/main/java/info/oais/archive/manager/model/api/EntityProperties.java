package info.oais.archive.manager.model.api;

import java.util.List;

/**
 * Body of {@code GET /api/entities/{id}}: an entity's types and its
 * properties, each value exactly as stored -- so a literal can be passed back
 * as it is to {@code DELETE /api/entities/{id}/properties}, and a resource's
 * {@code targetId} to the other endpoints.
 *
 * @param types      its {@code rdf:type}s, as full IRIs
 * @param properties its other statements, in property order
 */
public record EntityProperties(String iri, String id, String title, List<String> types, List<Property> properties) {

    /**
     * One statement about the entity.
     *
     * @param property the property's IRI
     * @param value    a literal's text, or the IRI of the resource it points to
     * @param resource whether it points to a resource rather than being a literal
     * @param targetId for a resource, its id in this API; null otherwise
     * @param datatype for a literal, its datatype IRI (e.g. {@code xsd:string}'s); null otherwise
     * @param language for a literal with a language tag, the tag; null otherwise
     */
    public record Property(String property, String value, boolean resource, String targetId, String datatype,
                           String language) {
    }
}
