package info.oais.archive.manager.service.format;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.ElementMeaning;
import info.oais.infomodel.structure.manifest.ManifestWriter;
import info.oais.infomodel.structure.manifest.StructureDescription;
import info.oais.infomodel.structure.manifest.ViewDescription;
import info.oais.infomodel.structure.image.DecodedImage;
import info.oais.infomodel.structure.image.FitsImageWriter;
import info.oais.infomodel.structure.image.OaisStructureImage;
import info.oais.infomodel.structure.topcat.OaisStructureTableBuilder;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.votable.VOTableWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A Data Object in the archive as viewers see it: its Representation
 * Information as a manifest (see oais-structure-manifest) for TOPCAT and
 * SPLAT to open by URL, and its data decoded on the server and served as
 * VOTable, which TOPCAT opens with no plugin, or -- when its Representation
 * Information gives an image view -- as FITS, which SAOImage DS9, Aladin and
 * Fiji/ImageJ open (see oais-structure-image). All follow the Data Object's
 * {@code im:interpretedUsing} through its AND/OR groups to the structure
 * descriptions ({@code im:specificationLanguage}, {@code im:specificationText}),
 * view specifications ({@code im:ViewSpecification}) and element semantics
 * ({@code bridge:structuralPath}); its bits are at its
 * {@code im:hasStorageLocation}.
 */
@Service
public class DataObjectViewService {

    private final RdfStore store;
    private final StorageFetcher fetcher;

    public DataObjectViewService(RdfStore store, StorageFetcher fetcher) {
        this.store = store;
        this.fetcher = fetcher;
    }

    /**
     * An application that shows data, and what a Data Object's
     * Representation Information has to provide for it.
     *
     * @param mtype      the SAMP message type it's sent with
     * @param clientName its SAMP name, to send the data to it alone
     * @param format     what the data is sent as: {@link #VOTABLE} or {@link #FITS}
     */
    public record Viewer(String id, String label, String mtype, String clientName, String format) {
    }

    /** The data as VOTable: {@code /api/data-objects/{id}/votable}. */
    public static final String VOTABLE = "votable";
    /** The data as a FITS image: {@code /api/data-objects/{id}/fits}. */
    public static final String FITS = "fits";

    /** TOPCAT: any table. */
    public static final Viewer TOPCAT = new Viewer("topcat", "View with TOPCAT", "table.load.votable", "topcat", VOTABLE);
    /** SPLAT: a spectrum -- a table whose columns are all numeric (wavelength, flux, ...). */
    public static final Viewer SPLAT = new Viewer("splat", "View with SPLAT", "spectrum.load.ssa-generic", "splat",
            VOTABLE);
    /** SAOImage DS9: an image. */
    public static final Viewer DS9 = new Viewer("ds9", "View with DS9", "image.load.fits", "ds9", FITS);
    /** Aladin Desktop: an image. */
    public static final Viewer ALADIN = new Viewer("aladin", "View with Aladin", "image.load.fits", "aladin", FITS);

    private static final java.util.regex.Pattern COLUMN_TYPE =
            java.util.regex.Pattern.compile("<column\\b[^>]*\\btype=\"([^\"]+)\"");
    private static final Set<String> NUMERIC = Set.of("int", "long", "short", "byte", "float", "double",
            "biginteger", "bigdecimal");

    /**
     * The applications {@code dataObject}'s data can be viewed with: those
     * whose needs its Representation Information network -- followed from
     * {@code im:interpretedUsing} through its groups -- meets. Its bits need a
     * storage location and a structure description the server can apply (DFDL
     * or DRB SDF). Then a table view is enough for TOPCAT, and SPLAT also needs
     * the table view's columns all to be numeric, as a spectrum's are; an image
     * view is enough for DS9 and Aladin.
     */
    public List<Viewer> viewers(String dataObject) {
        Optional<DescribedData> described = decodable(dataObject);
        if (described.isEmpty()) {
            return List.of();
        }
        List<Viewer> viewers = new ArrayList<>();
        Optional<ViewDescription> table = described.get().view(ViewDescription.TABLE);
        if (table.isPresent()) {
            viewers.add(TOPCAT);
            String view = specification(table.get().iri()).map(Specification::text).orElse("");
            java.util.regex.Matcher m = COLUMN_TYPE.matcher(view);
            int columns = 0;
            boolean allNumeric = true;
            while (m.find()) {
                columns++;
                allNumeric &= NUMERIC.contains(m.group(1).toLowerCase());
            }
            if (columns >= 2 && allNumeric) {
                viewers.add(SPLAT);
            }
        }
        if (described.get().view(ViewDescription.IMAGE).isPresent()) {
            viewers.add(DS9);
            viewers.add(ALADIN);
        }
        return viewers;
    }

    /** {@code dataObject} as a manifest describes it, if the server can decode it (with DFDL or DRB SDF). */
    private Optional<DescribedData> decodable(String dataObject) {
        return describe(dataObject, URI::create).filter(d -> d.structures().stream().anyMatch(s ->
                s.language().equals(StructureDescription.DFDL) || s.language().equals(StructureDescription.DRB_SDF)));
    }

    /** A structure description's or view specification's text, and what kind of file it is. */
    public record Specification(String text, String language, boolean view) {
        public String mediaType() {
            if (view || StructureDescription.DFDL.equals(language) || StructureDescription.DRB_SDF.equals(language)) {
                return "application/xml";
            }
            return StructureDescription.KAITAI.equals(language) ? "application/x-yaml" : "text/plain";
        }

        public String extension() {
            if (view) {
                return "-view.xml";
            }
            if (StructureDescription.DFDL.equals(language)) {
                return ".dfdl.xsd";
            }
            if (StructureDescription.DRB_SDF.equals(language)) {
                return ".drb.xsd";
            }
            return StructureDescription.KAITAI.equals(language) ? ".ksy" : ".txt";
        }
    }

    /** The text of the structure description or view specification {@code iri}, if it is one. */
    public Optional<Specification> specification(String iri) {
        Model m = store.dataModel();
        Resource r = m.getResource(iri);
        String text = literal(r, m.createProperty(Ns.IM + "specificationText"));
        if (text == null) {
            return Optional.empty();
        }
        return Optional.of(new Specification(text, literal(r, m.createProperty(Ns.IM + "specificationLanguage")),
                r.hasProperty(RDF.type, m.createResource(Ns.IM + "ViewSpecification"))));
    }

    /**
     * Whether {@code dataObject} can be viewed: its bits have a storage
     * location, and its Representation Information gives a structure
     * description and a table or image view.
     */
    public boolean viewable(String dataObject) {
        return describe(dataObject, iri -> URI.create(iri)).filter(d -> !d.structures().isEmpty()
                && (d.view(ViewDescription.TABLE).isPresent() || d.view(ViewDescription.IMAGE).isPresent())).isPresent();
    }

    /**
     * {@code dataObject} as a manifest describes it, each description and view
     * at the location {@code specificationUrl} gives for its individual.
     * Empty if it has no storage location or no Representation Information.
     */
    public Optional<DescribedData> describe(String dataObject, Function<String, URI> specificationUrl) {
        Model m = store.dataModel();
        Resource data = m.getResource(dataObject);
        Statement storage = data.getProperty(m.createProperty(Ns.IM + "hasStorageLocation"));
        Property interpretedUsing = m.createProperty(Ns.IM + "interpretedUsing");
        if (storage == null || !storage.getObject().isURIResource() || !data.hasProperty(interpretedUsing)) {
            return Optional.empty();
        }
        Set<Resource> reached = new LinkedHashSet<>();
        Deque<Resource> todo = new ArrayDeque<>();
        data.listProperties(interpretedUsing).forEachRemaining(s -> {
            if (s.getObject().isResource()) {
                todo.push(s.getObject().asResource());
            }
        });
        List<Property> followed = Stream.of("hasGroupMember", "hasStructureRepresentationInformation",
                "hasSemanticRepresentationInformation", "hasOtherRepresentationInformation", "interpretedUsingRecurse")
                .map(p -> m.createProperty(Ns.IM + p)).toList();
        while (!todo.isEmpty() && reached.size() < 100_000) {
            Resource r = todo.pop();
            if (!reached.add(r)) {
                continue;
            }
            for (Property p : followed) {
                r.listProperties(p).forEachRemaining(s -> {
                    if (s.getObject().isResource()) {
                        todo.push(s.getObject().asResource());
                    }
                });
            }
        }
        List<StructureDescription> structures = new ArrayList<>();
        List<ViewDescription> views = new ArrayList<>();
        List<ElementMeaning> meanings = new ArrayList<>();
        Property text = m.createProperty(Ns.IM + "specificationText");
        for (Resource r : reached) {
            if (!r.isURIResource()) {
                continue;
            }
            String language = literal(r, m.createProperty(Ns.IM + "specificationLanguage"));
            if (language != null && r.hasProperty(text)) {
                structures.add(new StructureDescription(r.getURI(), language, specificationUrl.apply(r.getURI()),
                        literal(r, m.createProperty(Ns.IM + "generatedClassName"))));
            }
            if (r.hasProperty(RDF.type, m.createResource(Ns.IM + "ViewSpecification")) && r.hasProperty(text)) {
                String kind = literal(r, m.createProperty(Ns.IM + "viewKind"));
                views.add(new ViewDescription(r.getURI(), kind == null ? ViewDescription.TABLE : kind,
                        specificationUrl.apply(r.getURI())));
            }
            String path = literal(r, m.createProperty(Ns.BRIDGE + "structuralPath"));
            if (path != null) {
                Statement units = r.getProperty(m.createProperty(Ns.RICO + "hasUnitOfMeasurement"));
                Statement concept = r.getProperty(m.createProperty(Ns.BRIDGE + "representsConcept"));
                meanings.add(new ElementMeaning(path, literal(r, m.createProperty(Ns.RDFS + "label")),
                        literal(r, m.createProperty(Ns.SKOS + "definition")),
                        units == null || !units.getObject().isResource() ? null
                                : literal(units.getObject().asResource(), m.createProperty(Ns.RDFS + "label")),
                        concept == null || !concept.getObject().isURIResource() ? null
                                : concept.getObject().asResource().getURI()));
            }
        }
        structures.sort(Comparator.comparing(StructureDescription::iri));
        views.sort(Comparator.comparing(ViewDescription::iri));
        String label = literal(data, m.createProperty(Ns.RDFS + "label"));
        String name = label != null ? label : dataObject.substring(dataObject.lastIndexOf('/') + 1);
        return Optional.of(new DescribedData(dataObject, name, URI.create(storage.getObject().asResource().getURI()),
                structures, views, meanings));
    }

    /** The Representation Information manifest (Turtle) for {@code data}. */
    public static String manifest(DescribedData data) {
        return ManifestWriter.write(data.name() + ": Representation Information from the archive", List.of(data));
    }

    /**
     * Decodes {@code dataObject}'s bits -- fetched from its storage location
     * (see {@link StorageFetcher}) -- with its first usable structure
     * description and table view, and writes them to {@code out} as VOTable,
     * with each column's units and meaning.
     *
     * @throws IOException if it can't be viewed, or fetching or decoding fails
     */
    public void writeVotable(String dataObject, OutputStream out) throws IOException {
        withLocalCopy(dataObject, (remote, local) -> {
            StarTable table = OaisStructureTableBuilder.open(local);
            table.setName(remote.name());
            new VOTableWriter().writeStarTable(table, out);
        });
    }

    /**
     * Decodes {@code dataObject}'s bits as {@link #writeVotable} does, views
     * them with its image view, and writes them to {@code out} as FITS, with
     * the pixels' units and meaning, and where they came from, in its header.
     *
     * @throws IOException if it can't be viewed as an image, or fetching or decoding fails
     */
    public void writeFits(String dataObject, OutputStream out) throws IOException {
        withLocalCopy(dataObject, (remote, local) -> {
            DecodedImage image = OaisStructureImage.open(local);
            FitsImageWriter.write(new DecodedImage(remote.name(), image.pixels(), image.pixelClass(), image.unit(),
                    image.description(), List.of("Decoded from " + remote.data(),
                            "through the OAIS Representation Information of " + remote.iri())), out);
        });
    }

    private interface LocalWork {
        void run(DescribedData remote, DescribedData local) throws IOException;
    }

    /**
     * Runs {@code work} on {@code dataObject} as described in the archive and
     * as a local copy -- its bits fetched, each description and view written
     * to a file, for the engines, which read files -- then deletes the copy.
     */
    private void withLocalCopy(String dataObject, LocalWork work) throws IOException {
        Path dir = Files.createTempDirectory("archive-view-");
        try {
            DescribedData remote = describe(dataObject, iri -> URI.create(iri)).orElseThrow(() -> new IOException(
                    "This Data Object has no storage location for its bits, or no Representation Information"));
            DescribedData local = new DescribedData(remote.iri(), remote.name(), fetcher.fetch(remote.data(), dir).toUri(),
                    remote.structures().stream().map(s -> new StructureDescription(s.iri(), s.language(),
                            localCopy(s.iri(), dir), s.generatedClassName())).toList(),
                    remote.views().stream().map(v -> new ViewDescription(v.iri(), v.kind(), localCopy(v.iri(), dir)))
                            .toList(),
                    remote.meanings());
            work.run(remote, local);
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** A specification's text as a local file, for the engines, which read descriptions from files. */
    private URI localCopy(String iri, Path work) {
        Specification spec = specification(iri).orElseThrow();
        try {
            return Files.writeString(Files.createTempFile(work, "spec-", spec.extension()), spec.text(),
                    StandardCharsets.UTF_8).toUri();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String literal(Resource r, Property p) {
        Statement s = r.getProperty(p);
        if (s == null) {
            return null;
        }
        RDFNode o = s.getObject();
        String text = o.isLiteral() ? o.asLiteral().getLexicalForm() : null;
        return text == null || text.isBlank() ? null : text;
    }
}
