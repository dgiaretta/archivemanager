package info.oais.archive.manager.i18n;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which language to prefer when a class or property has labels in more than
 * one (the real RiC-O ontology labels everything in English, French, and
 * Spanish, and classes additionally in German), and the interface's own text
 * is in ({@link #localeOf}, and the message bundles
 * {@code i18n/messages*.properties}): English, Dhivehi, French, Italian,
 * Brazilian Portuguese or Spanish -- German has the ontology's labels but no
 * interface text, so shows it in English. Stored per-session, set via
 * {@code GET /language/{lang}}, defaulting to English.
 *
 * <p>Dhivehi is written right to left: each page's {@code dir} follows the
 * interface's language. No bundled ontology has {@code @dv} labels, so class
 * and property names stay English in Dhivehi
 * ({@link info.oais.archive.manager.rdf.LabelPicker}'s fallback), until
 * {@code rdfs:label ...@dv} triples are added -- then they are used at once.
 * This is unrelated to whether the <i>data itself</i> (catalogue titles,
 * descriptions, etc.) can be in Dhivehi, which it already fully can (see the
 * README's internationalisation section).
 *
 * <p>Read from services (e.g. {@code OntologyService}, {@code ArchiveService})
 * via {@link #current()} rather than passed as a parameter, since it needs to
 * reach deep, shared label-resolution code without threading a language
 * argument through every method that might resolve a label. This uses
 * {@link RequestContextHolder} to reach the current session, which only
 * exists while an HTTP request is being handled -- code that runs outside
 * one (startup-time ontology queries, most notably) has no session to read,
 * so {@link #current()} falls back to {@link #DEFAULT} in that case rather
 * than throwing, the same lesson learned from the transaction-per-request
 * fix: nothing that runs at application startup can assume a request exists.
 */
@Component
public class LanguagePreference {

    public static final String SESSION_KEY = "preferredLanguage";
    public static final String DEFAULT = "en";

    /** The languages offered in the UI switcher, each by its own name. */
    public static final Map<String, String> AVAILABLE = buildAvailable();

    private static Map<String, String> buildAvailable() {
        // In alphabetical order of their codes, as the switcher shows them.
        Map<String, String> m = new LinkedHashMap<>();
        m.put("de", "Deutsch");
        m.put("dv", "ދިވެހި");
        m.put("en", "English");
        m.put("es", "Español");
        m.put("fr", "Français");
        m.put("it", "Italiano");
        m.put("pt", "Português (Brasil)");
        return m;
    }

    /**
     * The locale the interface's text is shown in for {@code language}: Portuguese
     * is Brazilian Portuguese ({@code pt-BR}); the others are just their language.
     * German has no interface text of its own (see
     * {@code i18n/messages*.properties}), so shows it in English, and uses
     * German for the ontology's labels.
     */
    public static java.util.Locale localeOf(String language) {
        if (language == null || language.isBlank()) {
            return java.util.Locale.ENGLISH;
        }
        return "pt".equals(language) ? java.util.Locale.forLanguageTag("pt-BR")
                : java.util.Locale.forLanguageTag(language);
    }

    /** The locale of the current session's preferred language. */
    public java.util.Locale locale() {
        return localeOf(current());
    }

    /** The current session's preferred language, or {@link #DEFAULT} if unset or there's no active request. */
    public String current() {
        HttpSession session = currentSession();
        if (session == null) {
            return DEFAULT;
        }
        Object value = session.getAttribute(SESSION_KEY);
        return (value instanceof String s && !s.isBlank()) ? s : DEFAULT;
    }

    private HttpSession currentSession() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getSession(false);
        } catch (IllegalStateException e) {
            // No request being handled on this thread right now (application startup,
            // eager bean construction, etc.) -- there's nothing to read a preference from.
            return null;
        }
    }
}
