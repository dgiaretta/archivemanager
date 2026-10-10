package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class GraphPageController {

    private final ArchiveService archive;

    private final info.oais.archive.manager.i18n.Messages messages;

    public GraphPageController(ArchiveService archive, info.oais.archive.manager.i18n.Messages messages) {
        this.messages = messages;
        this.archive = archive;
    }

    @GetMapping("/graph")
    public String full(Model model) {
        model.addAttribute("pageTitle", messages.get("title.fullGraph"));
        model.addAttribute("apiUrl", "/api/graph");
        model.addAttribute("focusId", null);
        model.addAttribute("focusIds", null);
        model.addAttribute("depth", null);
        return "graph/view";
    }

    @GetMapping("/graph/{id}")
    public String focused(@PathVariable String id,
                           @RequestParam(defaultValue = "1") int depth,
                           Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("pageTitle", messages.get("title.graphOf", archive.label(iri)));
        model.addAttribute("apiUrl", "/api/graph/" + id + "?depth=" + depth);
        model.addAttribute("focusId", id);
        model.addAttribute("focusIds", null);
        model.addAttribute("depth", depth);
        return "graph/view";
    }

    /**
     * Focused view around several resources at once -- one {@code id} per item on
     * whichever listing page's "view as graph" button sent us here, so the graph
     * shown is exactly that page of results, not the whole archive.
     */
    @GetMapping("/graph/multi")
    public String multi(@RequestParam("id") List<String> ids,
                         @RequestParam(defaultValue = "1") int depth,
                         Model model) {
        String apiIds = ids.stream().map(id -> "id=" + id).collect(Collectors.joining("&"));
        model.addAttribute("pageTitle", ids.size() == 1 ? messages.get("title.graphOne") : messages.get("title.graphMany", ids.size()));
        model.addAttribute("apiUrl", "/api/graph/multi?" + apiIds + "&depth=" + depth);
        model.addAttribute("focusId", null);
        model.addAttribute("focusIds", ids);
        model.addAttribute("depth", depth);
        return "graph/view";
    }
}
