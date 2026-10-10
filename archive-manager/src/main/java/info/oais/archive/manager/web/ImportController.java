package info.oais.archive.manager.web;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import info.oais.archive.manager.rdf.RdfStore;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Bulk import of Turtle data into the running archive, for exactly the case
 * of "I have a whole .ttl file's worth of example/real data and don't want to
 * build it up one field at a time through the entity editor" -- e.g. pasting
 * in the content of {@code sample-data-science.ttl} directly.
 *
 * <p>Gated behind the same edit-password login as everything else that
 * writes data (see {@code EditAuthInterceptor}) -- this doesn't introduce any
 * new privilege, since anyone who can already reach the entity editor can
 * already add arbitrary triples one at a time; it's a faster path to the
 * same place, not a new capability.
 *
 * <p>Pasted content must be complete, valid Turtle, including its own
 * {@code @prefix} declarations (Jena's parser doesn't inherit prefixes from
 * the target model) -- pasting the full contents of one of this project's
 * own {@code rdf/*.ttl} files works directly, since those are already
 * complete, self-contained documents.
 *
 * <p>Safety: the pasted text is parsed into a throwaway in-memory model
 * first. If parsing fails, nothing touches the real data graph at all --
 * the error is shown back to the user with Jena's own message, and they can
 * fix and resubmit. Only a successful parse gets merged into the live graph.
 */
@Controller
public class ImportController {

    private final RdfStore store;

    private final info.oais.archive.manager.i18n.Messages messages;

    public ImportController(RdfStore store, info.oais.archive.manager.i18n.Messages messages) {
        this.messages = messages;
        this.store = store;
    }

    @GetMapping("/entities/import")
    public String form(Model model) {
        return "entities/import";
    }

    @PostMapping("/entities/import")
    public String doImport(@RequestParam String turtle, Model model) {
        model.addAttribute("turtle", turtle);

        if (turtle == null || turtle.isBlank()) {
            model.addAttribute("error", messages.get("error.pasteTurtle"));
            return "entities/import";
        }

        org.apache.jena.rdf.model.Model parsed = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(parsed, new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);
        } catch (RiotException e) {
            model.addAttribute("error", messages.get("error.notTurtle", e.getMessage()));
            return "entities/import";
        }

        store.dataModel().add(parsed);
        model.addAttribute("success", messages.get("import.imported", String.valueOf(parsed.size())));
        model.addAttribute("turtle", "");
        return "entities/import";
    }
}
