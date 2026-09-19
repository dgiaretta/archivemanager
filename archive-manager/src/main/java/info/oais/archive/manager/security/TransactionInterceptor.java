package info.oais.archive.manager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.query.ReadWrite;
import info.oais.archive.manager.rdf.RdfStore;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * TDB2 requires every read or write to run inside an explicit transaction --
 * there's no auto-commit fallback. Rather than have every service method
 * (there are dozens of SPARQL call sites across {@code ArchiveService},
 * {@code GraphService}, {@code OntologyService}, {@code EditService}, the
 * SPARQL console...) open and close its own transaction, one is opened here
 * for the whole request before it reaches a controller, and committed (or
 * aborted, if the request failed) once the response -- including view
 * rendering -- is complete. Everything downstream just calls
 * {@code store.dataModel()} / {@code store.queryModel()} as before and
 * transparently runs inside whatever transaction is already open on the
 * current thread.
 *
 * <p>GET requests get a READ transaction; POST/PUT/DELETE/PATCH get WRITE.
 * This is a simple, method-based rule rather than inspecting which specific
 * endpoint is being called, so a couple of POST endpoints that don't
 * actually write anything (e.g. the read-only SPARQL console) end up
 * holding TDB2's single write-transaction slot briefly and unnecessarily --
 * harmless at this app's single-user scale, but worth knowing if this ever
 * needs to serve concurrent editors.
 */
@Component
public class TransactionInterceptor implements HandlerInterceptor {

    private final RdfStore store;

    public TransactionInterceptor(RdfStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        store.beginTransaction(isWrite(request.getMethod()) ? ReadWrite.WRITE : ReadWrite.READ);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                 Exception ex) {
        store.endTransaction(ex == null);
    }

    private boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
    }
}
