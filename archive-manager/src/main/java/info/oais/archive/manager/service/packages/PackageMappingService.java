package info.oais.archive.manager.service.packages;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.format.StorageFetcher;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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

    public PackageMappingService(RdfStore store, StorageFetcher fetcher) {
        this.store = store;
        this.fetcher = fetcher;
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
            return PackageInspector.inspect(file, name);
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** The mapping as CSV: one row per place a component is found, or per missing component. */
    public static String csv(PackageInspector.Inspection inspection) {
        StringBuilder sb = new StringBuilder("AIP component,Found,Where to find it in the package,What it says\r\n");
        for (PackageInspector.Finding f : inspection.findings()) {
            if (f.sources().isEmpty()) {
                sb.append(cell(f.component())).append(",no,,\r\n");
            }
            for (PackageInspector.Source s : f.sources()) {
                sb.append(cell(f.component())).append(",yes,").append(cell(s.where())).append(',')
                        .append(cell(s.value())).append("\r\n");
            }
        }
        return sb.toString();
    }

    private static String cell(String value) {
        String v = value == null ? "" : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
