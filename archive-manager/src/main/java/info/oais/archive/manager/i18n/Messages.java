package info.oais.archive.manager.i18n;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * The interface's text, from the message bundles ({@code i18n/messages*.properties}),
 * in the current session's language (see {@link LanguagePreference}) -- for what
 * controllers put on a page themselves, such as titles and error messages.
 */
@Component
public class Messages {

    private final MessageSource source;
    private final LanguagePreference languages;

    public Messages(MessageSource source, LanguagePreference languages) {
        this.source = source;
        this.languages = languages;
    }

    /** The message {@code key}, with {@code args} filled in, in the session's language. */
    public String get(String key, Object... args) {
        return source.getMessage(key, args.length == 0 ? null : args, languages.locale());
    }
}
