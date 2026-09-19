package info.oais.archive.manager.web;

import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ArchiveService archive;
    private final RdfStore store;

    public HomeController(ArchiveService archive, RdfStore store) {
        this.archive = archive;
        this.store = store;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("counts", archive.counts());
        model.addAttribute("storageLocation", store.storageLocation().toString());
        return "home";
    }
}
