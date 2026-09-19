package info.oais.archive.manager.rdf;

import java.util.List;

/**
 * Picks one label out of a set of candidates that may carry different
 * {@code xml:lang} tags (e.g. the real RiC-O ontology labels every class in
 * English, French, and Spanish, and every property in German too), rather
 * than showing every language as a separate, duplicate entry.
 *
 * <p>Preference order: an exact match for the requested language, then
 * English, then an untagged (no {@code xml:lang} at all) label, then
 * whatever's available, then a caller-supplied fallback (typically the
 * resource's local name).
 */
public final class LabelPicker {

    private LabelPicker() {
    }

    /** One candidate label: its text and its language tag ("" if untagged). */
    public record Candidate(String label, String lang) {
    }

    public static String pick(List<Candidate> candidates, String preferredLang, String fallback) {
        String exact = null;
        String english = null;
        String untagged = null;
        String any = null;

        for (Candidate c : candidates) {
            if (c.label() == null) {
                continue;
            }
            String lang = c.lang() == null ? "" : c.lang();
            if (any == null) {
                any = c.label();
            }
            if (lang.isEmpty() && untagged == null) {
                untagged = c.label();
            }
            if ("en".equalsIgnoreCase(lang) && english == null) {
                english = c.label();
            }
            if (preferredLang != null && preferredLang.equalsIgnoreCase(lang) && exact == null) {
                exact = c.label();
            }
        }

        if (exact != null) {
            return exact;
        }
        if (english != null) {
            return english;
        }
        if (untagged != null) {
            return untagged;
        }
        if (any != null) {
            return any;
        }
        return fallback;
    }
}
