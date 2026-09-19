package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/records")
public class RecordController {

    private final ArchiveService archive;

    public RecordController(ArchiveService archive) {
        this.archive = archive;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("resultPage", archive.listRecordResources(page, 50));
        return "records/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("id", id);
        model.addAttribute("iri", iri);
        model.addAttribute("title", archive.label(iri));
        model.addAttribute("types", archive.types(iri));
        model.addAttribute("description", archive.description(iri));
        model.addAttribute("creators", archive.creators(iri));
        model.addAttribute("constituents", archive.constituents(iri));
        model.addAttribute("parent", archive.parent(iri).orElse(null));
        model.addAttribute("instantiations", archive.instantiations(iri));
        model.addAttribute("mandates", archive.mandates(iri));
        model.addAttribute("provenanceActivities", archive.provenanceActivities(iri));
        model.addAttribute("attributes", archive.attributes(iri));
        model.addAttribute("hasOaisCounterpart", archive.oaisCounterpart(iri).isPresent());
        return "records/view";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("agents", archive.listAgents());
        model.addAttribute("recordSets", archive.listRecordResources());
        return "records/form";
    }

    @PostMapping
    public String create(@RequestParam String type,
                          @RequestParam String title,
                          @RequestParam(required = false) String description,
                          @RequestParam(required = false) String creatorId,
                          @RequestParam(required = false) String parentId,
                          Model model) {
        String creatorIri = (creatorId == null || creatorId.isBlank()) ? null : archive.decodeId(creatorId);
        String parentIri = (parentId == null || parentId.isBlank()) ? null : archive.decodeId(parentId);
        String newIri = archive.createRecordResource(type, title, description, creatorIri, parentIri);
        return "redirect:/records/" + archive.encodeId(newIri);
    }
}
