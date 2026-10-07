package info.oais.archive.manager.web;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
public class HelpController {

    /**
     * The structure adapters' own README files, copied into the jar at build
     * time (see the {@code docs/engines} resources in pom.xml), by the name
     * used in their URL -> page title.
     */
    private static final Map<String, String> ENGINE_READMES = Map.of(
            "dfdl", "DFDL (Apache Daffodil)",
            "kaitai", "Kaitai Struct",
            "drb", "DRB (Java Data Request Broker)",
            "east", "EAST (CCSDS 644.0-B-3)");

    // Our own bundled files, but escaped anyway: nothing in them needs raw HTML.
    private final Parser markdown = Parser.builder().build();
    private final HtmlRenderer html = HtmlRenderer.builder().escapeHtml(true).build();

    @GetMapping("/help")
    public String help() {
        return "help";
    }

    /** Renders one structure adapter's README (e.g. {@code /help/engines/dfdl}) as a page. */
    @GetMapping("/help/engines/{engine}")
    public String engineReadme(@PathVariable String engine, Model model) throws IOException {
        String title = ENGINE_READMES.get(engine);
        if (title == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String text;
        try (InputStream in = new ClassPathResource("docs/engines/README-" + engine.toUpperCase() + ".md").getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        model.addAttribute("title", title);
        model.addAttribute("html", html.render(markdown.parse(text)));
        return "help-engine";
    }
}
