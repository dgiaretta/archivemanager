package info.oais.archive.manager.model;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Alphabetical order for what the interface lists, so items are easy to
 * find: by the text people read, ignoring case but not accents (so "árvore"
 * sorts with "arvore", just after it), with empty text last.
 */
public final class Alphabetical {

    private Alphabetical() {
    }

    /** Text in alphabetical order. */
    public static final Comparator<String> TEXT = (a, b) -> {
        boolean noA = a == null || a.isBlank();
        boolean noB = b == null || b.isBlank();
        if (noA || noB) {
            return Boolean.compare(noA, noB);
        }
        Collator collator = Collator.getInstance(Locale.ROOT);
        collator.setStrength(Collator.SECONDARY);
        int c = collator.compare(a.strip(), b.strip());
        return c != 0 ? c : a.compareTo(b);
    };

    /** Items in the alphabetical order of {@code text}. */
    public static <T> Comparator<T> by(Function<T, String> text) {
        return Comparator.comparing(text, TEXT);
    }

    /** Resources by their title. */
    public static final Comparator<ResourceSummary> RESOURCES = by(ResourceSummary::title);
}
