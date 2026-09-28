package info.oais.archive.manager.web;

import jakarta.servlet.http.HttpSession;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.Hdf5Node;
import info.oais.archive.manager.model.format.Hdf5NodeKind;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.DfdlSampleRunner;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.DrbPythonSampleRunner;
import info.oais.archive.manager.service.format.DrbSampleRunner;
import info.oais.archive.manager.service.format.FormatDescriptionRdfService;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.HandWrittenDescriptions;
import info.oais.archive.manager.service.format.WriteBackResult;
import info.oais.archive.manager.service.format.KaitaiGenerator;
import info.oais.archive.manager.service.format.KaitaiSampleRunner;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.archive.manager.service.format.FormatIdentifiers;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.Descriptions;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.DescriptionLanguage;
import info.oais.infomodel.structure.description.DescriptionValidator;
import info.oais.infomodel.structure.description.Feature;
import info.oais.infomodel.structure.description.NumberFormat;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.Semantics;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Guided editor for building a Kaitai/DFDL/DRB format description AND, for
 * each field or tree row, the semantic name/definition/units that become its
 * Semantic Representation Information (see {@code FormatDescriptionRdfService}'s
 * doc comment for how the result is modeled as OAIS Representation
 * Information). The definition being built is held in the HTTP session, not
 * the archive, until "Save to archive" is submitted -- see
 * {@link FormatDefinition}'s own doc comment for why.
 */
@Controller
@RequestMapping("/repinfo-tools")
public class RepInfoToolController {

    private static final String SESSION_KEY = "repInfoToolDraft";

    private final ArchiveService archive;
    private final KaitaiGenerator kaitaiGenerator;
    private final DfdlGenerator dfdlGenerator;
    private final DrbGenerator drbGenerator;
    private final FormatDescriptionRdfService rdfService;
    private final DfdlSampleRunner dfdlSampleRunner;
    private final DrbPythonSampleRunner drbPythonSampleRunner;
    private final DrbSampleRunner drbSampleRunner;
    private final KaitaiSampleRunner kaitaiSampleRunner;

    public RepInfoToolController(ArchiveService archive, KaitaiGenerator kaitaiGenerator, DfdlGenerator dfdlGenerator,
                                  DrbGenerator drbGenerator, FormatDescriptionRdfService rdfService,
                                  DfdlSampleRunner dfdlSampleRunner, DrbPythonSampleRunner drbPythonSampleRunner,
                                  DrbSampleRunner drbSampleRunner, KaitaiSampleRunner kaitaiSampleRunner) {
        this.archive = archive;
        this.kaitaiGenerator = kaitaiGenerator;
        this.dfdlGenerator = dfdlGenerator;
        this.drbGenerator = drbGenerator;
        this.rdfService = rdfService;
        this.dfdlSampleRunner = dfdlSampleRunner;
        this.drbPythonSampleRunner = drbPythonSampleRunner;
        this.drbSampleRunner = drbSampleRunner;
        this.kaitaiSampleRunner = kaitaiSampleRunner;
    }

    @GetMapping
    public String start(HttpSession session, Model model) {
        model.addAttribute("hasDraft", draft(session) != null);
        return "repinfo-tools/start";
    }

    @PostMapping("/start")
    public String startNew(@RequestParam String template, HttpSession session) {
        FormatDefinition def = switch (template) {
            case "fits" -> FormatTemplates.fits();
            case "hdf5" -> FormatTemplates.hdf5();
            case "telemetry" -> FormatTemplates.telemetry();
            case "csv" -> FormatTemplates.csv();
            case "hand-kaitai", "hand-dfdl", "hand-drb" -> {
                FormatDefinition d = new FormatDefinition();
                d.setName("New format");
                d.setKind(FormatDefinitionKind.BYTE_LAYOUT);
                d.setTargets(java.util.EnumSet.of(handLanguage(template.substring("hand-".length()))));
                session.setAttribute(SESSION_KEY, d);
                yield null;
            }
            case "blank-logical" -> {
                FormatDefinition d = new FormatDefinition();
                d.setName("New logical schema");
                d.setKind(FormatDefinitionKind.LOGICAL_TREE);
                yield d;
            }
            default -> {
                FormatDefinition d = new FormatDefinition();
                d.setName("New format");
                d.setKind(FormatDefinitionKind.BYTE_LAYOUT);
                yield d;
            }
        };
        if (def == null) {
            return "redirect:/repinfo-tools/hand/" + template.substring("hand-".length());
        }
        session.setAttribute(SESSION_KEY, def);
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/discard")
    public String discard(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return "redirect:/repinfo-tools";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) String element, HttpSession session, Model model) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        model.addAttribute("def", def);
        model.addAttribute("fieldTypes", java.util.Arrays.stream(PrimitiveType.values())
                .filter(t -> t != PrimitiveType.BITS || def.allows(Feature.BIT_FIELDS)).toList());
        model.addAttribute("byteOrders", ByteOrder.values());
        model.addAttribute("nodeKinds", Hdf5NodeKind.values());
        model.addAttribute("languages", DescriptionLanguage.values());
        Map<String, Boolean> allow = new LinkedHashMap<>();
        Map<String, String> featureLanguages = new LinkedHashMap<>();
        for (Feature feature : Feature.values()) {
            allow.put(feature.name(), def.allows(feature));
            featureLanguages.put(feature.name(), feature.languagesText());
        }
        model.addAttribute("allow", allow);
        model.addAttribute("featureLanguages", featureLanguages);
        model.addAttribute("features", Feature.values());
        model.addAttribute("handWritten", def.getHandWritten().keySet().stream()
                .map(l -> Map.of("label", l.label(), "path", handPath(l))).toList());
        if (def.getKind() == FormatDefinitionKind.BYTE_LAYOUT) {
            FormatDescription format = def.toFormatDescription();
            List<DescriptionEditorView.Row> rows = DescriptionEditorView.rows(format, def.getTargets());
            long problemCount = DescriptionValidator.validate(format, def.getTargets()).size();
            if (def.getHandWritten().keySet().stream().anyMatch(l -> !HandWrittenDescriptions.isAddIn(l))
                    && def.getRoot().children().isEmpty()) {
                // Written entirely by hand: the tree is optional, only there for meanings.
                rows = rows.stream().map(r -> r.root() ? new DescriptionEditorView.Row(r.id(), r.depth(), r.kind(),
                        r.name(), r.summary(), r.semantics(), List.of(), r.root(), r.container(), r.first(), r.last())
                        : r).toList();
                problemCount = rows.stream().mapToLong(r -> r.problems().size()).sum();
            }
            model.addAttribute("rows", rows);
            model.addAttribute("problemCount", problemCount);
            if (element != null) {
                DescriptionEditorView.form(format, element).ifPresent(f -> model.addAttribute("form", f));
            }
        }
        return "repinfo-tools/edit";
    }

    /**
     * Adds an element inside the record (or branch) {@code parentId}, or a
     * branch to the choice {@code parentId}, then opens it for editing.
     */
    @PostMapping("/elements/add")
    public String addElement(@RequestParam String parentId, @RequestParam String kind, @RequestParam String name,
                             @RequestParam(required = false) String discriminator,
                             @RequestParam(required = false) String key,
                             HttpSession session, RedirectAttributes redirect) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        String snake = FormatIdentifiers.snakeCase(name);
        String id = ElementDescription.newId();
        try {
            switch (kind) {
                case "field" -> def.editRoot(root -> Descriptions.addChild(root, parentId, new FieldDescription(id, snake,
                        PrimitiveType.UINT8, null, null, Occurrence.ONCE, Semantics.NONE)));
                case "record" -> def.editRoot(root -> Descriptions.addChild(root, parentId,
                        new RecordDescription(id, snake, List.of(), null, Occurrence.ONCE, Semantics.NONE)));
                case "choice" -> {
                    Expression on = Expression.parse(required(discriminator, "What the choice is made on"));
                    def.editRoot(root -> Descriptions.addChild(root, parentId,
                            new ChoiceDescription(id, snake, on, List.of(), Occurrence.ONCE, Semantics.NONE)));
                }
                case "branch" -> {
                    String branchKey = required(key, "The value selecting this branch");
                    def.editRoot(root -> Descriptions.addBranch(root, parentId, new ChoiceDescription.Branch(branchKey,
                            new RecordDescription(id, snake, List.of(), null, Occurrence.ONCE, Semantics.NONE))));
                }
                default -> throw new IllegalArgumentException("Unknown kind of element: " + kind);
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("editorError", e.getMessage());
            return "redirect:/repinfo-tools/edit?element=" + parentId;
        }
        return "redirect:/repinfo-tools/edit?element=" + id;
    }

    /** Saves the edit form for one element (see {@code DescriptionEditorView.Form}). */
    @PostMapping("/elements/{id}/update")
    public String updateElement(@PathVariable String id, @RequestParam Map<String, String> form,
                                HttpSession session, RedirectAttributes redirect) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        try {
            ElementDescription existing = Descriptions.find(def.getRoot(), id)
                    .orElseThrow(() -> new IllegalArgumentException("That element no longer exists."));
            boolean root = existing.id().equals(def.getRoot().id());
            String name = root ? existing.name() : FormatIdentifiers.snakeCase(required(form.get("name"), "The name"));
            Occurrence occurrence = root ? Occurrence.ONCE : occurrence(form.get("occurs"), form.get("occursExpr"));
            Semantics semantics = semantics(form);
            ElementDescription updated;
            if (existing instanceof FieldDescription f) {
                PrimitiveType type = PrimitiveType.valueOf(form.getOrDefault("type", f.type().name()));
                String lengthText = form.getOrDefault("length", "").strip();
                Expression length = type.needsLength() && !lengthText.isEmpty() ? Expression.parse(lengthText) : null;
                String order = form.getOrDefault("byteOrder", "");
                // Options for features the chosen languages don't offer aren't on the form: keep what's there.
                String nilValue = form.containsKey("nilShown")
                        ? (form.containsKey("nil") ? form.getOrDefault("nilValue", "") : null) : f.nilValue();
                NumberFormat numberFormat = form.containsKey("numberPattern")
                        ? (form.get("numberPattern").isBlank() ? null : new NumberFormat(form.get("numberPattern").strip(),
                                form.getOrDefault("decimalSeparator", "."), form.getOrDefault("groupingSeparator", "")))
                        : f.numberFormat();
                updated = new FieldDescription(id, name, type, length, order.isBlank() ? null : ByteOrder.valueOf(order),
                        occurrence, semantics, optionalExpression(form, "position", f.offset()), nilValue, numberFormat);
            } else if (existing instanceof ChoiceDescription c) {
                updated = new ChoiceDescription(id, name, Expression.parse(required(form.get("discriminator"),
                        "What the choice is made on")), c.branches(), occurrence, semantics);
            } else {
                RecordDescription r = (RecordDescription) existing;
                String quote = form.containsKey("quote") ? form.get("quote").strip()
                        : r.isText() ? r.text().quote() : null;
                RecordDescription.TextLayout text = form.containsKey("text")
                        ? new RecordDescription.TextLayout(
                                DescriptionEditorView.unescapeControl(required(form.get("fieldSeparator"), "The field separator")),
                                DescriptionEditorView.unescapeControl(required(form.get("recordTerminator"), "The record terminator")),
                                quote)
                        : null;
                RecordDescription.Compression compression = form.containsKey("zlibShown")
                        ? (form.containsKey("zlib") ? RecordDescription.Compression.ZLIB : null) : r.compression();
                boolean branch = DescriptionEditorView.branchKeyOf(def.getRoot(), id) != null;
                updated = new RecordDescription(id, name, r.children(), text, branch ? Occurrence.ONCE : occurrence, semantics,
                        optionalExpression(form, "recordSize", r.size()), compression,
                        optionalExpression(form, "position", r.offset()));
                if (branch) {
                    String newKey = required(form.get("branchKey"), "The value selecting this branch");
                    def.editRoot(rootRecord -> rekeyBranch(rootRecord, id, newKey));
                }
            }
            ElementDescription replacement = updated;
            def.editRoot(rootRecord -> Descriptions.update(rootRecord, id, e -> replacement));
            redirect.addFlashAttribute("editorMessage", "Saved '" + replacement.name() + "'.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("editorError", e.getMessage());
        }
        return "redirect:/repinfo-tools/edit?element=" + id;
    }

    @PostMapping("/elements/{id}/delete")
    public String deleteElement(@PathVariable String id, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null && !id.equals(def.getRoot().id())) {
            def.editRoot(root -> Descriptions.remove(root, id));
        }
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/elements/{id}/move")
    public String moveElement(@PathVariable String id, @RequestParam int delta, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.editRoot(root -> Descriptions.move(root, id, delta));
        }
        return "redirect:/repinfo-tools/edit";
    }

    private static RecordDescription rekeyBranch(RecordDescription root, String branchRecordId, String key) {
        for (ElementDescription e : Descriptions.all(root)) {
            if (e instanceof ChoiceDescription c && c.branches().stream().anyMatch(b -> b.record().id().equals(branchRecordId))) {
                List<ChoiceDescription.Branch> branches = c.branches().stream()
                        .map(b -> b.record().id().equals(branchRecordId) ? new ChoiceDescription.Branch(key, b.record()) : b)
                        .toList();
                return Descriptions.update(root, c.id(), x -> c.withBranches(branches));
            }
        }
        return root;
    }

    /** An optional expression from the form; the existing one when the form doesn't offer it. */
    private static Expression optionalExpression(Map<String, String> form, String param, Expression existing) {
        if (!form.containsKey(param)) {
            return existing;
        }
        String text = form.get(param).strip();
        return text.isEmpty() ? null : Expression.parse(text);
    }

    private static Occurrence occurrence(String kind, String expression) {
        return switch (kind == null ? "once" : kind) {
            case "optional" -> new Occurrence.Optional(Expression.parse(required(expression, "The condition")));
            case "repeated" -> new Occurrence.Repeated(Expression.parse(required(expression, "The repeat count")));
            case "until_end" -> new Occurrence.UntilEnd();
            default -> Occurrence.ONCE;
        };
    }

    /** The semantics part of the edit form; see {@code Semantics} for what each part means. */
    private static Semantics semantics(Map<String, String> form) {
        Map<String, String> codes = new LinkedHashMap<>();
        for (String line : form.getOrDefault("codes", "").split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int sep = line.indexOf('=') >= 0 ? line.indexOf('=') : line.indexOf(':');
            if (sep <= 0) {
                throw new IllegalArgumentException("Write each coded value as 'value = meaning', e.g. '1 = housekeeping' (not '"
                        + line.strip() + "').");
            }
            codes.put(line.substring(0, sep).strip(), line.substring(sep + 1).strip());
        }
        return new Semantics(form.get("semanticName"), form.get("definition"), form.get("units"),
                uri(form.get("unitsUri"), "The units link"), uri(form.get("conceptUri"), "The concept link"), codes,
                decimal(form.get("scale"), "The scale"), decimal(form.get("offset"), "The offset"), form.get("fillValue"),
                decimal(form.get("validMin"), "The smallest valid value"), decimal(form.get("validMax"), "The largest valid value"));
    }

    private static java.net.URI uri(String text, String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(text.strip());
            if (uri.getScheme() == null) {
                throw new IllegalArgumentException(what + " must be a full web address, starting http:// or https://.");
            }
            return uri;
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(what + " isn't a valid web address.");
        }
    }

    private static java.math.BigDecimal decimal(String text, String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number (not '" + text.strip() + "').");
        }
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " is needed.");
        }
        return value;
    }

    @PostMapping("/details")
    public String updateDetails(@RequestParam String name, @RequestParam(required = false) String notes,
                                 @RequestParam(required = false) String fileExtensions,
                                 @RequestParam ByteOrder defaultByteOrder,
                                 @RequestParam(required = false) List<DescriptionLanguage> targets,
                                 HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.setName(name);
            def.setNotes(notes);
            def.setFileExtensions(fileExtensions);
            def.setDefaultByteOrder(defaultByteOrder);
            if (targets != null) {
                def.setTargets(java.util.EnumSet.copyOf(targets));
            }
        }
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/nodes")
    public String addNode(@RequestParam Hdf5NodeKind kind, @RequestParam String path,
                           @RequestParam(required = false) String dtype,
                           @RequestParam(required = false) String shape,
                           @RequestParam(required = false) String semanticName,
                           @RequestParam(required = false) String definition,
                           @RequestParam(required = false) String units,
                           HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null && !path.isBlank()) {
            def.addNode(new Hdf5Node(kind, path, dtype, parseShape(shape), semanticName, definition, units));
        }
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/nodes/{index}/delete")
    public String deleteNode(@PathVariable int index, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.removeNode(index);
        }
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/nodes/{index}/move")
    public String moveNode(@PathVariable int index, @RequestParam int delta, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.moveNode(index, delta);
        }
        return "redirect:/repinfo-tools/edit";
    }

    @GetMapping("/preview")
    public String preview(HttpSession session, Model model) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        populatePreview(def, model);
        return "repinfo-tools/preview";
    }

    /**
     * Runs the draft's generated DFDL schema against an uploaded sample file
     * (see {@link DfdlSampleRunner}) and re-renders the preview with the
     * decoded tree, or Daffodil's diagnostics, under the DFDL section. The
     * sample is only held for this request -- nothing is stored.
     */
    @PostMapping("/test-dfdl")
    public String testDfdl(@RequestParam("sample") MultipartFile sample, HttpSession session, Model model)
            throws IOException {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        populatePreview(def, model);
        String dfdl = (String) model.getAttribute("dfdl");
        if (dfdl == null) {
            return "redirect:/repinfo-tools/preview";
        }
        SampleDecodeResult result = sample.isEmpty()
                ? SampleDecodeResult.failure(EMPTY_SAMPLE)
                : dfdlSampleRunner.run(alignWith(def, DescriptionLanguage.DFDL), dfdl, sample.getBytes());
        addSampleResult(model, "dfdlTest", result, sample);
        return "repinfo-tools/preview";
    }

    /**
     * Same as {@link #testDfdl}, but runs the draft's generated drb-python
     * driver in a Python process (see {@link DrbPythonSampleRunner}), with its
     * hand-written add-in if there is one and the server allows it, then shows
     * what its metadata and checks add-ons return; needs drb-python installed
     * on the machine running this app.
     */
    @PostMapping("/test-drb-python")
    public String testDrbPython(@RequestParam("sample") MultipartFile sample, HttpSession session, Model model)
            throws IOException {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        if (def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return "redirect:/repinfo-tools/preview";
        }
        populatePreview(def, model);
        String module = (String) model.getAttribute("drbPython");
        if (module == null) {
            return "redirect:/repinfo-tools/preview";
        }
        if (sample.isEmpty()) {
            addSampleResult(model, "drbPythonTest", SampleDecodeResult.failure(EMPTY_SAMPLE), sample);
            return "repinfo-tools/preview";
        }
        DrbPythonSampleRunner.Outcome outcome = drbPythonSampleRunner.run(module,
                def.handWritten(DescriptionLanguage.DRB_PYTHON).orElse(null), drbGenerator.pythonFactoryClassName(def),
                drbGenerator.pythonDriverId(def), sample.getBytes(), sample.getOriginalFilename());
        addSampleResult(model, "drbPythonTest", DrbPythonSampleRunner.align(def.toFormatDescription(), outcome.decoded()),
                sample);
        model.addAttribute("drbPythonAddOnResults", outcome.addOns());
        return "repinfo-tools/preview";
    }

    /**
     * Same as {@link #testDfdl}, but applies the draft's generated DRB SDF
     * schema with GAEL's Java DRB, in-process (see {@link DrbSampleRunner}).
     */
    @PostMapping("/test-drb-java")
    public String testDrbJava(@RequestParam("sample") MultipartFile sample, HttpSession session, Model model)
            throws IOException {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        if (def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return "redirect:/repinfo-tools/preview";
        }
        populatePreview(def, model);
        String sdf = (String) model.getAttribute("drbJava");
        if (sdf == null) {
            return "redirect:/repinfo-tools/preview";
        }
        SampleDecodeResult result = sample.isEmpty()
                ? SampleDecodeResult.failure(EMPTY_SAMPLE)
                : drbSampleRunner.run(alignWith(def, DescriptionLanguage.DRB), sdf, sample.getBytes());
        addSampleResult(model, "drbJavaTest", result, sample);
        return "repinfo-tools/preview";
    }

    /**
     * Same as {@link #testDfdl}, but compiles the draft's generated
     * {@code .ksy} with the bundled Kaitai Struct compiler and runs it (see
     * {@link KaitaiSampleRunner}); takes a few seconds.
     */
    @PostMapping("/test-kaitai")
    public String testKaitai(@RequestParam("sample") MultipartFile sample, HttpSession session, Model model)
            throws IOException {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        populatePreview(def, model);
        String ksy = (String) model.getAttribute("kaitai");
        if (ksy == null) {
            return "redirect:/repinfo-tools/preview";
        }
        FormatDescription format = alignWith(def, DescriptionLanguage.KAITAI);
        String rootName = format != null ? format.root().name() : HandWrittenDescriptions.kaitaiId(ksy);
        SampleDecodeResult result = sample.isEmpty()
                ? SampleDecodeResult.failure(EMPTY_SAMPLE)
                : kaitaiSampleRunner.run(format, ksy, rootName, sample.getBytes());
        addSampleResult(model, "kaitaiTest", result, sample);
        return "repinfo-tools/preview";
    }

    private static final String EMPTY_SAMPLE = "Choose a non-empty sample file to test against.";

    private static final String WRITTEN_KEY = "repinfo-tools-written";
    /** Written files larger than this aren't kept in the session for downloading. */
    private static final int MAX_KEPT_BYTES = 32 * 1024 * 1024;

    /** A written file kept in the session until it's downloaded or replaced. */
    private record WrittenFile(String fileName, byte[] bytes) implements java.io.Serializable {
    }

    /**
     * Writes a sample file back with one engine's description (the same text
     * the preview shows, generated or written by hand): decodes it, applies
     * the {@code changes} ({@code path = value} lines, none for a round
     * trip), encodes it again, and compares the result with the sample. The
     * written file can then be downloaded ({@link #downloadWritten}).
     *
     * @param engine {@code kaitai}, {@code dfdl}, {@code drb-java} or {@code drb-python}
     */
    @PostMapping("/write-back/{engine}")
    public String writeBack(@PathVariable String engine, @RequestParam("sample") MultipartFile sample,
                            @RequestParam(defaultValue = "") String changes, HttpSession session, Model model)
            throws IOException {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        if (def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return "redirect:/repinfo-tools/preview";
        }
        populatePreview(def, model);
        session.removeAttribute(WRITTEN_KEY);
        model.addAttribute("writeBackEngine", engine);
        model.addAttribute("writeBackChanges", changes);
        model.addAttribute("sampleFileName", sample.getOriginalFilename());
        model.addAttribute("sampleFileSize", sample.getSize());
        Map<info.oais.infomodel.structure.ElementPath, String> parsed;
        try {
            parsed = parseChanges(changes);
        } catch (IllegalArgumentException e) {
            model.addAttribute("writeBack", WriteBackResult.failure(e.getMessage()));
            return "repinfo-tools/preview";
        }
        model.addAttribute("writeBackChanged", !parsed.isEmpty());
        byte[] bytes = sample.getBytes();
        WriteBackResult result = sample.isEmpty() ? WriteBackResult.failure(EMPTY_SAMPLE)
                : writeBack(engine, def, model, bytes, parsed);
        if (result == null) {
            return "redirect:/repinfo-tools/preview";
        }
        model.addAttribute("writeBack", result);
        if (result.ok() && result.written().length <= MAX_KEPT_BYTES) {
            session.setAttribute(WRITTEN_KEY, new WrittenFile(writtenName(sample.getOriginalFilename(), engine,
                    !parsed.isEmpty()), result.written()));
        }
        return "repinfo-tools/preview";
    }

    /** The result of writing back with {@code engine}'s description, or null if there's no such description. */
    private WriteBackResult writeBack(String engine, FormatDefinition def, Model model, byte[] sample,
                                      Map<info.oais.infomodel.structure.ElementPath, String> changes) {
        switch (engine) {
            case "kaitai" -> {
                String ksy = (String) model.getAttribute("kaitai");
                if (ksy == null) {
                    return null;
                }
                String rootName = def.handWritten(DescriptionLanguage.KAITAI).isPresent()
                        ? HandWrittenDescriptions.kaitaiId(ksy) : def.toFormatDescription().root().name();
                return kaitaiSampleRunner.write(ksy, rootName, sample, changes);
            }
            case "dfdl" -> {
                String dfdl = (String) model.getAttribute("dfdl");
                return dfdl == null ? null : dfdlSampleRunner.write(dfdl, sample, changes);
            }
            case "drb-java" -> {
                String sdf = (String) model.getAttribute("drbJava");
                return sdf == null ? null : drbSampleRunner.write(sdf, sample, changes);
            }
            case "drb-python" -> {
                String module = (String) model.getAttribute("drbPython");
                if (module == null) {
                    return null;
                }
                Map<String, String> byPath = new LinkedHashMap<>();
                changes.forEach((path, value) -> byPath.put(path.toString(), value));
                DrbPythonSampleRunner.WriteOutcome outcome = drbPythonSampleRunner.write(module,
                        def.handWritten(DescriptionLanguage.DRB_PYTHON).orElse(null),
                        drbGenerator.pythonFactoryClassName(def), drbGenerator.pythonDriverId(def), sample, byPath);
                if (outcome.error() != null) {
                    return WriteBackResult.failure(outcome.error());
                }
                return outcome.reference() == null ? WriteBackResult.of(sample, outcome.written(), null)
                        : WriteBackResult.of(outcome.reference(), outcome.written(), "The add-in's prepare() changed "
                                + "the file's bytes before they were read, and it has no restore() to undo that, so "
                                + "the written bytes are compared with what prepare() produced, not with the sample.");
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * {@code path = value} lines: blank lines and lines starting with
     * {@code #} are skipped; a value in double quotes keeps its spaces.
     *
     * @throws IllegalArgumentException naming the line that isn't a change
     */
    public static Map<info.oais.infomodel.structure.ElementPath, String> parseChanges(String text) {
        Map<info.oais.infomodel.structure.ElementPath, String> changes = new LinkedHashMap<>();
        String[] lines = text.replace("\r\n", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                throw new IllegalArgumentException("Line " + (i + 1) + " isn't a change: write path = value, "
                        + "e.g. /header/count = 3");
            }
            String value = line.substring(equals + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            try {
                changes.put(info.oais.infomodel.structure.ElementPath.parse(line.substring(0, equals)), value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": " + e.getMessage());
            }
        }
        return changes;
    }

    private static String writtenName(String sampleName, String engine, boolean changed) {
        String base = DrbPythonSampleRunner.safeName(sampleName);
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String extension = dot > 0 ? base.substring(dot) : "";
        return stem + (changed ? "-changed-" : "-written-") + engine + extension;
    }

    /** The file the last write-back produced, kept in the session. */
    @GetMapping("/write-back/download")
    public ResponseEntity<ByteArrayResource> downloadWritten(HttpSession session) {
        if (!(session.getAttribute(WRITTEN_KEY) instanceof WrittenFile file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(new ByteArrayResource(file.bytes()));
    }

    private void addSampleResult(Model model, String attribute, SampleDecodeResult result, MultipartFile sample) {
        model.addAttribute(attribute, result);
        model.addAttribute("sampleFileName", sample.getOriginalFilename());
        model.addAttribute("sampleFileSize", sample.getSize());
    }

    private void populatePreview(FormatDefinition def, Model model) {
        model.addAttribute("def", def);
        Map<String, String> notGenerated = new LinkedHashMap<>();
        Map<String, Boolean> byHand = new LinkedHashMap<>();
        Map<String, Boolean> outOfDate = new LinkedHashMap<>();
        model.addAttribute("kaitai", effective(def, DescriptionLanguage.KAITAI, "kaitai", notGenerated, byHand, outOfDate,
                () -> kaitaiGenerator.generate(def)));
        model.addAttribute("dfdl", effective(def, DescriptionLanguage.DFDL, "dfdl", notGenerated, byHand, outOfDate,
                () -> dfdlGenerator.generate(def)));
        model.addAttribute("drbPython", generated(def, DescriptionLanguage.DRB_PYTHON, "drbPython", notGenerated,
                () -> drbGenerator.generate(def, DrbTarget.PYTHON)));
        model.addAttribute("drbPythonPackage", drbGenerator.pythonDistributionName(def));
        model.addAttribute("drbPythonAddOns", DrbGenerator.ADDON_KINDS.stream()
                .map(k -> drbGenerator.pythonDriverId(def) + "_" + k).toList());
        model.addAttribute("drbPythonAddIn", def.handWritten(DescriptionLanguage.DRB_PYTHON).orElse(null));
        model.addAttribute("drbPythonRunsAddIns", drbPythonSampleRunner.runsAddIns());
        model.addAttribute("drbPythonVersion", drbPythonSampleRunner.drbVersion().orElse(null));
        model.addAttribute("drbPythonUnavailable", drbPythonSampleRunner.notAvailableMessage());
        model.addAttribute("drbJava", effective(def, DescriptionLanguage.DRB, "drbJava", notGenerated, byHand, outOfDate,
                () -> drbGenerator.generate(def, DrbTarget.JAVA)));
        model.addAttribute("notGenerated", notGenerated);
        model.addAttribute("byHand", byHand);
        model.addAttribute("outOfDate", outOfDate);
        model.addAttribute("allEntities", archive.listAllEntities());
    }

    /**
     * The text written by hand for {@code language} if there is one (noting
     * whether the tree has changed since), otherwise what's generated.
     */
    private static String effective(FormatDefinition def, DescriptionLanguage language, String key,
                                    Map<String, String> notGenerated, Map<String, Boolean> byHand,
                                    Map<String, Boolean> outOfDate, java.util.function.Supplier<String> generate) {
        java.util.Optional<String> hand = def.handWritten(language);
        if (hand.isPresent() && def.getKind() == FormatDefinitionKind.BYTE_LAYOUT) {
            byHand.put(key, true);
            outOfDate.put(key, def.handWrittenOutOfDate(language, generatedOrNull(generate)));
            return hand.get();
        }
        return generated(def, language, key, notGenerated, generate);
    }

    private static String generatedOrNull(java.util.function.Supplier<String> generate) {
        try {
            return generate.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** What to line a sample test's result up against: nothing for a description written by hand. */
    private static FormatDescription alignWith(FormatDefinition def, DescriptionLanguage language) {
        return def.handWritten(language).isPresent() ? null : def.toFormatDescription();
    }

    /**
     * A generator's output, or null (with the reason under {@code key} in
     * {@code notGenerated}) when a byte layout isn't meant for that language,
     * or uses a feature it can't express.
     */
    private static String generated(FormatDefinition def, DescriptionLanguage language, String key,
                                    Map<String, String> notGenerated, java.util.function.Supplier<String> generate) {
        if (def.getKind() == FormatDefinitionKind.BYTE_LAYOUT && !def.targets(language)) {
            notGenerated.put(key, "Not generated: " + language.label()
                    + " isn't one of this description's languages (see Details in the editor).");
            return null;
        }
        try {
            return generate.get();
        } catch (Feature.UnsupportedFeatureException e) {
            notGenerated.put(key, e.getMessage());
            return null;
        }
    }

    @GetMapping("/download/{format}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String format, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            return downloadGenerated(format, def);
        } catch (Feature.UnsupportedFeatureException e) {
            return ResponseEntity.status(409).contentType(MediaType.TEXT_PLAIN)
                    .body(new ByteArrayResource(e.getMessage().getBytes(StandardCharsets.UTF_8)));
        }
    }

    private ResponseEntity<ByteArrayResource> downloadGenerated(String format, FormatDefinition def) {
        String text;
        String filename;
        MediaType mediaType;
        switch (format) {
            case "kaitai" -> {
                text = def.handWritten(DescriptionLanguage.KAITAI).orElseGet(() -> kaitaiGenerator.generate(def));
                filename = safeFileName(def.getName()) + ".ksy";
                mediaType = MediaType.parseMediaType("application/x-yaml");
            }
            case "dfdl" -> {
                text = def.handWritten(DescriptionLanguage.DFDL).orElseGet(() -> dfdlGenerator.generate(def));
                filename = safeFileName(def.getName()) + ".dfdl.xsd";
                mediaType = MediaType.APPLICATION_XML;
            }
            case "drb-python" -> {
                Map<String, String> pkg = drbGenerator.pythonDriverPackage(def);
                if (pkg != null) {
                    // A byte layout gets a whole installable driver package, not just the module.
                    String root = drbGenerator.pythonDistributionName(def);
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType("application/zip"))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + root + ".zip\"")
                            .body(new ByteArrayResource(zip(root, pkg)));
                }
                text = drbGenerator.generate(def, DrbTarget.PYTHON);
                filename = safeFileName(def.getName()) + ".py";
                mediaType = MediaType.TEXT_PLAIN;
            }
            case "drb-java" -> {
                text = def.getKind() == FormatDefinitionKind.BYTE_LAYOUT
                        ? def.handWritten(DescriptionLanguage.DRB).orElseGet(() -> drbGenerator.generate(def, DrbTarget.JAVA))
                        : drbGenerator.generate(def, DrbTarget.JAVA);
                boolean sdf = def.getKind() == FormatDefinitionKind.BYTE_LAYOUT;
                filename = safeFileName(def.getName()) + (sdf ? ".drb.xsd" : ".java");
                mediaType = sdf ? MediaType.APPLICATION_XML : MediaType.TEXT_PLAIN;
            }
            default -> {
                return ResponseEntity.notFound().build();
            }
        }
        if (text == null) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayResource resource = new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) String dataObjectId,
                        @RequestParam(required = false) List<String> formats,
                        HttpSession session) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        Map<String, String> generated = new LinkedHashMap<>();
        if (formats != null) {
            for (String format : formats) {
                try {
                    switch (format) {
                        case "kaitai" -> generated.put(savedLabel(def, DescriptionLanguage.KAITAI, "Kaitai Struct"),
                                def.handWritten(DescriptionLanguage.KAITAI).orElseGet(() -> kaitaiGenerator.generate(def)));
                        case "dfdl" -> generated.put(savedLabel(def, DescriptionLanguage.DFDL, "DFDL"),
                                def.handWritten(DescriptionLanguage.DFDL).orElseGet(() -> dfdlGenerator.generate(def)));
                        case "drb-python" -> generated.put(def.handWritten(DescriptionLanguage.DRB_PYTHON).isPresent()
                                        ? "DRB (Python, drb-python), with an add-in written by hand"
                                        : "DRB (Python, drb-python)", drbPythonWithAddIn(def));
                        case "drb-java" -> generated.put(def.getKind() == FormatDefinitionKind.BYTE_LAYOUT
                                        ? savedLabel(def, DescriptionLanguage.DRB, "DRB SDF schema (Java, fr.gael.drb)")
                                        : "DRB (Java, fr.gael.drb)",
                                def.getKind() == FormatDefinitionKind.BYTE_LAYOUT
                                        ? def.handWritten(DescriptionLanguage.DRB).orElseGet(() -> drbGenerator.generate(def, DrbTarget.JAVA))
                                        : drbGenerator.generate(def, DrbTarget.JAVA));
                        default -> { }
                    }
                } catch (Feature.UnsupportedFeatureException e) {
                    // Not offered on the preview page for this description; nothing to save.
                }
            }
        }
        String dataObjectIri = (dataObjectId == null || dataObjectId.isBlank()) ? null : archive.decodeId(dataObjectId);
        String savedIri = rdfService.saveToArchive(def, dataObjectIri, generated);
        session.removeAttribute(SESSION_KEY);
        return "redirect:/resource/" + archive.encodeId(savedIri);
    }

    /** The driver module, followed by the hand-written add-in (its {@code addin.py}) if there is one. */
    private String drbPythonWithAddIn(FormatDefinition def) {
        String module = drbGenerator.generate(def, DrbTarget.PYTHON);
        return def.handWritten(DescriptionLanguage.DRB_PYTHON)
                .map(addIn -> module + "\n\n# " + "=".repeat(20) + " addin.py, written by hand " + "=".repeat(20)
                        + "\n\n" + addIn)
                .orElse(module);
    }

    private static String savedLabel(FormatDefinition def, DescriptionLanguage language, String label) {
        return def.handWritten(language).isPresent() ? label + ", written by hand" : label;
    }

    /**
     * {@code kaitai}, {@code dfdl}, {@code drb}: descriptions written by hand; {@code drb-python}: an add-in
     * to the generated drb-python driver.
     */
    private static DescriptionLanguage handLanguage(String path) {
        return switch (path) {
            case "kaitai" -> DescriptionLanguage.KAITAI;
            case "dfdl" -> DescriptionLanguage.DFDL;
            case "drb" -> DescriptionLanguage.DRB;
            case "drb-python" -> DescriptionLanguage.DRB_PYTHON;
            default -> throw new IllegalArgumentException("Descriptions in '" + path + "' can't be written by hand.");
        };
    }

    private static String handPath(DescriptionLanguage language) {
        return switch (language) {
            case KAITAI -> "kaitai";
            case DFDL -> "dfdl";
            case DRB -> "drb";
            case DRB_PYTHON -> "drb-python";
        };
    }

    /** What {@code language} generates from the tree now, or null (nothing yet, or it can't express it). */
    private String generatedNow(FormatDefinition def, DescriptionLanguage language) {
        if (def.getRoot().children().isEmpty() || HandWrittenDescriptions.isAddIn(language)) {
            // An add-in adds to what's generated rather than replacing it, so it can't fall behind.
            return null;
        }
        return generatedOrNull(() -> switch (language) {
            case KAITAI -> kaitaiGenerator.generate(def);
            case DFDL -> dfdlGenerator.generate(def);
            default -> drbGenerator.generate(def, DrbTarget.JAVA);
        });
    }

    /**
     * Writing a description by hand, for what the element tree can't express.
     * Starts from the hand-written text, else what the tree generates, else a
     * worked example; {@code example} loads one of the worked examples instead.
     */
    @GetMapping("/hand/{language}")
    public String handEditor(@PathVariable String language, @RequestParam(required = false) String example,
                             HttpSession session, Model model) {
        FormatDefinition def = draft(session);
        if (def == null || def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return "redirect:/repinfo-tools";
        }
        DescriptionLanguage lang = handLanguage(language);
        String text = HandWrittenDescriptions.examples(lang).stream().filter(e -> e.id().equals(example)).findFirst()
                .map(HandWrittenDescriptions.Example::text)
                .or(() -> def.handWritten(lang))
                .orElseGet(() -> {
                    String generated = generatedNow(def, lang);
                    return generated != null ? generated : HandWrittenDescriptions.skeleton(lang);
                });
        populateHandEditor(def, lang, text, List.of(), model);
        return "repinfo-tools/hand";
    }

    @PostMapping("/hand/{language}")
    public String saveHandWritten(@PathVariable String language, @RequestParam String text, HttpSession session,
                                  Model model) {
        FormatDefinition def = draft(session);
        if (def == null || def.getKind() != FormatDefinitionKind.BYTE_LAYOUT) {
            return "redirect:/repinfo-tools";
        }
        DescriptionLanguage lang = handLanguage(language);
        String normalised = text.replace("\r\n", "\n");
        List<String> problems = new ArrayList<>(HandWrittenDescriptions.check(lang, normalised));
        if (problems.isEmpty() && HandWrittenDescriptions.isAddIn(lang)) {
            problems.addAll(drbPythonSampleRunner.checkAddIn(normalised));
        }
        if (!problems.isEmpty()) {
            populateHandEditor(def, lang, normalised, problems, model);
            return "repinfo-tools/hand";
        }
        def.setHandWritten(lang, normalised, generatedNow(def, lang));
        return "redirect:/repinfo-tools/preview#" + language + "-section";
    }

    @PostMapping("/hand/{language}/revert")
    public String revertHandWritten(@PathVariable String language, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.clearHandWritten(handLanguage(language));
        }
        return "redirect:/repinfo-tools/preview#" + language + "-section";
    }

    private void populateHandEditor(FormatDefinition def, DescriptionLanguage lang, String text, List<String> problems,
                                    Model model) {
        model.addAttribute("def", def);
        model.addAttribute("language", lang);
        model.addAttribute("languagePath", handPath(lang));
        model.addAttribute("text", text);
        model.addAttribute("problems", problems);
        model.addAttribute("examples", HandWrittenDescriptions.examples(lang));
        model.addAttribute("isHandWritten", def.handWritten(lang).isPresent());
        model.addAttribute("outOfDate", def.handWrittenOutOfDate(lang, generatedNow(def, lang)));
        model.addAttribute("addIn", HandWrittenDescriptions.isAddIn(lang));
        model.addAttribute("runsAddIns", drbPythonSampleRunner.runsAddIns());
        model.addAttribute("hooks", DrbPythonSampleRunner.ADD_IN_HOOKS);
    }

    private FormatDefinition draft(HttpSession session) {
        Object attr = session.getAttribute(SESSION_KEY);
        return attr instanceof FormatDefinition def ? def : null;
    }

    private List<Integer> parseShape(String shape) {
        if (shape == null || shape.isBlank()) {
            return null;
        }
        List<Integer> dims = new ArrayList<>();
        for (String part : shape.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                try {
                    dims.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException ignored) {
                    // Skip anything that isn't a plain integer rather than failing the whole add.
                }
            }
        }
        return dims.isEmpty() ? null : dims;
    }

    /** Zips {@code files} (relative path -> text) under one top-level {@code root} folder. */
    private static byte[] zip(String root, Map<String, String> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(root + "/" + file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private String safeFileName(String name) {
        String slug = name.strip().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "format" : slug;
    }
}
