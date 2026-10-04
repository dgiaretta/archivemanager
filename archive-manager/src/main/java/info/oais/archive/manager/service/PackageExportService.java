package info.oais.archive.manager.service;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.format.DataObjectViewService;
import info.oais.archive.manager.service.format.StorageFetcher;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes out what the archive holds as files, for another archive or anyone
 * else to use without this application:
 * <ul>
 *   <li>a <em>data description</em> -- a Data Object's Representation
 *       Information, or any Representation Information, with everything it
 *       needs -- as a zip of {@code description.ttl} and each description or
 *       view specification as a file of its own;</li>
 *   <li>an <em>Archival Information Package</em> as a BagIt bag (RFC 8493),
 *       zipped: {@code aip.ttl} describing the AIP and everything it's made
 *       of, the bits of its Data Objects, its descriptions as files, and the
 *       OAIS ontologies its terms come from, with SHA-256 manifests -- the
 *       third of OAIS's ways of preserving information, handing a complete
 *       AIP to another archive.</li>
 * </ul>
 * The Turtle names each file it comes with by an {@code im:hasStorageLocation}
 * relative to itself (as a Representation Information manifest does),
 * alongside the original location. What's included is everything reachable
 * from the start through OAIS Information Model properties, except where a
 * Transformation came from and the AIP an AIP Version was made from, which
 * are only referred to; links to RiC-O descriptions are kept as references,
 * not followed.
 */
@Service
public class PackageExportService {

    /** Stands for "relative to the Turtle file" until the Turtle is written. */
    private static final String RELATIVE = "http://relative.invalid/";

    /** OAIS properties that refer to other packages or Data Objects rather than to parts of this one. */
    private static final Set<String> NOT_FOLLOWED = Set.of(Ns.IM + "transformationSource", Ns.IM + "hasSourceAIP",
            Ns.IM + "derivedFrom");

    /** The tag file naming each component of the AIP. */
    public static final String COMPONENTS_FILE = "oais-aip-components.txt";

    private static final List<String> ONTOLOGIES = List.of("rdf/oais_im_schema-sh-v5.ttl",
            "rdf/oais-im-local-extensions.ttl");

    private final RdfStore store;
    private final QueryRunner q;
    private final ArchiveService archive;
    private final DataObjectViewService views;
    private final StorageFetcher fetcher;
    private final AipComponents components;

    public PackageExportService(RdfStore store, QueryRunner q, ArchiveService archive, DataObjectViewService views,
                                StorageFetcher fetcher, AipComponents components) {
        this.store = store;
        this.q = q;
        this.archive = archive;
        this.views = views;
        this.fetcher = fetcher;
        this.components = components;
    }

    /** A file to download: its name and contents. */
    public record Export(String fileName, byte[] bytes) {
    }

    /** Whether {@code iri} is an Archival Information Package (or a kind of one). */
    public boolean isPackage(String iri) {
        return ask(iri, "?t rdfs:subClassOf* im:ArchivalInformationPackage");
    }

    /** Whether {@code iri} is Representation Information, or a Data Object interpreted using some. */
    public boolean hasDescription(String iri) {
        return ask(iri, "?t rdfs:subClassOf* im:RepresentationInformation")
                || store.dataModel().getResource(iri).hasProperty(store.dataModel()
                .createProperty(Ns.IM + "interpretedUsing"));
    }

    private boolean ask(String iri, String typeTest) {
        return !q.select(store.queryModel(), Ns.PREFIXES + "SELECT ?t WHERE { <%s> a ?t . %s } LIMIT 1"
                .formatted(iri, typeTest)).isEmpty();
    }

    /**
     * {@code iri}'s data description: for a Data Object, the Representation
     * Information it is interpreted using; for Representation Information,
     * itself -- with all it needs, as a zip.
     */
    public Export dataDescription(String iri) throws IOException {
        Model m = store.dataModel();
        Resource start = m.getResource(iri);
        List<Resource> roots = new ArrayList<>();
        start.listProperties(m.createProperty(Ns.IM + "interpretedUsing")).forEachRemaining(s -> {
            if (s.getObject().isResource()) {
                roots.add(s.getObject().asResource());
            }
        });
        if (roots.isEmpty()) {
            roots.add(start);
        }
        Set<Resource> included = reachable(m, roots);
        String name = fileName(archive.label(iri)) + "-description";
        Files files = new Files(name + "/");
        Model out = extract(m, included);
        if (!roots.contains(start)) {
            for (Resource root : roots) {
                out.add(start, m.createProperty(Ns.IM + "interpretedUsing"), root);
            }
        }
        addDescriptionFiles(m, included, out, files, "descriptions/", new LinkedHashMap<>());
        files.add("description.ttl", turtle(out));
        files.add("README.txt", ("""
                Data description: %s
                %s

                description.ttl is the OAIS Representation Information, in RDF (Turtle), using
                the OAIS Information Model's terms (http://ontology.oais.info/im/, defined in
                ontologies/). Each structure description and view specification is also a file
                of its own in descriptions/, named in description.ttl by an im:hasStorageLocation
                relative to it; its im:specificationLanguage says what language it is in.
                Written by archive-manager on %s.
                """).formatted(archive.label(iri), iri, LocalDate.now()).getBytes(StandardCharsets.UTF_8));
        addOntologies(files, "ontologies/");
        return new Export(name + ".zip", files.zip());
    }

    /**
     * {@code aipIri} as a zipped BagIt bag: the AIP and everything it's made
     * of in {@code data/aip.ttl}, its Data Objects' bits in
     * {@code data/objects/}, its descriptions in {@code data/descriptions/},
     * and the ontologies in {@code data/ontologies/}. The tag file
     * {@value #COMPONENTS_FILE} names every component an AIP must have
     * ({@link AipComponents}) -- the individuals in {@code aip.ttl} and the
     * files that are it -- and says which are missing, as {@code bag-info.txt}
     * does too. An AIP with no Packaging Information of its own gets one in
     * {@code aip.ttl} describing the bag, which is what binds its components
     * together.
     *
     * @param referToBits whether to refer to the Data Objects' bits in {@code fetch.txt}, by their storage
     *                    locations, rather than include them: for large data, or bits already kept where the
     *                    receiver can fetch them. They are still read once, for the manifest's digests.
     * @throws IOException if a Data Object's bits can't be fetched: an AIP without its bits isn't complete,
     *                     and a bag can't refer to a file without its digest
     */
    public Export bag(String aipIri, boolean referToBits) throws IOException {
        Model m = store.dataModel();
        Set<Resource> included = reachable(m, List.of(m.getResource(aipIri)));
        String name = fileName(archive.label(aipIri));
        Files payload = new Files("data/");
        Model out = extract(m, included);
        Map<String, String> filesByIri = new LinkedHashMap<>();
        addDescriptionFiles(m, included, out, payload, "descriptions/", filesByIri);

        Property storage = m.createProperty(Ns.IM + "hasStorageLocation");
        Set<String> used = new HashSet<>();
        List<BagIt.Fetched> fetchedFiles = new ArrayList<>();
        Path work = java.nio.file.Files.createTempDirectory("aip-bag-");
        try {
            for (Resource r : included) {
                if (!isDataObject(m, r)) {
                    continue;
                }
                for (Statement s : r.listProperties(storage).toList()) {
                    if (!s.getObject().isURIResource() || s.getObject().asResource().getURI().startsWith(RELATIVE)) {
                        continue;
                    }
                    URI location = URI.create(s.getObject().asResource().getURI());
                    Path fetched;
                    try {
                        fetched = fetcher.fetch(location, work);
                    } catch (IOException e) {
                        throw new IOException("Couldn't fetch the bits of " + archive.label(r.getURI()) + " from "
                                + location + ", so the AIP can't be written out complete: " + e.getMessage(), e);
                    }
                    String file = unique("objects/" + lastSegment(location, archive.label(r.getURI())), used);
                    byte[] bytes = java.nio.file.Files.readAllBytes(fetched);
                    if (referToBits) {
                        fetchedFiles.add(new BagIt.Fetched(location.toASCIIString(), "data/" + file, bytes.length,
                                BitStore.sha256(bytes)));
                    } else {
                        payload.add(file, bytes);
                    }
                    out.add(r, storage, out.createResource(RELATIVE + file));
                    filesByIri.putIfAbsent(r.getURI(), "data/" + file + (referToBits
                            ? " (to fetch from " + location + ", as fetch.txt says)" : ""));
                }
            }
        } finally {
            try (Stream<Path> paths = java.nio.file.Files.walk(work)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        String packaging = null;
        if (!m.getResource(aipIri).hasProperty(m.createProperty(Ns.IM + "delimitedBy"))) {
            packaging = aipIri + (aipIri.contains("#") ? "-bagit-packaging" : "#bagit-packaging");
            Resource p = out.createResource(packaging);
            p.addProperty(RDF.type, out.createResource(Ns.IM + "PackagingInformation"));
            p.addProperty(out.createProperty(Ns.RDFS + "label"), "BagIt packaging of " + archive.label(aipIri));
            p.addProperty(out.createProperty(Ns.RDFS + "comment"), "This AIP is packaged as a BagIt bag (RFC 8493, "
                    + "version 1.0): bagit.txt declares it; the payload is under data/ -- this description "
                    + "(data/aip.ttl), the Data Objects' bits (data/objects/), the descriptions (data/descriptions/) "
                    + "and the ontologies defining the terms (data/ontologies/), each file named here by an "
                    + "im:hasStorageLocation relative to data/aip.ttl; manifest-sha256.txt gives every payload "
                    + "file's SHA-256 digest; " + COMPONENTS_FILE + " names each component of the AIP."
                    + (fetchedFiles.isEmpty() ? "" : " The Data Objects' bits are referred to rather than included: "
                    + "fetch.txt gives the URL to fetch each from, and where it goes in the bag."));
            p.addProperty(out.createProperty(Ns.IM + "identifies"), out.getResource(aipIri));
            out.add(out.getResource(aipIri), out.createProperty(Ns.IM + "delimitedBy"), p);
        }
        List<AipComponents.Part> parts = components.check(aipIri, filesByIri, packaging);

        payload.add("aip.ttl", turtle(out));
        addOntologies(payload, "ontologies/");
        Map<String, String> info = new LinkedHashMap<>();
        info.put("External-Identifier", aipIri);
        info.put("External-Description", "OAIS Archival Information Package: " + archive.label(aipIri)
                + ". data/aip.ttl describes it in RDF (Turtle) with the OAIS Information Model's terms, defined in "
                + "data/ontologies/; each file's place in it is the im:hasStorageLocation relative to data/aip.ttl. "
                + COMPONENTS_FILE + " names each of its components."
                + (fetchedFiles.isEmpty() ? "" : " The Data Objects' bits are listed in fetch.txt, to be fetched "
                + "into the bag before it is complete."));
        info.put("OAIS-AIP-Complete", AipComponents.complete(parts) ? "yes" : "no");
        if (!AipComponents.complete(parts)) {
            info.put("OAIS-AIP-Missing", AipComponents.missing(parts));
        }
        info.put("Bag-Software-Agent", "archive-manager (OAIS)");
        info.put("Bagging-Date", LocalDate.now().toString());
        return new Export(name + ".zip", BagIt.write(name, payload.entries(), fetchedFiles, info,
                Map.of(COMPONENTS_FILE, AipComponents.text(aipIri, parts).getBytes(StandardCharsets.UTF_8))));
    }

    private static boolean isDataObject(Model m, Resource r) {
        return r.hasProperty(RDF.type, m.createResource(Ns.IM + "DataObject"))
                || r.hasProperty(RDF.type, m.createResource(Ns.IM + "DigitalObject"))
                || r.hasProperty(RDF.type, m.createResource(Ns.IM + "PhysicalObject"));
    }

    /**
     * Everything in the data graph reachable from {@code roots} through OAIS
     * Information Model properties (except those to other packages), and the
     * concepts of any concept scheme reached.
     */
    private static Set<Resource> reachable(Model m, List<Resource> roots) {
        Set<Resource> reached = new LinkedHashSet<>();
        Deque<Resource> todo = new ArrayDeque<>(roots);
        Property inScheme = m.createProperty(Ns.SKOS + "inScheme");
        while (!todo.isEmpty()) {
            Resource r = todo.pop();
            if (!reached.add(r)) {
                continue;
            }
            for (Statement s : r.listProperties().toList()) {
                String p = s.getPredicate().getURI();
                if (p.startsWith(Ns.IM) && !NOT_FOLLOWED.contains(p) && s.getObject().isResource()
                        && m.contains(s.getObject().asResource(), null, (RDFNode) null)) {
                    todo.add(s.getObject().asResource());
                }
            }
            m.listSubjectsWithProperty(inScheme, r).forEachRemaining(todo::add);
        }
        return reached;
    }

    /** Every statement about {@code included}, with the usual prefixes. */
    private static Model extract(Model m, Set<Resource> included) {
        Model out = ModelFactory.createDefaultModel();
        out.setNsPrefix("im", Ns.IM);
        out.setNsPrefix("rdfs", Ns.RDFS);
        out.setNsPrefix("skos", Ns.SKOS);
        out.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
        out.setNsPrefix("ex", Ns.EX);
        for (Resource r : included) {
            out.add(m.listStatements(r, null, (RDFNode) null));
        }
        return out;
    }

    /**
     * Each description or view specification in {@code included} as a file,
     * named in {@code out}; each file's path in the bag is put in {@code paths}.
     */
    private void addDescriptionFiles(Model m, Set<Resource> included, Model out, Files files, String folder,
                                     Map<String, String> paths) {
        Property storage = out.createProperty(Ns.IM + "hasStorageLocation");
        Set<String> used = new HashSet<>();
        for (Resource r : included) {
            if (!r.isURIResource()) {
                continue;
            }
            views.specification(r.getURI()).ifPresent(spec -> {
                String file = unique(folder + fileName(archive.label(r.getURI())) + spec.extension(), used);
                files.add(file, spec.text().getBytes(StandardCharsets.UTF_8));
                out.add(r, storage, out.createResource(RELATIVE + file));
                paths.put(r.getURI(), files.prefix + file);
            });
        }
    }

    private static void addOntologies(Files files, String folder) throws IOException {
        for (String resource : ONTOLOGIES) {
            try (InputStream in = new ClassPathResource(resource).getInputStream()) {
                files.add(folder + resource.substring(resource.lastIndexOf('/') + 1), in.readAllBytes());
            }
        }
    }

    /** Turtle, with each file named relative to the Turtle file itself. */
    private static byte[] turtle(Model model) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, model, Lang.TURTLE);
        return out.toString(StandardCharsets.UTF_8).replace("<" + RELATIVE, "<").getBytes(StandardCharsets.UTF_8);
    }

    private static String lastSegment(URI location, String fallback) {
        String path = location.getPath();
        String last = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        return last.isBlank() ? fileName(fallback) + ".bin" : fileName(last);
    }

    private static String unique(String path, Set<String> used) {
        String candidate = path;
        int dot = path.lastIndexOf('.');
        for (int i = 2; !used.add(candidate); i++) {
            candidate = dot > path.lastIndexOf('/') ? path.substring(0, dot) + "-" + i + path.substring(dot)
                    : path + "-" + i;
        }
        return candidate;
    }

    static String fileName(String label) {
        String name = label == null ? "" : label.strip().replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^[-.]+|[-.]+$", "");
        return name.isEmpty() ? "package" : name;
    }

    /** Files being gathered under a folder, in order. */
    private static final class Files {
        private final String prefix;
        private final Map<String, byte[]> entries = new LinkedHashMap<>();

        Files(String prefix) {
            this.prefix = prefix;
        }

        void add(String path, byte[] bytes) {
            entries.put(prefix + path, bytes);
        }

        Map<String, byte[]> entries() {
            return entries;
        }

        byte[] zip() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue());
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        }
    }
}
