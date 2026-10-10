package info.oais.archive.manager.service.format;

import info.oais.archive.manager.rdf.Ns;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The keywords the FITS Standard 4.0 defines, each with the section defining
 * it and its definition there (abridged), from {@code fits/fits-keywords.tsv}:
 * what a FITS header's keyword records mean. Indexed keywords are named as in
 * the Standard -- {@code NAXISn}, {@code CTYPEi}, {@code PCi_j},
 * {@code RADESYSa} -- and match the keywords they stand for ({@code NAXIS2},
 * {@code CTYPE1}, {@code PC1_2}, {@code RADESYSB}).
 *
 * <p>As Semantic Representation Information it's a SKOS concept scheme,
 * {@value #SCHEME}, one concept per keyword ({@link #model()}): per-file FITS
 * descriptions (see {@link FitsDescriber}) point each header record at its
 * keyword's concept, and their Semantic Representation Information at the
 * scheme.
 */
public final class FitsDictionary {

    /** The dictionary as a SKOS concept scheme, typed as Semantic Representation Information. */
    public static final String SCHEME = "http://ontology.oais.info/fits/keywords";
    /** Where its concepts are: this followed by the keyword as the Standard names it, e.g. {@code NAXISn}. */
    public static final String CONCEPTS = "http://ontology.oais.info/fits/keyword/";
    static final String STANDARD_URL = "https://fits.gsfc.nasa.gov/fits_standard.html";

    /**
     * One keyword.
     *
     * @param keyword    as the Standard names it, e.g. {@code NAXISn}; empty for a record with a blank keyword
     * @param section    the section of the Standard defining it, e.g. {@code 4.4.1.1}
     * @param value      the kind of value, where the Standard's keyword tables state it; else empty
     * @param definition its definition in the Standard, abridged
     */
    public record Entry(String keyword, String section, String value, String definition) {

        /** Its concept in the dictionary. */
        public String conceptIri() {
            return CONCEPTS + (keyword.isEmpty() ? "blank" : keyword);
        }

        /** As the Standard names it, with "(blank)" for the blank keyword. */
        public String label() {
            return keyword.isEmpty() ? "(blank)" : keyword;
        }
    }

    private record Matcher(Pattern pattern, Entry entry) {
    }

    private static final FitsDictionary INSTANCE = load();

    private final List<Entry> entries;
    private final List<Matcher> matchers;

    private FitsDictionary(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        List<Matcher> m = new ArrayList<>();
        for (Entry e : entries) {
            m.add(new Matcher(pattern(e.keyword()), e));
        }
        this.matchers = List.copyOf(m);
    }

    public static FitsDictionary get() {
        return INSTANCE;
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * The entry for {@code keyword} as written in a header, e.g.
     * {@code NAXIS2} or {@code DATE-OBS}: the keyword itself if the Standard
     * defines it, else the indexed keyword it's an instance of.
     */
    public Optional<Entry> lookup(String keyword) {
        String k = keyword == null ? "" : keyword.strip();
        for (Entry e : entries) {
            if (e.keyword().equals(k)) {
                return Optional.of(e);
            }
        }
        for (Matcher m : matchers) {
            if (m.pattern().matcher(k).matches()) {
                return Optional.of(m.entry());
            }
        }
        return Optional.empty();
    }

    /**
     * A keyword as named in the Standard as a pattern: lower-case {@code n},
     * {@code i}, {@code j}, {@code m} stand for an index (1 to 3 digits), and
     * {@code a} for an alternate description's letter, or none.
     */
    static Pattern pattern(String keyword) {
        StringBuilder sb = new StringBuilder();
        for (char c : keyword.toCharArray()) {
            switch (c) {
                case 'n', 'i', 'j', 'm' -> sb.append("[0-9]{1,3}");
                case 'a' -> sb.append("[A-Z]?");
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(sb.toString());
    }

    /**
     * The dictionary as RDF: the concept scheme {@value #SCHEME}, typed as
     * Semantic Representation Information, and a concept per keyword with its
     * label and notation (the keyword), definition, and the section defining it.
     */
    public Model model() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("skos", Ns.SKOS);
        m.setNsPrefix("rdfs", Ns.RDFS);
        m.setNsPrefix("im", Ns.IM);
        m.setNsPrefix("fits", CONCEPTS);
        Property label = m.createProperty(Ns.RDFS + "label");
        Property inScheme = m.createProperty(Ns.SKOS + "inScheme");
        Resource scheme = m.createResource(SCHEME)
                .addProperty(RDF.type, m.createResource(Ns.SKOS + "ConceptScheme"))
                .addProperty(RDF.type, m.createResource(Ns.IM + "SemanticRepresentationInformation"))
                .addProperty(label, "FITS keyword dictionary (FITS Standard 4.0)")
                .addProperty(m.createProperty(Ns.RDFS + "comment"), "What the keywords of FITS headers mean: the "
                        + entries.size() + " keywords defined by the FITS Standard 4.0 (IAU FITS Working Group, 2018),"
                        + " each with the section defining it and its definition there, abridged. Indexed keywords "
                        + "are named as in the Standard: n, i, j and m stand for an index, a for an alternate "
                        + "coordinate description's letter (e.g. NAXISn for NAXIS1, NAXIS2, ...).")
                .addProperty(m.createProperty(Ns.RDFS + "seeAlso"), m.createResource(STANDARD_URL));
        for (Entry e : entries) {
            Resource concept = m.createResource(e.conceptIri())
                    .addProperty(RDF.type, m.createResource(Ns.SKOS + "Concept"))
                    .addProperty(inScheme, scheme)
                    .addProperty(label, e.label())
                    .addProperty(m.createProperty(Ns.SKOS + "prefLabel"), e.label())
                    .addProperty(m.createProperty(Ns.SKOS + "notation"), e.label())
                    .addProperty(m.createProperty(Ns.SKOS + "definition"), e.definition())
                    .addProperty(m.createProperty(Ns.SKOS + "scopeNote"), "Defined in section " + e.section()
                            + " of the FITS Standard 4.0" + (e.value().isEmpty() ? "." : "; its value is "
                            + e.value() + "."));
            scheme.addProperty(m.createProperty(Ns.SKOS + "hasTopConcept"), concept);
        }
        return m;
    }

    private static FitsDictionary load() {
        try (InputStream in = FitsDictionary.class.getResourceAsStream("/fits/fits-keywords.tsv")) {
            if (in == null) {
                throw new IllegalStateException("fits/fits-keywords.tsv is missing");
            }
            List<Entry> entries = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                if (f.length < 4) {
                    continue;
                }
                String keyword = f[0].strip().equals("(blank)") ? "" : f[0].strip();
                entries.add(new Entry(keyword, f[1].strip(), f[2].strip(), f[3].strip()));
            }
            return new FitsDictionary(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
