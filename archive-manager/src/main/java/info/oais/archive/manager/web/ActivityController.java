package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/activities")
public class ActivityController {

    private final ArchiveService archive;

    public ActivityController(ArchiveService archive) {
        this.archive = archive;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("resultPage", archive.listActivities(page, 50));
        return "activities/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("id", id);
        model.addAttribute("iri", iri);
        model.addAttribute("title", archive.label(iri));
        model.addAttribute("types", archive.types(iri));
        model.addAttribute("description", archive.description(iri));
        model.addAttribute("attributes", archive.attributes(iri));
        model.addAttribute("participants", archive.participants(iri));
        model.addAttribute("records", archive.recordsWithProvenanceActivity(iri));
        return "activities/view";
    }
}
