package info.oais.archive.manager.web;

import java.util.List;

import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * A generic, works-for-anything detail page: type(s), literal attributes,
 * and outgoing/incoming resource links, resolved purely by SPARQL. This is
 * the fallback destination for graph nodes that aren't a Record, Agent,
 * Activity or Mandate -- typically OAIS individuals (Information Object,
 * Data Object, Provenance Information...) and RiC-O support entities
 * (Date, Relation instances).
 */
@Controller
@RequestMapping("/resource")
public class ResourceController {

    private final ArchiveService archive;
    private final info.oais.archive.manager.service.format.DataObjectViewService views;

    public ResourceController(ArchiveService archive,
                              info.oais.archive.manager.service.format.DataObjectViewService views) {
        this.archive = archive;
        this.views = views;
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("id", id);
        model.addAttribute("iri", iri);
        model.addAttribute("title", archive.label(iri));
        model.addAttribute("types", archive.types(iri));
        model.addAttribute("attributes", archive.attributes(iri));
        model.addAttribute("outgoing", archive.outgoingLinks(iri));
        model.addAttribute("incoming", archive.incomingLinks(iri));
        // A Data Object whose bits have a storage location and that has Representation Information:
        // offer it to viewers (its manifest for TOPCAT/SPLAT, VOTable when it has a table view, FITS when
        // it has an image view). Both flags are always set, since the template combines them with "or",
        // which refuses a missing (null) value.
        model.addAttribute("tableViewable", false);
        model.addAttribute("imageViewable", false);
        views.describe(iri, java.net.URI::create).ifPresent(d -> {
            model.addAttribute("describedData", d);
            List<info.oais.archive.manager.service.format.DataObjectViewService.Viewer> viewers = views.viewers(iri);
            model.addAttribute("viewers", viewers);
            model.addAttribute("tableViewable", viewers.stream().anyMatch(v ->
                    v.format().equals(info.oais.archive.manager.service.format.DataObjectViewService.VOTABLE)));
            model.addAttribute("imageViewable", viewers.stream().anyMatch(v ->
                    v.format().equals(info.oais.archive.manager.service.format.DataObjectViewService.FITS)));
        });
        return "resource/view";
    }
}
