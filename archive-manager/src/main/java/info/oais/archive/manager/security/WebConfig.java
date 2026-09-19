package info.oais.archive.manager.security;

import org.springframework.context.annotation.Configuration;
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
