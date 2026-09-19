package info.oais.archive.manager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import info.oais.archive.manager.i18n.LanguagePreference;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * Makes {@code loggedIn}, {@code currentLanguage}, and {@code availableLanguages}
 * available in every Thymeleaf template without each page controller needing
 * to set them itself, so the shared nav fragment can show "Log in"/"Log out"
 * and the language switcher appropriately.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final LanguagePreference languagePreference;

    public GlobalModelAttributes(LanguagePreference languagePreference) {
        this.languagePreference = languagePreference;
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
}
