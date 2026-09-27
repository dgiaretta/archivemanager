package info.oais.archive.manager.web;

import jakarta.servlet.http.HttpSession;
import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
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
import info.oais.archive.manager.service.format.KaitaiGenerator;
import info.oais.archive.manager.service.format.SampleDecodeResult;
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

    public RepInfoToolController(ArchiveService archive, KaitaiGenerator kaitaiGenerator, DfdlGenerator dfdlGenerator,
                                  DrbGenerator drbGenerator, FormatDescriptionRdfService rdfService,
                                  DfdlSampleRunner dfdlSampleRunner, DrbPythonSampleRunner drbPythonSampleRunner,
                                  DrbSampleRunner drbSampleRunner) {
        this.archive = archive;
        this.kaitaiGenerator = kaitaiGenerator;
        this.dfdlGenerator = dfdlGenerator;
        this.drbGenerator = drbGenerator;
        this.rdfService = rdfService;
        this.dfdlSampleRunner = dfdlSampleRunner;
        this.drbPythonSampleRunner = drbPythonSampleRunner;
        this.drbSampleRunner = drbSampleRunner;
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
        session.setAttribute(SESSION_KEY, def);
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/discard")
    public String discard(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return "redirect:/repinfo-tools";
    }

    @GetMapping("/edit")
    public String edit(HttpSession session, Model model) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return "redirect:/repinfo-tools";
        }
        model.addAttribute("def", def);
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("byteOrders", ByteOrder.values());
        model.addAttribute("nodeKinds", Hdf5NodeKind.values());
        return "repinfo-tools/edit";
    }

    @PostMapping("/details")
    public String updateDetails(@RequestParam String name, @RequestParam(required = false) String notes,
                                 @RequestParam(required = false) String fileExtensions,
                                 @RequestParam ByteOrder defaultByteOrder, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.setName(name);
            def.setNotes(notes);
            def.setFileExtensions(fileExtensions);
            def.setDefaultByteOrder(defaultByteOrder);
        }
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/fields")
    public String addField(@RequestParam String name, @RequestParam FieldType type,
                            @RequestParam(required = false) Integer lengthBytes,
                            @RequestParam(required = false) ByteOrder byteOrder,
                            @RequestParam(required = false) String semanticName,
                            @RequestParam(required = false) String definition,
                            @RequestParam(required = false) String units,
                            HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null && !name.isBlank()) {
            def.addField(new FormatField(name, type, lengthBytes, byteOrder, semanticName, definition, units));
        }
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/fields/{index}/delete")
    public String deleteField(@PathVariable int index, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.removeField(index);
        }
        return "redirect:/repinfo-tools/edit";
    }

    @PostMapping("/fields/{index}/move")
    public String moveField(@PathVariable int index, @RequestParam int delta, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def != null) {
            def.moveField(index, delta);
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
                : dfdlSampleRunner.run(dfdl, sample.getBytes());
        addSampleResult(model, "dfdlTest", result, sample);
        return "repinfo-tools/preview";
    }

    /**
     * Same as {@link #testDfdl}, but runs the draft's generated drb-python
     * driver in a Python process (see {@link DrbPythonSampleRunner}); needs
     * drb-python installed on the machine running this app.
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
        SampleDecodeResult result = sample.isEmpty()
                ? SampleDecodeResult.failure(EMPTY_SAMPLE)
                : drbPythonSampleRunner.run(drbGenerator.generate(def, DrbTarget.PYTHON),
                        drbGenerator.pythonFactoryClassName(def), sample.getBytes(), sample.getOriginalFilename());
        addSampleResult(model, "drbPythonTest", result, sample);
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
        SampleDecodeResult result = sample.isEmpty()
                ? SampleDecodeResult.failure(EMPTY_SAMPLE)
                : drbSampleRunner.run(drbGenerator.generate(def, DrbTarget.JAVA), sample.getBytes());
        addSampleResult(model, "drbJavaTest", result, sample);
        return "repinfo-tools/preview";
    }

    private static final String EMPTY_SAMPLE = "Choose a non-empty sample file to test against.";

    private void addSampleResult(Model model, String attribute, SampleDecodeResult result, MultipartFile sample) {
        model.addAttribute(attribute, result);
        model.addAttribute("sampleFileName", sample.getOriginalFilename());
        model.addAttribute("sampleFileSize", sample.getSize());
    }

    private void populatePreview(FormatDefinition def, Model model) {
        model.addAttribute("def", def);
        model.addAttribute("kaitai", kaitaiGenerator.generate(def));
        model.addAttribute("dfdl", dfdlGenerator.generate(def));
        model.addAttribute("drbPython", drbGenerator.generate(def, DrbTarget.PYTHON));
        model.addAttribute("drbPythonPackage", drbGenerator.pythonDistributionName(def));
        model.addAttribute("drbPythonVersion", drbPythonSampleRunner.drbVersion().orElse(null));
        model.addAttribute("drbPythonUnavailable", drbPythonSampleRunner.notAvailableMessage());
        model.addAttribute("drbJava", drbGenerator.generate(def, DrbTarget.JAVA));
        model.addAttribute("allEntities", archive.listAllEntities());
    }

    @GetMapping("/download/{format}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String format, HttpSession session) {
        FormatDefinition def = draft(session);
        if (def == null) {
            return ResponseEntity.notFound().build();
        }
        String text;
        String filename;
        MediaType mediaType;
        switch (format) {
            case "kaitai" -> {
                text = kaitaiGenerator.generate(def);
                filename = safeFileName(def.getName()) + ".ksy";
                mediaType = MediaType.parseMediaType("application/x-yaml");
            }
            case "dfdl" -> {
                text = dfdlGenerator.generate(def);
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
                text = drbGenerator.generate(def, DrbTarget.JAVA);
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
                switch (format) {
                    case "kaitai" -> generated.put("Kaitai Struct", kaitaiGenerator.generate(def));
                    case "dfdl" -> generated.put("DFDL", dfdlGenerator.generate(def));
                    case "drb-python" -> generated.put("DRB (Python, drb-python)", drbGenerator.generate(def, DrbTarget.PYTHON));
                    case "drb-java" -> generated.put(def.getKind() == FormatDefinitionKind.BYTE_LAYOUT
                            ? "DRB SDF schema (Java, fr.gael.drb)" : "DRB (Java, fr.gael.drb)", drbGenerator.generate(def, DrbTarget.JAVA));
                    default -> { }
                }
            }
        }
        String dataObjectIri = (dataObjectId == null || dataObjectId.isBlank()) ? null : archive.decodeId(dataObjectId);
        String savedIri = rdfService.saveToArchive(def, dataObjectIri, generated);
        session.removeAttribute(SESSION_KEY);
        return "redirect:/resource/" + archive.encodeId(savedIri);
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
