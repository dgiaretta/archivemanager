package info.oais.archive.manager.web;

import info.oais.archive.manager.model.format.KnownFormatDescription;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.format.FormatDescriptionRdfService;
import info.oais.archive.manager.service.format.KnownFormat;
import info.oais.archive.manager.service.format.SpreadsheetReader;
import info.oais.infomodel.structure.description.Semantics;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * RepInfo Tools for data whose format is already known -- a spreadsheet, or
 * delimited text: describe what each column means, and save that as Semantic
 * Representation Information used together with the format's Structure
 * Representation Information (a format profile, or existing Structure
 * Representation Information) and Other Representation Information (the
 * software that reads it, or existing Other Representation Information).
 * See {@link KnownFormatDescription} and
 * {@link FormatDescriptionRdfService#saveKnownFormat}.
 *
 * <p>The description being built lives in the HTTP session, like the byte
 * layouts' (see {@link RepInfoToolController}); every route needs the edit
 * login, as every {@code /repinfo-tools} route does.</p>
 */
@Controller
@RequestMapping("/repinfo-tools/known")
public class KnownFormatController {

    static final String SESSION_KEY = "repinfo-tools-known-format";

    private final SpreadsheetReader reader;
    private final FormatDescriptionRdfService rdf;
    private final ArchiveService archive;
    private final RdfStore store;
    private final QueryRunner q;

    private final info.oais.archive.manager.i18n.Messages messages;

    public KnownFormatController(SpreadsheetReader reader, FormatDescriptionRdfService rdf, ArchiveService archive,
                                 RdfStore store, QueryRunner q, info.oais.archive.manager.i18n.Messages messages) {
        this.messages = messages;
        this.reader = reader;
        this.rdf = rdf;
        this.archive = archive;
        this.store = store;
        this.q = q;
    }

    @PostMapping("/start")
    public String start(@RequestParam String format, HttpSession session) {
        KnownFormat known = KnownFormat.of(format);
        KnownFormatDescription d = new KnownFormatDescription();
        d.setFormat(known.key());
        d.setName("New " + known.label());
        d.setRegistryId(known.registryId());
        d.setVersion(known.version());
        if (known.text()) {
            d.setCharacterEncoding("UTF-8");
            d.setLineEnding("CRLF");
            d.setDelimiter(",");
            d.setQuote("\"");
            d.addPart(new KnownFormatDescription.Part(KnownFormatDescription.newId(),
                    known.key().toUpperCase() + " file", 1, List.of()));
        }
        session.setAttribute(SESSION_KEY, d);
        return "redirect:/repinfo-tools/known";
    }

    @GetMapping
    public String edit(@RequestParam(required = false) String item, HttpSession session, Model model) {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        populate(d, item, model);
        return "repinfo-tools/known";
    }

    private void populate(KnownFormatDescription d, String item, Model model) {
        model.addAttribute("d", d);
        model.addAttribute("format", KnownFormat.of(d.getFormat()));
        d.item(item).ifPresent(i -> {
            model.addAttribute("item", i);
            model.addAttribute("itemPart", d.partOfItem(i.id()).orElseThrow());
            model.addAttribute("sem", SemanticsForm.of(i.semantics()));
        });
        model.addAttribute("allEntities", archive.listAllEntities());
        model.addAttribute("structures", repInfoOfType("StructureRepresentationInformation"));
        model.addAttribute("others", repInfoOfType("OtherRepresentationInformation"));
    }

    /** Existing Representation Information of a category, to offer instead of the format's own. */
    private List<Map<String, String>> repInfoOfType(String type) {
        return q.select(store.queryModel(), Ns.PREFIXES + """
                SELECT DISTINCT ?iri ?label WHERE {
                  ?iri a im:%s .
                  OPTIONAL { ?iri rdfs:label ?l }
                  BIND (COALESCE(?l, STR(?iri)) AS ?label)
                } ORDER BY ?label LIMIT 500""".formatted(type));
    }

    @PostMapping("/details")
    public String details(@RequestParam Map<String, String> form, HttpSession session) {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        d.setName(form.get("name"));
        d.setNotes(form.get("notes"));
        d.setRegistryId(form.get("registryId"));
        d.setVersion(form.get("version"));
        if (KnownFormat.of(d.getFormat()).text()) {
            d.setCharacterEncoding(form.get("characterEncoding"));
            d.setLineEnding(form.get("lineEnding"));
            d.setDelimiter(form.get("delimiter"));
            d.setQuote(form.get("quote"));
        }
        return "redirect:/repinfo-tools/known";
    }

    @PostMapping("/parts/add")
    public String addPart(@RequestParam String name, @RequestParam(defaultValue = "1") int headerRow,
                          HttpSession session, RedirectAttributes redirect) {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        if (name.isBlank()) {
            redirect.addFlashAttribute("error", messages.get("error.sheetName"));
        } else {
            d.addPart(new KnownFormatDescription.Part(KnownFormatDescription.newId(), name.strip(), Math.max(headerRow, 1),
                    List.of()));
        }
        return "redirect:/repinfo-tools/known";
    }

    @PostMapping("/parts/{id}/update")
    public String updatePart(@PathVariable String id, @RequestParam String name,
                             @RequestParam(defaultValue = "1") int headerRow, HttpSession session) {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        d.part(id).ifPresent(p -> d.replacePart(id, new KnownFormatDescription.Part(id,
                name.isBlank() ? p.name() : name.strip(), Math.max(headerRow, 1), p.items())));
        return "redirect:/repinfo-tools/known";
    }

    @PostMapping("/parts/{id}/delete")
    public String deletePart(@PathVariable String id, HttpSession session) {
        KnownFormatDescription d = draft(session);
        if (d != null) {
            d.removePart(id);
        }
        return "redirect:/repinfo-tools/known";
    }

    @PostMapping("/items/add")
    public String addItem(@RequestParam String partId, @RequestParam String header, HttpSession session,
                          RedirectAttributes redirect) {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        if (header.isBlank()) {
            redirect.addFlashAttribute("error", messages.get("error.columnHeader"));
            return "redirect:/repinfo-tools/known";
        }
        KnownFormatDescription.Item item = new KnownFormatDescription.Item(KnownFormatDescription.newId(), header.strip(),
                Semantics.NONE);
        d.addItem(partId, item);
        return "redirect:/repinfo-tools/known?item=" + item.id() + "#item";
    }

    @PostMapping("/items/{id}/update")
    public String updateItem(@PathVariable String id, @RequestParam Map<String, String> form, HttpSession session,
                             RedirectAttributes redirect) {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        try {
            KnownFormatDescription.Item existing = d.item(id)
                    .orElseThrow(() -> new IllegalArgumentException("That column no longer exists."));
            String header = form.getOrDefault("header", "").isBlank() ? existing.header() : form.get("header").strip();
            d.replaceItem(id, new KnownFormatDescription.Item(id, header, SemanticsForm.parse(form)));
            redirect.addFlashAttribute("message", messages.get("message.saved", header));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/repinfo-tools/known?item=" + id + "#item";
    }

    @PostMapping("/items/{id}/delete")
    public String deleteItem(@PathVariable String id, HttpSession session) {
        KnownFormatDescription d = draft(session);
        if (d != null) {
            d.removeItem(id);
        }
        return "redirect:/repinfo-tools/known";
    }

    /** Proposes the sheets and columns from a sample: headers from each sheet's first non-empty row. */
    @PostMapping("/from-sample")
    public String fromSample(@RequestParam("sample") MultipartFile sample, HttpSession session,
                             RedirectAttributes redirect) throws IOException {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        try {
            List<KnownFormatDescription.Part> parts = reader.propose(d, reader.read(d, sample.getBytes()));
            if (parts.isEmpty()) {
                redirect.addFlashAttribute("error", messages.get("error.noRows"));
            } else {
                d.setParts(parts);
                int columns = parts.stream().mapToInt(p -> p.items().size()).sum();
                redirect.addFlashAttribute("message", parts.size() == 1
                        ? messages.get("message.tookColumnsOneSheet", columns, sample.getOriginalFilename())
                        : messages.get("message.tookColumns", columns, parts.size(), sample.getOriginalFilename()));
            }
        } catch (IOException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/repinfo-tools/known";
    }

    /** Tests the description against a sample: each column found, its values and what they mean. */
    @PostMapping("/test")
    public String test(@RequestParam("sample") MultipartFile sample, HttpSession session, Model model) throws IOException {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        populate(d, null, model);
        model.addAttribute("sampleFileName", sample.getOriginalFilename());
        try {
            model.addAttribute("testResults", reader.test(d, reader.read(d, sample.getBytes())));
        } catch (IOException e) {
            model.addAttribute("testError", e.getMessage());
        }
        return "repinfo-tools/known";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String dataObjectId,
                       @RequestParam(defaultValue = "") String structure, @RequestParam(defaultValue = "") String other,
                       HttpSession session) {
        KnownFormatDescription d = draft(session);
        if (d == null) {
            return "redirect:/repinfo-tools";
        }
        String dataObjectIri = dataObjectId == null || dataObjectId.isBlank() ? null : archive.decodeId(dataObjectId);
        String saved = rdf.saveKnownFormat(d, dataObjectIri, structure.isBlank() ? null : structure,
                other.isBlank() ? null : other);
        session.removeAttribute(SESSION_KEY);
        return "redirect:/resource/" + archive.encodeId(saved);
    }

    @PostMapping("/discard")
    public String discard(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return "redirect:/repinfo-tools";
    }

    private static KnownFormatDescription draft(HttpSession session) {
        return session.getAttribute(SESSION_KEY) instanceof KnownFormatDescription d ? d : null;
    }
}
