package info.oais.archive.manager.i18n;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fixed English labels that services produce -- an AIP component's name and
 * status, a Transformation check's outcome -- in the interface's language,
 * for templates: {@code ${@labels.of('aip', p.name())}}. The services keep
 * English, since the same labels go into files (a BagIt bag's
 * {@code oais-aip-components.txt}); the page looks each up by a key made from
 * {@code prefix} and the English, e.g. {@code aip.content_information}, and
 * shows the English when there's none. A label starting with a count -- "3
 * machine-readable descriptions" -- is looked up with an {@code n} in its
 * place ({@code aip.n_machine_readable_descriptions...}) and the count as the
 * message's argument.
 */
@Component("labels")
public class Labels {

    private static final Pattern COUNT = Pattern.compile("(\\d+) (.*)", Pattern.DOTALL);

    private final MessageSource messages;
    private final LanguagePreference languages;

    public Labels(MessageSource messages, LanguagePreference languages) {
        this.messages = messages;
        this.languages = languages;
    }

    /** {@code english} in the interface's language, if the bundles have it; else as it is. */
    public String of(String prefix, String english) {
        if (english == null || english.isBlank()) {
            return english;
        }
        Locale locale = languages.locale();
        Matcher m = COUNT.matcher(english);
        if (m.matches()) {
            String found = messages.getMessage(key(prefix, "n " + m.group(2)), new Object[] {m.group(1)}, null, locale);
            if (found != null) {
                return found;
            }
        }
        return messages.getMessage(key(prefix, english), null, english, locale);
    }

    /** E.g. {@code aip} and "Access Rights Information" -> {@code aip.access_rights_information}. */
    static String key(String prefix, String english) {
        return prefix + "." + english.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
