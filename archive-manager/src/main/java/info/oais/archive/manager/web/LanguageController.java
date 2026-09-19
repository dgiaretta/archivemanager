package info.oais.archive.manager.web;

import jakarta.servlet.http.HttpServletRequest;
import info.oais.archive.manager.i18n.LanguagePreference;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;

/**
 * A plain GET (not a form POST) since this only sets a display preference,
 * not archive data -- the same reasoning language switcher links on most
 * sites use. It doesn't touch the triple store at all, so it's also outside
 * anything {@code TransactionInterceptor} needs to open a transaction for.
 */
@Controller
public class LanguageController {

    @GetMapping("/language/{lang}")
    public String setLanguage(@PathVariable String lang, HttpServletRequest request) {
        String normalized = lang == null ? LanguagePreference.DEFAULT : lang.trim().toLowerCase();
        if (!LanguagePreference.AVAILABLE.containsKey(normalized)) {
            normalized = LanguagePreference.DEFAULT;
        }
        request.getSession(true).setAttribute(LanguagePreference.SESSION_KEY, normalized);
        return "redirect:" + refererPath(request);
    }

    /**
     * Extracts just the path+query from the Referer header, discarding its
     * scheme/host, so the redirect can only ever land back on this same app
     * regardless of what a (browser-set, not attacker-supplied-as-a-parameter)
     * Referer value contains.
     */
    private String refererPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                return "/";
            }
            String query = uri.getRawQuery();
            return query == null ? path : path + "?" + query;
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
