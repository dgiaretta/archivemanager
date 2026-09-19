package info.oais.archive.manager.model;

/**
 * @param isExternal true if the target has no asserted rdf:type anywhere in
 *                    the graph -- i.e. it was only ever used as a property
 *                    value (a bare URI like an rdfs:seeAlso link to a file
 *                    in Dropbox), not a typed entity of our own. Lets the
 *                    template link straight to the URI itself instead of
 *                    routing through /resource/{id}, which has nothing
 *                    useful to show for something that isn't really "one of
 *                    ours".
 */
public record LinkedResource(String propertyLocalName, ResourceSummary resource, boolean isExternal) {
}