package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * "Greater visibility of how properties and relationships are used" --
 * live class/property usage counts across the whole data graph, open to
 * everyone the same as the rest of browsing (this is meta-information
 * about the archive's data model, not archive data itself).
 */
@Controller
public class StatisticsController {

    private final ArchiveService archive;

    public StatisticsController(ArchiveService archive) {
        this.archive = archive;
    }

    @GetMapping("/statistics")
    public String statistics(Model model) {
        model.addAttribute("classCounts", archive.typesInUse());
        model.addAttribute("propertyCounts", archive.propertyUsageCounts());
        return "statistics/view";
    }
}
