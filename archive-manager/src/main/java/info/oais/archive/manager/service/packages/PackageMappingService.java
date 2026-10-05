package info.oais.archive.manager.service.packages;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.format.StorageFetcher;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Maps the package an AIP (or anything else) is stored as -- the file at its
 * {@code im:hasStorageLocation}, e.g. an Eternal AIP as a .7z -- to the
 * components OAIS requires of an AIP, with {@link PackageInspector}: where in
 * the package each one is, and what it says there.
 */
@Service
public class PackageMappingService {

    private static final List<String> PACKAGE_EXTENSIONS = List.of(".7z", ".zip", ".tar", ".tar.gz", ".tgz");

    private final RdfStore store;
    private final StorageFetcher fetcher;
    private final QueryRunner q;
    private final ArchiveService archive;

    public PackageMappingService(RdfStore store, StorageFetcher fetcher, QueryRunner q, ArchiveService archive) {
        this.store = store;
        this.fetcher = fetcher;
        this.q = q;
        this.archive = archive;
    }

    /** The storage location of {@code iri} that is a package, if it has one. */
    public Optional<URI> packageLocation(String iri) {
        Model m = store.dataModel();
        for (Statement s : m.getResource(iri).listProperties(m.createProperty(Ns.IM + "hasStorageLocation")).toList()) {
            if (s.getObject().isURIResource()) {
                URI location = URI.create(s.getObject().asResource().getURI());
                String path = location.getPath() == null ? "" : location.getPath().toLowerCase(Locale.ROOT);
                if (PACKAGE_EXTENSIONS.stream().anyMatch(path::endsWith)) {
                    return Optional.of(location);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Fetches the package at {@code location} and maps it.
     *
     * @throws IOException if it can't be fetched or read
     */
    public PackageInspector.Inspection inspect(URI location) throws IOException {
        Path dir = Files.createTempDirectory("package-inspect-");
        try {
            Path file = fetcher.fetch(location, dir);
            String name = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
            return withSemanticAips(PackageInspector.inspect(file, name));
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /**
     * The Semantic Representation Information the upload spreadsheet's
     * Semantics identifies by UUID -- a separate AIP -- found in this archive,
     * if it's described here: anything whose IRI, or any of whose values,
     * holds the UUID (e.g. an AIP whose storage location is that AIP's file).
     */
    private PackageInspector.Inspection withSemanticAips(PackageInspector.Inspection inspection) {
        List<PackageInspector.Finding> findings = new ArrayList<>();
        for (PackageInspector.Finding f : inspection.findings()) {
            if (!f.component().equals("Semantic Representation Information")) {
                findings.add(f);
                continue;
            }
            List<PackageInspector.Source> sources = new ArrayList<>(f.sources());
            for (PackageInspector.Source s : f.sources()) {
                String uuid = s.where().matches("metadata\\.csv: (dc\\.)?[Ss]emantics") ? PackageInspector.uuid(s.value())
                        : null;
                if (uuid == null) {
                    continue;
                }
                List<Map<String, String>> found = q.select(store.dataModel(), Ns.PREFIXES + """
                        SELECT DISTINCT ?s WHERE {
                          ?s ?p ?o .
                          FILTER(CONTAINS(LCASE(STR(?s)), "%1$s") || CONTAINS(LCASE(STR(?o)), "%1$s"))
                        } LIMIT 5
                        """.formatted(uuid));
                if (found.isEmpty()) {
                    sources.add(new PackageInspector.Source("this archive", "no AIP with the identifier " + uuid
                            + " is described in this archive (it may be held elsewhere, e.g. in Eternal)"));
                }
                for (Map<String, String> r : found) {
                    String iri = r.get("s");
                    sources.add(new PackageInspector.Source("this archive", archive.label(iri) + " (" + iri + ")",
                            "/resource/" + archive.encodeId(iri)));
                }
            }
            findings.add(new PackageInspector.Finding(f.component(), f.depth(), f.required(), f.found(), sources,
                    f.note(), f.gaps()));
        }
        return new PackageInspector.Inspection(inspection.archiveFormat(), inspection.size(), inspection.bagName(),
                inspection.bagitVersion(), inspection.bagInfo(), inspection.bagCheck(), inspection.files(), findings,
                inspection.spreadsheet(), inspection.spreadsheetColumns(), inspection.columnDefinitions(),
                inspection.notes());
    }

    /**
     * The mapping as CSV: one row per place a component is found, and per
     * expected entry of the upload spreadsheet that isn't filled in.
     */
    public static String csv(PackageInspector.Inspection inspection) {
        StringBuilder sb = new StringBuilder("AIP component,Found,Where to find it in the package,What it says\r\n");
        for (PackageInspector.Finding f : inspection.findings()) {
            for (PackageInspector.Finding.Row r : f.rows()) {
                String found = !r.gap() ? "yes" : f.found() ? "not filled in" : f.required() ? "no" : "no (optional)";
                sb.append(cell(f.component())).append(',').append(found).append(',').append(cell(r.where()))
                        .append(',').append(cell(r.value())).append("\r\n");
            }
        }
        return sb.toString();
    }

    private static String cell(String value) {
        String v = value == null ? "" : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
