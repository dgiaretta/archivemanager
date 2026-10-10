package info.oais.archive.manager.web;

import info.oais.archive.manager.model.ImportResult;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.CatalogueImportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The public NAM Catalogue: searchable, anonymous, read-only (see
 * {@link #search}), plus the admin-gated Bulk Upload Spreadsheet import
 * that populates it and the Accession Register together
 * ({@link #uploadForm}/{@link #upload}, gated by {@code EditAuthInterceptor}).
 */
@Controller
@RequestMapping("/catalogue")
public class CatalogueController {

    private final ArchiveService archive;
    private final CatalogueImportService importService;

    private final info.oais.archive.manager.i18n.Messages messages;

    public CatalogueController(ArchiveService archive, CatalogueImportService importService, info.oais.archive.manager.i18n.Messages messages) {
        this.messages = messages;
        this.archive = archive;
        this.importService = importService;
    }

    @GetMapping
    public String search(@RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "1") int page,
                          Model model) {
        model.addAttribute("query", q);
        model.addAttribute("resultPage", archive.searchCatalogue(q, page, 50));
        return "catalogue/search";
    }

    /**
     * "Ask an interesting question without knowing SPARQL": a filter form
     * (any combination of creator/type/language/subject/coverage/rights/
     * date range/transferred, all optional and AND-combined) plus a few
     * pre-built aggregate reports (records by creator/type/language/year).
     * Both are genuinely computed from live data, not canned examples.
     */
    @GetMapping("/explore")
    public String explore(@RequestParam(required = false) String creator,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false) String language,
                           @RequestParam(required = false) String subject,
                           @RequestParam(required = false) String coverage,
                           @RequestParam(required = false) String rights,
                           @RequestParam(required = false) String dateFrom,
                           @RequestParam(required = false) String dateTo,
                           @RequestParam(required = false) String transferredRecord,
                           @RequestParam(defaultValue = "1") int page,
                           Model model) {
        model.addAttribute("creator", creator);
        model.addAttribute("type", type);
        model.addAttribute("language", language);
        model.addAttribute("subject", subject);
        model.addAttribute("coverage", coverage);
        model.addAttribute("rights", rights);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("transferredRecord", transferredRecord);

        boolean anyFilter = notBlank(creator) || notBlank(type) || notBlank(language) || notBlank(subject)
                || notBlank(coverage) || notBlank(rights) || notBlank(dateFrom) || notBlank(dateTo)
                || notBlank(transferredRecord);
        model.addAttribute("anyFilter", anyFilter);
        if (anyFilter) {
            model.addAttribute("resultPage", archive.advancedSearch(creator, type, language, subject,
                    coverage, rights, dateFrom, dateTo, transferredRecord, page, 50));
        }

        model.addAttribute("byCreator", archive.recordsByCreator());
        model.addAttribute("byType", archive.recordsByType());
        model.addAttribute("byLanguage", archive.recordsByLanguage());
        model.addAttribute("byYear", archive.recordsByYear());
        return "catalogue/explore";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @GetMapping("/upload")
    public String uploadForm() {
        return "catalogue/upload";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                          @RequestParam(required = false) String accessionDescription,
                          @RequestParam(required = false) String depositor,
                          @RequestParam(required = false) String recordingArchivist,
                          @RequestParam(required = false) String accessionDate,
                          Model model) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("error", messages.get("error.chooseFile"));
            return "catalogue/upload";
        }
        String filename = file.getOriginalFilename();
        boolean isXlsx = filename != null && filename.toLowerCase().endsWith(".xlsx");
        try {
            ImportResult result;
            if (isXlsx) {
                // No lossy CSV-export step in this path at all -- prefer this over CSV
                // whenever the source data has non-Latin script in it (Dhivehi, for NAM);
                // see CatalogueImportService's class-level note for exactly why plain CSV
                // export from Excel can silently corrupt it.
                try (var xlsxStream = file.getInputStream()) {
                    result = importService.importXlsx(xlsxStream, accessionDescription, depositor,
                            recordingArchivist, accessionDate);
                }
            } else {
                try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
                    result = importService.importCsv(reader, accessionDescription, depositor,
                            recordingArchivist, accessionDate);
                }
            }
            model.addAttribute("result", result);
            return "catalogue/upload-result";
        } catch (IOException e) {
            model.addAttribute("error", messages.get("error.couldNotRead", e.getMessage()));
            return "catalogue/upload";
        } catch (RuntimeException e) {
            model.addAttribute("error", messages.get("error.importFailed", e.getMessage()));
            return "catalogue/upload";
        }
    }
}
