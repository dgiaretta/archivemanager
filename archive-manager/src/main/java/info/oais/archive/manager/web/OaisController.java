package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/oais")
public class OaisController {

    private final ArchiveService archive;

    private final info.oais.archive.manager.i18n.Messages messages;

    public OaisController(ArchiveService archive, info.oais.archive.manager.i18n.Messages messages) {
        this.messages = messages;
        this.archive = archive;
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("id", id);
        model.addAttribute("iri", iri);
        String recordTitle = archive.label(iri);
        model.addAttribute("title", recordTitle);
        model.addAttribute("pageTitle", messages.get("title.oais", recordTitle));

        // Class-level correspondences declared in the bridge, one lookup per
        // rdf:type the resource carries (usually just one: Record, RecordPart
        // or RecordSet).
        Map<String, List<?>> mappings = new LinkedHashMap<>();
        for (String type : archive.types(iri)) {
            mappings.put(type, archive.bridgeMappingsForType(type));
        }
        model.addAttribute("mappingsByType", mappings);

        // The individual-level OAIS counterpart, if the sample/edited data
        // links one, walked out into its structural tree and flattened into
        // a plain indented list (rendered with a non-recursive th:each).
        archive.oaisCounterpart(iri).ifPresentOrElse(
                oaisIri -> model.addAttribute("oaisRows", archive.flatten(archive.oaisTree(oaisIri, 4))),
                () -> model.addAttribute("oaisRows", null));

        // The one documented gap: Representation Information has no RiC-O class.
        model.addAttribute("representationInfoGap", archive.classGapNote("RepresentationInformation").orElse(null));

        return "oais/view";
    }
}
