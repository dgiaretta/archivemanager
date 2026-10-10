package info.oais.archive.manager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import info.oais.archive.manager.i18n.LanguagePreference;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Makes {@code loggedIn}, {@code currentLanguage}, {@code availableLanguages}
 * and {@code jsMessages} available in every Thymeleaf template without each
 * page controller needing to set them itself, so the shared nav fragment can
 * show "Log in"/"Log out" and the language switcher appropriately, and the
 * pages' scripts can show their text in the interface's language.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    /** The messages the pages' scripts use: those whose keys start with {@code js.}. */
    private static final List<String> JS_KEYS = jsKeys();

    private final LanguagePreference languagePreference;
    private final MessageSource messages;

    public GlobalModelAttributes(LanguagePreference languagePreference, MessageSource messages) {
        this.languagePreference = languagePreference;
        this.messages = messages;
    }

    @ModelAttribute("loggedIn")
    public boolean loggedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(EditAuthInterceptor.SESSION_KEY));
    }

    @ModelAttribute("currentLanguage")
    public String currentLanguage() {
        return languagePreference.current();
    }

    @ModelAttribute("availableLanguages")
    public Map<String, String> availableLanguages() {
        return LanguagePreference.AVAILABLE;
    }

    /**
     * The scripts' messages in the interface's language, by key, as written
     * in the bundles: a message with arguments is a MessageFormat pattern
     * ({@code {0}}, and {@code ''} for an apostrophe), which the scripts fill
     * in themselves (see {@code fragments.html}).
     */
    @ModelAttribute("jsMessages")
    public Map<String, String> jsMessages() {
        Locale locale = languagePreference.locale();
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : JS_KEYS) {
            out.put(key.substring("js.".length()), messages.getMessage(key, null, key, locale));
        }
        return out;
    }

    private static List<String> jsKeys() {
        Properties p = new Properties();
        try (InputStream in = GlobalModelAttributes.class.getResourceAsStream("/i18n/messages.properties")) {
            if (in != null) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p.stringPropertyNames().stream().filter(k -> k.startsWith("js.")).sorted().toList();
    }
}
