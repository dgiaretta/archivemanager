package info.oais.archive.manager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Gates every endpoint that creates, edits, or deletes data behind a login.
 * Everything else in the app (browsing records/agents/activities/mandates,
 * the OAIS mapping view, the graph, the SPARQL console) stays open.
 *
 * <p>Deliberately matched explicitly path-by-path rather than via a broad
 * {@code addPathPatterns("/entities/**")} registration: several of these
 * share a path with an open, read-only sibling that must NOT be gated
 * (e.g. {@code GET /entities} is the open browse list, but
 * {@code POST /entities} creates a new entity and must be gated) --
 * distinguishing them needs the HTTP method, which path-pattern
 * registration alone can't express, so the decision is made explicitly in
 * code here instead of relying on framework pattern matching.
 */
@Component
public class EditAuthInterceptor implements HandlerInterceptor {

    /** Session attribute set to {@code Boolean.TRUE} once the edit password has been entered correctly. */
    public static final String SESSION_KEY = "editAuthenticated";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = requestPath(request);
        String method = request.getMethod();

        if (!requiresAuth(path, method)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        boolean authenticated = session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY));
        if (authenticated) {
            return true;
        }

        if ("GET".equals(method)) {
            String target = path + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
            String encoded = URLEncoder.encode(target, StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/login?redirect=" + encoded);
        } else {
            // A logged-out user reaching a protected POST directly (rather than
            // through the already-gated edit page that links to it) is not a
            // flow the normal UI produces -- just refuse it.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                    "<p>Not logged in. <a href=\"" + request.getContextPath() + "/login\">Log in</a> to make changes.</p>");
        }
        return false;
    }

    private String requestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private boolean requiresAuth(String path, String method) {
        if (path.equals("/records/new") && method.equals("GET")) {
            return true;
        }
        if (path.equals("/records") && method.equals("POST")) {
            return true;
        }
        if (path.equals("/entities/new") && method.equals("GET")) {
            return true;
        }
        if (path.equals("/entities") && method.equals("POST")) {
            return true;
        }
        if (path.matches("^/entities/[^/]+/edit$") && method.equals("GET")) {
            return true;
        }
        if (path.matches("^/entities/[^/]+/(types|properties|relationships|delete)(/delete)?$")
                && method.equals("POST")) {
            return true;
        }
        if (path.equals("/entities/import") && (method.equals("GET") || method.equals("POST"))) {
            return true;
        }
        if (path.equals("/catalogue/upload") && (method.equals("GET") || method.equals("POST"))) {
            return true;
        }
        if (path.equals("/diagnostics/xlsx-test") && (method.equals("GET") || method.equals("POST"))) {
            return true;
        }
        if (path.matches("^/accession-register/[^/]+/delete$") && method.equals("POST")) {
            return true;
        }
        if (path.equals("/accession-register/delete-all") && method.equals("POST")) {
            return true;
        }
        if (path.equals("/diagnostics/tdb2-roundtrip") && method.equals("POST")) {
            return true;
        }
        // Unlike every other rule above, this is a plain prefix match rather than a
        // single path/regex: every /repinfo-tools/... route (GET and POST alike) is
        // part of the same create-a-description-and-save-it-to-the-archive flow, with
        // no open, read-only sibling sharing a path the way e.g. GET /entities does --
        // so there's nothing here that needs the method-level disambiguation the other
        // rules exist for.
        if (path.startsWith("/repinfo-tools")) {
            return true;
        }
        // The REST counterparts of the rules above (ArchiveApiController) -- same
        // shared-path-with-an-open-GET-sibling situation as /entities and /records
        // themselves, so the same explicit method-aware matching is needed here too.
        if (path.equals("/api/records") && method.equals("POST")) {
            return true;
        }
        if (path.equals("/api/entities") && method.equals("POST")) {
            return true;
        }
        if (path.matches("^/api/entities/[^/]+$") && method.equals("DELETE")) {
            return true;
        }
        if (path.matches("^/api/entities/[^/]+/(types|properties|relationships)$")
                && (method.equals("POST") || method.equals("DELETE"))) {
            return true;
        }
        return false;
    }
}
