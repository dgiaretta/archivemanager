package info.oais.archive.manager.service;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.format.StorageFetcher;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Makes an Archival Information Package around a Data Object, with as much
 * of what OAIS requires of an AIP (see {@link AipComponents}) as the archive
 * already knows or can work out:
 * <ul>
 *   <li>Content Information: the Data Object, and the Representation
 *       Information it's interpreted using;</li>
 *   <li>Preservation Description Information with Fixity Information (the
 *       SHA-256 and size of its bits, fetched from their storage location),
 *       Reference Information (the Data Object's identifiers and where its
 *       bits are), Provenance Information (that this AIP was made from it,
 *       when and by whom), Context Information (what it's interpreted using
 *       and what it's linked to, and anything given), and Access Rights
 *       Information when given -- it's for the archive to decide, so it's
 *       never made up;</li>
 *   <li>a Package Description saying what the package is;</li>
 *   <li>the Designated Community it must stay understandable to, and its
 *       Preservation Objective, when given.</li>
 * </ul>
 * Packaging Information is provided when the AIP is written out as a BagIt
 * bag. What's still missing then shows on the AIP's page, to add with the
 * editor.
 */
@Service
public class AipBuilder {

    private final RdfStore store;
    private final EditService edit;
    private final ArchiveService archive;
    private final StorageFetcher fetcher;
    private final QueryRunner q;

    public AipBuilder(RdfStore store, EditService edit, ArchiveService archive, StorageFetcher fetcher, QueryRunner q) {
        this.store = store;
        this.edit = edit;
        this.archive = archive;
        this.fetcher = fetcher;
        this.q = q;
    }

    /**
     * What's given about the AIP that the archive can't know. Every part is optional.
     *
     * @param designatedCommunity   an existing Designated Community's IRI, used as it is
     * @param newCommunity          the name of a new Designated Community, if no existing one is chosen
     * @param communityDescription  what the new Designated Community knows and needs
     * @param preservationObjective what the Designated Community must be able to do with it
     * @param accessRights          who may use it, and how
     * @param context               how it relates to other information, besides what the archive finds
     * @param recordedBy            who is making the AIP
     */
    public record Details(String designatedCommunity, String newCommunity, String communityDescription,
                          String preservationObjective, String accessRights, String context, String recordedBy) {

        static String blankToNull(String s) {
            return s == null || s.isBlank() ? null : s.strip();
        }
    }

    /** A Designated Community already in the archive, to choose from. */
    public record Community(String iri, String label) {
    }

    /** The Designated Communities in the archive, in alphabetical order. */
    public List<Community> communities() {
        List<Community> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.dataModel(), Ns.PREFIXES
                + "SELECT DISTINCT ?c WHERE { ?c a im:DesignatedCommunity }")) {
            out.add(new Community(row.get("c"), archive.label(row.get("c"))));
        }
        out.sort(info.oais.archive.manager.model.Alphabetical.by(Community::label));
        return out;
    }

    /** Whether {@code iri} is a Data Object (a Digital or Physical Object). */
    public boolean isDataObject(String iri) {
        return !q.select(store.queryModel(), Ns.PREFIXES + """
                SELECT ?t WHERE { <%s> a ?t . ?t rdfs:subClassOf* im:DataObject } LIMIT 1
                """.formatted(iri)).isEmpty();
    }

    /** The AIPs {@code dataObject} is the Content Data Object of, if any. */
    public List<String> aipsOf(String dataObject) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> row : q.select(store.dataModel(), Ns.PREFIXES + """
                SELECT DISTINCT ?aip WHERE { ?aip im:hasContentInformation ?ci . ?ci im:hasDataObject <%s> }
                ORDER BY ?aip
                """.formatted(dataObject))) {
            out.add(row.get("aip"));
        }
        return out;
    }

    /** The SHA-256 and size of a Data Object's bits, as fetched. */
    public record Digest(String sha256, long size, String location) {
    }

    /**
     * Fetches {@code dataObject}'s bits from its storage location and digests
     * them. Empty if it has no storage location.
     *
     * @throws IOException if they can't be fetched
     */
    public Optional<Digest> digest(String dataObject) throws IOException {
        String location = firstObject(store.dataModel(), dataObject, Ns.IM + "hasStorageLocation");
        if (location == null) {
            return Optional.empty();
        }
        Path dir = Files.createTempDirectory("aip-bits-");
        try {
            Path bits = fetcher.fetch(URI.create(location), dir);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream in = Files.newInputStream(bits)) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    sha256.update(buffer, 0, n);
                    size += n;
                }
            }
            return Optional.of(new Digest(HexFormat.of().formatHex(sha256.digest()), size, location));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /**
     * Records an AIP whose Content Data Object is {@code dataObject}, with
     * everything the archive knows or was given (see the class comment).
     *
     * @param digest its bits' digest (see {@link #digest}), or null if it has none, or they couldn't be fetched
     * @param why    why there's no digest, for the Fixity Information's absence; null if there is one
     * @return the AIP's IRI
     */
    public String create(String dataObject, Details d, Digest digest, String why) {
        Model m = store.dataModel();
        String name = archive.label(dataObject);
        String now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String today = now.substring(0, 10);
        String by = Details.blankToNull(d.recordedBy());

        String content = edit.createEntity(Ns.IM + "ContentInformation");
        edit.addType(content, Ns.IM + "InformationObject");
        edit.addLiteral(content, Ns.RDFS + "label", "Content Information: " + name);
        edit.addRelationship(content, Ns.IM + "hasDataObject", dataObject);
        List<String> repInfo = objects(m, dataObject, Ns.IM + "interpretedUsing");
        for (String ri : repInfo) {
            edit.addRelationship(content, Ns.IM + "hasRepresentationInformation", ri);
        }

        String aip = edit.createEntity(Ns.IM + "ArchivalInformationPackage");
        edit.addLiteral(aip, Ns.RDFS + "label", "AIP: " + name);
        edit.addRelationship(aip, Ns.IM + "hasContentInformation", content);

        String pdi = edit.createEntity(Ns.IM + "PreservationDescriptionInformation");
        edit.addLiteral(pdi, Ns.RDFS + "label", "PDI: " + name);
        edit.addRelationship(aip, Ns.IM + "hasPreservationDescriptiveInformation", pdi);

        if (digest != null) {
            String fixity = edit.createEntity(Ns.IM + "FixityInformation");
            edit.addLiteral(fixity, Ns.RDFS + "label", "Fixity: " + name);
            edit.addLiteral(fixity, Ns.RDFS + "comment", "SHA-256: " + digest.sha256() + " (" + digest.size()
                    + " bytes), of the bits at " + digest.location() + ", computed on " + now + ".");
            edit.addRelationship(pdi, Ns.IM + "hasFixityInformation", fixity);
        }

        String reference = edit.createEntity(Ns.IM + "ReferenceInformation");
        edit.addLiteral(reference, Ns.RDFS + "label", "Reference: " + name);
        StringBuilder ref = new StringBuilder("Identifier of the Content Data Object: " + dataObject + ".");
        for (String identifier : identifiers(m, dataObject)) {
            ref.append(" Identifier: ").append(identifier).append('.');
        }
        String location = firstObject(m, dataObject, Ns.IM + "hasStorageLocation");
        if (location != null) {
            ref.append(" Its bits are at ").append(location).append('.');
        }
        edit.addLiteral(reference, Ns.RDFS + "comment", ref.toString());
        edit.addRelationship(pdi, Ns.IM + "hasReferenceInformation", reference);

        String provenance = edit.createEntity(Ns.IM + "ProvenanceInformation");
        edit.addLiteral(provenance, Ns.RDFS + "label", "Provenance: " + name);
        edit.addLiteral(provenance, Ns.RDFS + "comment", "This AIP was made from the Data Object " + name + " ("
                + dataObject + ") on " + now + (by == null ? "" : " by " + by) + ", in the archive."
                + (digest == null ? "" : " Its bits were fetched from " + digest.location() + " and digested then."));
        edit.addRelationship(pdi, Ns.IM + "hasProvenanceInformation", provenance);

        String context = edit.createEntity(Ns.IM + "ContextInformation");
        edit.addLiteral(context, Ns.RDFS + "label", "Context: " + name);
        StringBuilder ctx = new StringBuilder();
        if (Details.blankToNull(d.context()) != null) {
            ctx.append(d.context().strip()).append(d.context().strip().endsWith(".") ? " " : ". ");
        }
        if (!repInfo.isEmpty()) {
            ctx.append("It is interpreted using ").append(String.join(", ", repInfo.stream().map(archive::label)
                    .toList())).append(". ");
        }
        List<String> linked = linkedFrom(m, dataObject, content);
        if (!linked.isEmpty()) {
            ctx.append("It is linked from ").append(String.join(", ", linked)).append('.');
        }
        edit.addLiteral(context, Ns.RDFS + "comment", ctx.toString().strip());
        edit.addRelationship(pdi, Ns.IM + "hasContextInformation", context);

        if (Details.blankToNull(d.accessRights()) != null) {
            String rights = edit.createEntity(Ns.IM + "AccessRightsInformation");
            edit.addLiteral(rights, Ns.RDFS + "label", "Access Rights: " + name);
            edit.addLiteral(rights, Ns.RDFS + "comment", d.accessRights().strip());
            edit.addRelationship(pdi, Ns.IM + "hasAccessRightsInformation", rights);
        }

        String description = edit.createEntity(Ns.IM + "PackageDescription");
        edit.addLiteral(description, Ns.RDFS + "label", "Package Description: " + name);
        edit.addLiteral(description, Ns.RDFS + "comment", name + ": an Archival Information Package made from the "
                + "Data Object " + name + " on " + today + (repInfo.isEmpty() ? "" : ", interpreted using "
                + String.join(", ", repInfo.stream().map(archive::label).toList())) + ".");
        edit.addRelationship(description, Ns.IM + "derivedFrom", aip);
        edit.addRelationship(aip, Ns.IM + "describedBy", description);

        String community = Details.blankToNull(d.designatedCommunity());
        if (community == null && Details.blankToNull(d.newCommunity()) != null) {
            community = edit.createEntity(Ns.IM + "DesignatedCommunity");
            edit.addLiteral(community, Ns.RDFS + "label", d.newCommunity().strip());
            edit.addLiteral(community, Ns.RDFS + "comment", Details.blankToNull(d.communityDescription()));
        }
        if (community != null) {
            edit.addRelationship(aip, Ns.IM + "hasDesignatedCommunity", community);
            if (Details.blankToNull(d.preservationObjective()) != null) {
                String objective = edit.createEntity(Ns.IM + "PreservationObjective");
                edit.addLiteral(objective, Ns.RDFS + "label", "Preservation Objective: " + name);
                edit.addLiteral(objective, Ns.RDFS + "comment", d.preservationObjective().strip());
                edit.addRelationship(community, Ns.IM + "hasPreservationObjective", objective);
                edit.addRelationship(objective, Ns.IM + "carriedOutUsing", content);
            }
        }
        if (why != null) {
            edit.addLiteral(aip, Ns.RDFS + "comment", "No Fixity Information yet: " + why);
        }
        return aip;
    }

    /** The Data Object's identifiers, as written on it (dc:identifier, rico:identifier, ...). */
    private static List<String> identifiers(Model m, String iri) {
        List<String> out = new ArrayList<>();
        for (Statement s : m.getResource(iri).listProperties().toList()) {
            String p = s.getPredicate().getLocalName();
            if (p != null && p.toLowerCase(java.util.Locale.ROOT).contains("identifier") && s.getObject().isLiteral()) {
                out.add(s.getObject().asLiteral().getLexicalForm());
            }
        }
        return out;
    }

    /** What links to the Data Object (records, other packages...), by label, besides its new Content Information. */
    private List<String> linkedFrom(Model m, String iri, String except) {
        List<String> out = new ArrayList<>();
        for (Resource s : m.listStatements(null, null, m.getResource(iri)).mapWith(Statement::getSubject).toSet()) {
            if (s.isURIResource() && !s.getURI().equals(except) && !s.hasProperty(RDF.type,
                    m.getResource(Ns.IM + "ContentInformation"))) {
                out.add(archive.label(s.getURI()));
            }
        }
        out.sort(info.oais.archive.manager.model.Alphabetical.TEXT);
        return out;
    }

    private static List<String> objects(Model m, String iri, String property) {
        List<String> out = new ArrayList<>();
        for (Statement s : m.getResource(iri).listProperties(m.createProperty(property)).toList()) {
            RDFNode o = s.getObject();
            if (o.isURIResource()) {
                out.add(o.asResource().getURI());
            }
        }
        return out;
    }

    private static String firstObject(Model m, String iri, String property) {
        List<String> found = objects(m, iri, property);
        return found.isEmpty() ? null : found.get(0);
    }
}
