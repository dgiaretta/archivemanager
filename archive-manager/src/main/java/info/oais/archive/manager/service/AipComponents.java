package info.oais.archive.manager.service;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The components an Archival Information Package must have, by the OAIS
 * Information Model (as {@code oais_im_schema-sh-v5.ttl} restricts it), and
 * which of them a given AIP has:
 * <ul>
 *   <li>exactly one Content Information ({@code im:hasContentInformation}),
 *       with its Content Data Object ({@code im:hasDataObject}), that Data
 *       Object's bits, and the Representation Information needed to
 *       interpret it ({@code im:hasRepresentationInformation} on the Content
 *       Information, or {@code im:interpretedUsing} on the Data Object);</li>
 *   <li>exactly one Preservation Description Information
 *       ({@code im:hasPreservationDescriptiveInformation}), complete with
 *       Reference, Provenance, Context, Fixity and Access Rights Information;</li>
 *   <li>exactly one Packaging Information ({@code im:delimitedBy}), saying how
 *       the components are bound together and how to extract them;</li>
 *   <li>at least one Package Description ({@code im:describedBy}), for finding
 *       the package.</li>
 * </ul>
 */
@Component
public class AipComponents {

    private final RdfStore store;

    public AipComponents(RdfStore store) {
        this.store = store;
    }

    public enum Status {
        PRESENT("present"), MISSING("missing"), TOO_MANY("more than one"), PROVIDED_BY_BAG("provided by the bag");

        private final String text;

        Status(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /**
     * One component an AIP must have.
     *
     * @param name        e.g. "Fixity Information"
     * @param depth       0 for a component of the AIP itself, 1 for a part of one
     * @param requirement what OAIS asks for, e.g. "exactly one"
     * @param status      whether this AIP has it
     * @param iris        the individuals that are it
     * @param files       the files in a bag that hold it, if written out
     * @param note        anything more to say, or null
     */
    public record Part(String name, int depth, String requirement, Status status, List<String> iris,
                       List<String> files, String note) {

        public boolean missing() {
            return status == Status.MISSING || status == Status.TOO_MANY;
        }
    }

    /**
     * {@code aip}'s components.
     *
     * @param files     for a bag: the file holding each individual's bits or description, by IRI; else empty
     * @param packaging for a bag with no Packaging Information of its own: the one describing the bag; else null
     */
    public List<Part> check(String aip, Map<String, String> files, String packaging) {
        Model m = store.dataModel();
        Resource r = m.getResource(aip);
        List<Part> parts = new ArrayList<>();

        List<Resource> contents = objects(r, "hasContentInformation");
        parts.add(part("Content Information", 0, "exactly one", contents, true, files, null));
        List<Resource> dataObjects = new ArrayList<>();
        List<Resource> repInfo = new ArrayList<>();
        for (Resource ci : contents) {
            dataObjects.addAll(objects(ci, "hasDataObject"));
            repInfo.addAll(objects(ci, "hasRepresentationInformation"));
        }
        parts.add(part("Content Data Object", 1, "exactly one", dataObjects, true, files, null));
        parts.add(bits(m, dataObjects, files));
        for (Resource d : dataObjects) {
            repInfo.addAll(objects(d, "interpretedUsing"));
        }
        List<String> descriptionFiles = new ArrayList<>();
        int descriptions = 0;
        for (Resource ri : representationInformation(m, repInfo)) {
            if (ri.hasProperty(m.createProperty(Ns.IM + "specificationText"))) {
                descriptions++;
                if (files.containsKey(ri.getURI())) {
                    descriptionFiles.add(files.get(ri.getURI()));
                }
            }
        }
        parts.add(new Part("Representation Information", 1, "at least one", repInfo.isEmpty() ? Status.MISSING
                : Status.PRESENT, uris(repInfo), descriptionFiles, repInfo.isEmpty() ? null
                : descriptions + (descriptions == 1 ? " machine-readable description" : " machine-readable descriptions")
                + " (structure descriptions, view specifications) among it"));

        List<Resource> pdis = objects(r, "hasPreservationDescriptiveInformation");
        parts.add(part("Preservation Description Information", 0, "exactly one", pdis, true, files, null));
        for (String kind : List.of("Reference", "Provenance", "Context", "Fixity", "AccessRights")) {
            List<Resource> found = new ArrayList<>();
            for (Resource pdi : pdis) {
                found.addAll(objects(pdi, "has" + kind + "Information"));
            }
            String name = (kind.equals("AccessRights") ? "Access Rights" : kind) + " Information";
            String note = kind.equals("Fixity") && found.isEmpty() && !files.isEmpty()
                    ? "the bag's manifest-sha256.txt checks the files in transfer, but isn't the AIP's own Fixity "
                    + "Information" : null;
            parts.add(part(name, 1, "exactly one", found, true, files, note));
        }

        List<Resource> packagings = objects(r, "delimitedBy");
        if (packagings.isEmpty() && packaging != null) {
            parts.add(new Part("Packaging Information", 0, "exactly one", Status.PROVIDED_BY_BAG, List.of(packaging),
                    List.of("bagit.txt", "manifest-sha256.txt"), "this BagIt bag binds the components together and "
                    + "says how to extract them; described in data/aip.ttl"));
        } else if (packagings.isEmpty()) {
            parts.add(new Part("Packaging Information", 0, "exactly one", Status.PROVIDED_BY_BAG, List.of(),
                    List.of(), "writing the AIP out as a BagIt bag provides it"));
        } else {
            parts.add(part("Packaging Information", 0, "exactly one", packagings, true, files, null));
        }
        parts.add(part("Package Description", 0, "at least one", objects(r, "describedBy"), false, files, null));
        return parts;
    }

    private Part bits(Model m, List<Resource> dataObjects, Map<String, String> files) {
        List<String> locations = new ArrayList<>();
        List<String> inBag = new ArrayList<>();
        boolean physical = !dataObjects.isEmpty();
        for (Resource d : dataObjects) {
            physical &= d.hasProperty(org.apache.jena.vocabulary.RDF.type, m.createResource(Ns.IM + "PhysicalObject"));
            for (Resource location : objects(d, "hasStorageLocation")) {
                locations.add(location.getURI());
            }
            if (files.containsKey(d.getURI())) {
                inBag.add(files.get(d.getURI()));
            }
        }
        if (physical) {
            return new Part("Bits of the Content Data Object", 1, "for a Digital Object", Status.PRESENT, List.of(), List.of(),
                    "a Physical Object has no bits");
        }
        if (locations.isEmpty()) {
            return new Part("Bits of the Content Data Object", 1, "at least one bit sequence", Status.MISSING, List.of(),
                    List.of(), "the Data Object has no storage location for its bits");
        }
        return new Part("Bits of the Content Data Object", 1, "at least one bit sequence", Status.PRESENT, locations, inBag,
                null);
    }

    private static Part part(String name, int depth, String requirement, List<Resource> found, boolean atMostOne,
                             Map<String, String> files, String note) {
        Status status = found.isEmpty() ? Status.MISSING
                : atMostOne && found.size() > 1 ? Status.TOO_MANY : Status.PRESENT;
        List<String> inBag = new ArrayList<>();
        for (Resource r : found) {
            if (r.isURIResource() && files.containsKey(r.getURI())) {
                inBag.add(files.get(r.getURI()));
            }
        }
        return new Part(name, depth, requirement, status, uris(found), inBag, note);
    }

    /** The Representation Information reachable from {@code roots}. */
    private static Set<Resource> representationInformation(Model m, List<Resource> roots) {
        Set<Resource> reached = new LinkedHashSet<>();
        Deque<Resource> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            Resource r = todo.pop();
            if (!reached.add(r)) {
                continue;
            }
            for (String p : List.of("hasGroupMember", "hasStructureRepresentationInformation",
                    "hasSemanticRepresentationInformation", "hasOtherRepresentationInformation",
                    "interpretedUsingRecurse", "interpretedUsing")) {
                todo.addAll(objects(r, p));
            }
        }
        return reached;
    }

    private static List<Resource> objects(Resource r, String imProperty) {
        List<Resource> found = new ArrayList<>();
        for (Statement s : r.listProperties(r.getModel().createProperty(Ns.IM + imProperty)).toList()) {
            if (s.getObject().isURIResource()) {
                found.add(s.getObject().asResource());
            }
        }
        return found;
    }

    private static List<String> uris(List<Resource> resources) {
        return resources.stream().filter(Resource::isURIResource).map(Resource::getURI).distinct().toList();
    }

    /** Whether nothing is missing. */
    public static boolean complete(List<Part> parts) {
        return parts.stream().noneMatch(Part::missing);
    }

    /** The parts that are missing, by name, e.g. "Package Description, Context Information". */
    public static String missing(List<Part> parts) {
        return String.join(", ", parts.stream().filter(Part::missing).map(p -> p.name()
                + (p.status() == Status.TOO_MANY ? " (more than one)" : "")).toList());
    }

    /** The parts as a plain-text list, for a bag's tag file. */
    public static String text(String aip, List<Part> parts) {
        StringBuilder sb = new StringBuilder();
        sb.append("OAIS Archival Information Package components\n");
        sb.append("(the OAIS Information Model, CCSDS 650.0-M-3, as data/ontologies/ defines it)\n\n");
        sb.append("AIP: ").append(aip).append('\n');
        sb.append("Complete: ").append(complete(parts) ? "yes" : "no -- missing " + missing(parts)).append("\n\n");
        sb.append("Each component is the individual(s) named in data/aip.ttl; files are relative to the bag.\n\n");
        for (Part p : parts) {
            String indent = "  ".repeat(p.depth());
            sb.append(indent).append(p.name()).append(" (").append(p.requirement()).append("): ")
                    .append(p.status().text()).append('\n');
            for (String iri : p.iris()) {
                sb.append(indent).append("    ").append(iri).append('\n');
            }
            for (String file : p.files()) {
                sb.append(indent).append("    file: ").append(file).append('\n');
            }
            if (p.note() != null) {
                sb.append(indent).append("    note: ").append(p.note()).append('\n');
            }
        }
        return sb.toString();
    }
}
