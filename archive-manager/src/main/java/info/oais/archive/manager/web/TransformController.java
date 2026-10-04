package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.BitStore;
import info.oais.archive.manager.service.transform.PropertyCheck;
import info.oais.archive.manager.service.transform.PropertyCheck.Meaning;
import info.oais.archive.manager.service.transform.TransformationException;
import info.oais.archive.manager.service.transform.TransformationMapping;
import info.oais.archive.manager.service.transform.TransformationService;
import info.oais.archive.manager.service.transform.TransformationService.Target;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline.SchemaElement;
import jakarta.servlet.http.HttpSession;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transforming a Data Object into another format (see
 * {@link TransformationService}): choose the new format, say what fills each
 * of its elements, choose the Transformation Information Properties to check,
 * try it, and do it. Editing only, like RepInfo Tools.
 */
@Controller
@RequestMapping("/transform")
public class TransformController {

    private static final String TRIAL = "transformTrial";

    private final TransformationService transformations;
    private final ArchiveService archive;

    public TransformController(TransformationService transformations, ArchiveService archive) {
        this.transformations = transformations;
        this.archive = archive;
    }

    @GetMapping("/{id}")
    public String form(@PathVariable String id, @RequestParam(required = false) String target, Model model) {
        String dataObject = archive.decodeId(id);
        try {
            prepare(id, dataObject, target, null, model);
        } catch (IOException | RuntimeException e) {
            model.addAttribute("error", message(e));
        }
        return "transform/form";
    }

    @PostMapping("/{id}/try")
    public String tryIt(@PathVariable String id, @RequestParam Map<String, String> form, HttpSession session,
                        Model model) {
        String dataObject = archive.decodeId(id);
        try {
            Context c = prepare(id, dataObject, form.get("target"), form, model);
            if (c == null) {
                throw new TransformationException("Choose the format to transform it into.");
            }
            TransformationService.Trial trial = transformations.run(dataObject, c.target(), c.mapping(),
                    c.properties());
            session.setAttribute(TRIAL, new SavedTrial(id, trial.written()));
            model.addAttribute("trial", trial);
        } catch (IOException | RuntimeException e) {
            model.addAttribute("error", message(e));
        }
        return "transform/form";
    }

    @PostMapping("/{id}")
    public String transform(@PathVariable String id, @RequestParam Map<String, String> form, Model model) {
        String dataObject = archive.decodeId(id);
        try {
            Context c = prepare(id, dataObject, form.get("target"), form, model);
            if (c == null) {
                throw new TransformationException("Choose the format to transform it into.");
            }
            TransformationService.Trial trial = transformations.run(dataObject, c.target(), c.mapping(),
                    c.properties());
            String transformation = transformations.record(dataObject, c.target(), c.mapping(), trial,
                    c.properties(), ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
            return "redirect:/resource/" + archive.encodeId(transformation);
        } catch (IOException | RuntimeException e) {
            model.addAttribute("error", message(e));
            return "transform/form";
        }
    }

    /**
     * Records a Transformation done with another application: the uploaded
     * result, and the form saying what was done -- the application, who did
     * it and when, how, what the result is interpreted using, whether it is
     * reversible, and each Transformation Information Property's outcome.
     */
    @PostMapping("/{id}/external")
    public String external(@PathVariable String id, @RequestParam("file") MultipartFile file,
                           @RequestParam Map<String, String> form, Model model) {
        String dataObject = archive.decodeId(id);
        model.addAttribute("ext", form);
        Path dir = null;
        try {
            prepare(id, dataObject, null, null, model);
            TransformationService.ExternalTransformation t = externalFrom(form);
            if (file.isEmpty()) {
                throw new TransformationException("Choose the transformed file to upload.");
            }
            String fileName = BitStore.safeName(file.getOriginalFilename());
            dir = Files.createTempDirectory("transform-upload-");
            Path uploaded = dir.resolve(fileName);
            byte[] bytes = file.getBytes();
            Files.write(uploaded, bytes);
            List<PropertyCheck> checks = transformations.checkExternal(dataObject, t, uploaded);
            String transformation = transformations.recordExternal(dataObject, t, bytes, fileName, checks,
                    ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
            return "redirect:/resource/" + archive.encodeId(transformation);
        } catch (IOException | RuntimeException e) {
            model.addAttribute("externalError", message(e));
            return "transform/form";
        } finally {
            if (dir != null) {
                try (var files = Files.walk(dir)) {
                    files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                } catch (IOException ignored) {
                    // a temporary folder; the system cleans it up eventually
                }
            }
        }
    }

    /** The external Transformation the form describes; throws saying what's missing. */
    private TransformationService.ExternalTransformation externalFrom(Map<String, String> form) {
        String application = required(form, "application", "the application used");
        String performedBy = required(form, "performedBy", "who did the Transformation");
        String method = required(form, "method", "what was done");
        LocalDate performedOn;
        try {
            String day = field(form, "performedOn");
            performedOn = day.isEmpty() ? LocalDate.now() : LocalDate.parse(day);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("The date isn't a date: " + form.get("performedOn"));
        }
        String repInfo = field(form, "repInfo");
        if (!repInfo.isEmpty() && transformations.representationInformation().stream()
                .noneMatch(c -> c.iri().equals(repInfo))) {
            throw new IllegalArgumentException("That Representation Information isn't in the archive.");
        }
        List<TransformationService.ExternalCheck> checks = new java.util.ArrayList<>();
        for (String key : form.keySet()) {
            if (!key.startsWith("ext.tip.") || !"on".equals(form.get(key))) {
                continue;
            }
            String path = key.substring("ext.tip.".length());
            String target = field(form, "ext.target." + path);
            String outcome = field(form, "ext.outcome." + path);
            String tolerance = field(form, "ext.tol." + path);
            checks.add(new TransformationService.ExternalCheck(path, target.isEmpty() ? null : target,
                    java.util.Arrays.stream(PropertyCheck.Outcome.values()).filter(o -> o.text().equals(outcome))
                            .findFirst().orElse(PropertyCheck.Outcome.NOT_CHECKED),
                    field(form, "ext.evidence." + path), tolerance.isEmpty() ? null : decimal(tolerance, path)));
        }
        return new TransformationService.ExternalTransformation(application, field(form, "applicationVersion"),
                field(form, "applicationUrl"), performedBy, performedOn, method, repInfo.isEmpty() ? null : repInfo,
                "on".equals(form.get("reversible")), field(form, "reversibility"), checks);
    }

    private static String required(Map<String, String> form, String name, String what) {
        String value = field(form, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Say " + what + ".");
        }
        return value;
    }

    private static String field(Map<String, String> form, String name) {
        String value = form.get(name);
        return value == null ? "" : value.strip();
    }

    /** The data the last "Try it" made, to look at before transforming. */
    @GetMapping("/{id}/trial")
    public ResponseEntity<byte[]> trial(@PathVariable String id, HttpSession session) {
        Object saved = session.getAttribute(TRIAL);
        if (!(saved instanceof SavedTrial t) || !t.id().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transformed.bin\"")
                .body(t.written());
    }

    private record SavedTrial(String id, byte[] written) implements java.io.Serializable {
    }

    private record Context(Target target, TransformationMapping mapping, Map<String, BigDecimal> properties) {
    }

    /**
     * Fills the model for the page: the Data Object, the formats it can be
     * transformed into and, once one is chosen, the rows of its elements and
     * the properties to check -- from {@code form} if given, else a first
     * guess.
     */
    private Context prepare(String id, String dataObject, String targetIri, Map<String, String> form,
                            Model model) throws IOException {
        model.addAttribute("id", id);
        model.addAttribute("title", archive.label(dataObject));
        model.addAttribute("transformable", transformations.transformable(dataObject));
        List<Target> targets = transformations.targets();
        model.addAttribute("targets", targets);
        // For a Transformation done with another application: what the result can be interpreted using, and
        // the old format's values, any of which can be a Transformation Information Property to record.
        model.addAttribute("repInfoChoices", transformations.representationInformation());
        model.addAttribute("today", LocalDate.now().toString());
        if (!model.containsAttribute("ext")) {
            model.addAttribute("ext", Map.of());
        }
        try {
            model.addAttribute("externalRows", TransformationForm.sourceElements(
                    transformations.sourceStructure(dataObject), transformations.meanings(dataObject)).stream()
                    .filter(TransformationForm.SourceElement::value).toList());
        } catch (IOException | RuntimeException e) {
            model.addAttribute("externalRows", List.of());
        }
        if (targetIri == null || targetIri.isBlank()) {
            return null;
        }
        Target target = transformations.target(targetIri).orElseThrow(() ->
                new TransformationException("That format can't be transformed into: it has no DFDL description"));
        model.addAttribute("target", target);
        SchemaElement targetOutline = transformations.outline(target);
        SchemaElement sourceOutline = transformations.sourceStructure(dataObject);
        Map<String, Meaning> sourceMeanings = transformations.meanings(dataObject);
        Map<String, Meaning> targetMeanings = transformations.meanings(target.iri());
        List<TransformationForm.SourceElement> sources = TransformationForm.sourceElements(sourceOutline,
                sourceMeanings);
        model.addAttribute("sources", sources);

        TransformationMapping mapping;
        String text = form == null ? "" : form.getOrDefault("mappingText", "");
        boolean useText = form != null && "on".equals(form.get("useText"));
        if (useText) {
            mapping = TransformationMapping.parse(text);
        } else if (form != null) {
            mapping = TransformationForm.fromForm(targetOutline, form);
        } else {
            mapping = TransformationForm.guess(targetOutline, sources, targetMeanings);
        }
        model.addAttribute("rows", TransformationForm.rows(targetOutline, mapping, targetMeanings));
        model.addAttribute("mappingText", mapping.text());

        Map<String, String> existing = new LinkedHashMap<>();
        transformations.informationProperties(dataObject).forEach(p -> existing.putIfAbsent(p.path(), p.label()));
        Map<String, BigDecimal> properties = new LinkedHashMap<>();
        List<PropertyRow> propertyRows = new java.util.ArrayList<>();
        for (TransformationForm.SourceElement s : sources) {
            if (!s.value()) {
                continue;
            }
            boolean checked = form == null ? existing.containsKey(s.path()) || mapping.copyOf(s.path()).isPresent()
                    : "on".equals(form.get("tip." + s.path()));
            String tolerance = form == null ? "" : Optional.ofNullable(form.get("tol." + s.path())).orElse("").strip();
            if (checked) {
                properties.put(s.path(), tolerance.isEmpty() ? null : decimal(tolerance, s.path()));
            }
            propertyRows.add(new PropertyRow(s.path(), s.display(), existing.containsKey(s.path()), checked,
                    tolerance));
        }
        model.addAttribute("propertyRows", propertyRows);
        return new Context(target, mapping, properties);
    }

    /** A source element whose values could be a Transformation Information Property to check. */
    record PropertyRow(String path, String display, boolean existing, boolean checked, String tolerance) {
    }

    private static BigDecimal decimal(String text, String path) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The tolerance for " + path + " isn't a number: " + text);
        }
    }

    private static String message(Exception e) {
        if (e instanceof TransformationException || e instanceof IllegalArgumentException
                || e instanceof StructureInterpretationException) {
            return e.getMessage();
        }
        return "The Transformation couldn't be done: " + e.getMessage();
    }
}
