package info.oais.archive.manager.web;

import info.oais.archive.manager.model.Page;
import info.oais.archive.manager.model.ResourceSummary;
import info.oais.archive.manager.service.ArchiveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ArchiveApiController {

    private final ArchiveService archiveService;

    public ArchiveApiController(ArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return archiveService.counts();
    }

    @GetMapping("/records")
    public Page<ResourceSummary> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return archiveService.listRecordResources(page, size);
    }

    @GetMapping("/entities")
    public Page<ResourceSummary> entities(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return archiveService.listAllEntitiesPaged(type, page, size);
    }

    @GetMapping("/accessions")
    public Page<ResourceSummary> accessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return archiveService.listAccessions(page, size);
    }

    @GetMapping("/agents")
    public Page<ResourceSummary> agents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return archiveService.listAgents(page, size);
    }
}
