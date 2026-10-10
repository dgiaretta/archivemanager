package info.oais.archive.manager.security;

import info.oais.archive.manager.i18n.LanguagePreference;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TransactionInterceptor transactionInterceptor;
    private final EditAuthInterceptor editAuthInterceptor;

    public WebConfig(TransactionInterceptor transactionInterceptor, EditAuthInterceptor editAuthInterceptor) {
        this.transactionInterceptor = transactionInterceptor;
        this.editAuthInterceptor = editAuthInterceptor;
    }

    /**
     * The interface's language is the session's preferred language (see
     * {@link LanguagePreference}), the one the switcher in the top bar sets --
     * for its text (the message bundles) as for the ontology's labels.
     */
    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public java.util.Locale resolveLocale(HttpServletRequest request) {
                HttpSession session = request.getSession(false);
                Object language = session == null ? null : session.getAttribute(LanguagePreference.SESSION_KEY);
                return LanguagePreference.localeOf(language instanceof String s ? s : LanguagePreference.DEFAULT);
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, java.util.Locale locale) {
                request.getSession(true).setAttribute(LanguagePreference.SESSION_KEY,
                        locale == null ? LanguagePreference.DEFAULT : locale.getLanguage());
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // TransactionInterceptor first (order 0): it must wrap every request that
        // might touch the store, including ones EditAuthInterceptor goes on to
        // block, so the transaction it opens is always properly closed via
        // afterCompletion regardless of what happens later in the chain.
        // EditAuthInterceptor itself decides path-by-path (including HTTP
        // method) which requests actually need a login, and returns true
        // immediately for everything else.
        registry.addInterceptor(transactionInterceptor).order(0);
        registry.addInterceptor(editAuthInterceptor).order(1);
    }
}
