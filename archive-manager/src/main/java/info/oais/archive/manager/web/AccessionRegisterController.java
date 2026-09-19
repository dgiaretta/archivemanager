package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.EditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The public NAM Accession Register: anonymous, read-only, paginated list
 * of {@code nam:Accession} individuals -- each one a batch of records
 * transferred to NAM at one time from the same source, per the standard
 * archival definition NAM's Records Management Plan quotes from TNA. Each
 * entry links to {@code /activities/{id}} for the detail view (an Accession
 * is modelled as a subclass of {@code rico:Activity}, so that existing page
 * -- attributes, participants, and every record it's the provenance of --
 * already shows everything an Accession Register entry needs, with no
 * separate detail view required here).
 */
@Controller
@RequestMapping("/accession-register")
public class AccessionRegisterController {

    private final ArchiveService archive;
    private final EditService edit;

    public AccessionRegisterController(ArchiveService archive, EditService edit) {
        this.archive = archive;
        this.edit = edit;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("resultPage", archive.listAccessions(page, 50));
        return "accession-register/list";
    }

    /**
     * Deletes the accession AND every record/OAIS-counterpart resource it
     * created (see {@code EditService.deleteAccessionCascade} for exactly
     * why plain single-resource delete isn't enough here: it would leave
     * the records fully intact, orphaned but still findable, which is
     * exactly the kind of stale-data confusion that makes "delete and
     * re-import" testing unreliable). Admin-gated, same as every other
     * delete in this app.
     */
    @PostMapping("/{id}/delete")
    public String deleteCascade(@PathVariable String id, Model model) {
        String iri = archive.decodeId(id);
        edit.deleteAccessionCascade(iri);
        return "redirect:/accession-register";
    }

    /**
     * The bulk version, and the thorough one: every resource any import has
     * ever created, orphaned or not (see
     * {@code EditService.deleteAllCatalogueImportData} for exactly why the
     * simpler "iterate live accessions" approach isn't enough here -- it
     * can't reach records whose accession was already removed by the older,
     * non-cascading delete before this cascading delete existed).
     */
    @PostMapping("/delete-all")
    public String deleteAllCascade() {
        edit.deleteAllCatalogueImportData();
        return "redirect:/accession-register";
    }
}
