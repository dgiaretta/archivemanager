package info.oais.archive.manager.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import info.oais.archive.manager.security.EditAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * A single shared password, not per-user accounts -- see the README for what
 * this does and doesn't protect against. The password is compared with
 * {@link java.security.MessageDigest#isEqual} (constant-time) rather than
 * {@code String.equals}, a cheap and easy hardening step against naive
 * timing attacks, but this is still fundamentally a casual deterrent, not a
 * real access-control system: no hashing at rest (it's one shared secret
 * read straight from config, there's no "at rest" to hash), no rate
 * limiting, no CSRF protection (this app doesn't include Spring Security),
 * and the session cookie itself is the only thing distinguishing a logged-in
 * request from a logged-out one.
 */
@Controller
public class LoginController {

    private final byte[] editPasswordBytes;

    public LoginController(@Value("${archive.edit-password:changeme}") String editPassword) {
        this.editPasswordBytes = editPassword.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(defaultValue = "/") String redirect,
                             @RequestParam(required = false) String error,
                             Model model) {
        model.addAttribute("redirect", safeRedirect(redirect));
        model.addAttribute("error", error != null);
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String password,
                           @RequestParam(defaultValue = "/") String redirect,
                           HttpServletRequest request) {
        String target = safeRedirect(redirect);
        byte[] submitted = password.getBytes(StandardCharsets.UTF_8);
        if (java.security.MessageDigest.isEqual(submitted, editPasswordBytes)) {
            HttpSession session = request.getSession(true);
            session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
            return "redirect:" + target;
        }
        String encoded = URLEncoder.encode(target, StandardCharsets.UTF_8);
        return "redirect:/login?redirect=" + encoded + "&error=1";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }

    /**
     * Only same-site relative paths are allowed as a redirect target -- the
     * "redirect" parameter is attacker-controllable (it round-trips through a
     * query string and a form field), so without this an open-redirect
     * vulnerability would let a crafted login link send a successfully
     * logged-in user on to an arbitrary external URL.
     */
    private String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank() || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/";
        }
        return redirect;
    }
}
