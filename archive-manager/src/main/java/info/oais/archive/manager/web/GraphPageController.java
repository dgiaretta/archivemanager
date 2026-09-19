package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GraphPageController {

    private final ArchiveService archive;

    public GraphPageController(ArchiveService archive) {
        this.archive = archive;
    }

    @GetMapping("/graph")
    public String full(Model model) {
        model.addAttribute("pageTitle", "Full relationship graph");
        model.addAttribute("apiUrl", "/api/graph");
        model.addAttribute("focusId", null);
        model.addAttribute("depth", null);
        return "graph/view";
    }

    @GetMapping("/graph/{id}")
    public String focused(@PathVariable String id,
                           @RequestParam(defaultValue = "1") int depth,
                           Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("pageTitle", "Graph: " + archive.label(iri));
        model.addAttribute("apiUrl", "/api/graph/" + id + "?depth=" + depth);
        model.addAttribute("focusId", id);
        model.addAttribute("depth", depth);
        return "graph/view";
    }
}
