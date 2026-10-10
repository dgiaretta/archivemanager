package info.oais.archive.manager.web;

import info.oais.archive.manager.i18n.Messages;
import info.oais.archive.manager.service.AipBuilder;
import info.oais.archive.manager.service.ArchiveService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

/**
 * Makes an Archival Information Package around a Data Object, from its page
 * (see {@link AipBuilder}), then shows the AIP, whose checklist says what's
 * still missing. A Data Object already in an AIP isn't packaged again: its
 * AIP is shown instead.
 */
@Controller
public class AipController {

    private final ArchiveService archive;
    private final AipBuilder builder;
    private final Messages messages;

    public AipController(ArchiveService archive, AipBuilder builder, Messages messages) {
        this.archive = archive;
        this.builder = builder;
        this.messages = messages;
    }

    @PostMapping("/data-objects/{id}/aip")
    public String create(@PathVariable String id,
                         @RequestParam(required = false) String designatedCommunity,
                         @RequestParam(required = false) String newCommunity,
                         @RequestParam(required = false) String communityDescription,
                         @RequestParam(required = false) String preservationObjective,
                         @RequestParam(required = false) String accessRights,
                         @RequestParam(required = false) String context,
                         @RequestParam(required = false) String recordedBy,
                         RedirectAttributes redirect) {
        String dataObject = archive.decodeId(id);
        if (!builder.isDataObject(dataObject)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a Data Object");
        }
        List<String> existing = builder.aipsOf(dataObject);
        if (!existing.isEmpty()) {
            redirect.addFlashAttribute("aipMessage", messages.get("aip.alreadyIn"));
            return "redirect:/resource/" + archive.encodeId(existing.get(0));
        }
        AipBuilder.Digest digest = null;
        String why = null;
        try {
            digest = builder.digest(dataObject).orElse(null);
            if (digest == null) {
                why = messages.get("aip.noStorage");
            }
        } catch (IOException | RuntimeException e) {
            why = messages.get("aip.fetchFailed", e.getMessage());
        }
        String aip = builder.create(dataObject, new AipBuilder.Details(designatedCommunity, newCommunity,
                communityDescription, preservationObjective, accessRights, context, recordedBy), digest, why);
        redirect.addFlashAttribute("aipMessage", why == null ? messages.get("aip.created")
                : messages.get("aip.createdWithoutFixity", why));
        return "redirect:/resource/" + archive.encodeId(aip);
    }
}
