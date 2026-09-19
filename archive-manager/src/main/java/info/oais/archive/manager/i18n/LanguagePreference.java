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
 * Spanish, and classes additionally in German). Stored per-session, set via
 * {@code GET /language/{lang}}, defaulting to English.
 *
 * <p><b>Dhivehi (dv) is listed as available, but is currently inert.</b> The
 * mechanism itself is fully generic -- {@link info.oais.archive.manager.rdf.LabelPicker}
 * accepts any language code, not a hard-coded set -- but nothing in this
 * project's bundled ontology/vocabulary files (RiC-O, OAIS, Dublin Core, or
 * the {@code nam:} extension vocabulary) currently has an {@code @dv}-tagged
 * {@code rdfs:label}, since accurately translating ~150 ontology/technical
 * terms into Dhivehi needs a qualified native-speaker translator, not
 * something to fabricate here. Selecting "dv" today falls back to English
 * (per {@code LabelPicker}'s documented fallback chain) rather than erroring
 * or showing nothing -- so it's safe to leave listed, and the moment
 * accurate {@code rdfs:label ...@dv} triples are added anywhere in the
 * ontology graph, choosing Dhivehi starts using them immediately, with no
 * further code changes. This only concerns ontology/UI *labels* (class and
 * property names in the entity editor's pickers) -- it's unrelated to
 * whether the *data itself* (catalogue titles, descriptions, etc.) can be
 * in Dhivehi, which it already fully can (see the CSS/font/encoding
 * support noted in the README's internationalisation section).
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

    /** The languages offered in the UI switcher. See the class-level note on "dv" specifically. */
    public static final Map<String, String> AVAILABLE = buildAvailable();

    private static Map<String, String> buildAvailable() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("en", "English");
        m.put("fr", "Français");
        m.put("es", "Español");
        m.put("de", "Deutsch");
        m.put("dv", "Dhivehi");
        return m;
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
