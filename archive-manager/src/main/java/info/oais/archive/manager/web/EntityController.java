package info.oais.archive.manager.web;

import info.oais.archive.manager.model.BridgeMapping;
import info.oais.archive.manager.model.EditableProperty;
import info.oais.archive.manager.model.EditableRelationship;
import info.oais.archive.manager.model.PropertyOption;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.EditService;
import info.oais.archive.manager.service.OntologyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The generic editor: create a new individual of any RiC-O or OAIS class,
 * and add/remove its type(s), literal properties, and relationships to
 * other resources. Unlike {@link RecordController}'s narrower "new record"
 * form, this works for any resource regardless of class.
 */
@Controller
@RequestMapping("/entities")
public class EntityController {

    /**
     * Prefix marking a relationship-target select option as "create a new
     * entity of this class" rather than an existing entity's encoded id --
     * followed immediately by the class IRI, e.g.
     * {@code "new:http://ontology.oais.org/im/TransformationInformationProperty"}.
     * Matched literally in {@link #addRelationship}; must stay in sync with
     * the same prefix used in {@code entities/edit.html}'s JavaScript.
     */
    private static final String NEW_TARGET_PREFIX = "new:";

    private final ArchiveService archive;
    private final OntologyService ontology;
    private final EditService edit;

    public EntityController(ArchiveService archive, OntologyService ontology, EditService edit) {
        this.archive = archive;
        this.ontology = ontology;
        this.edit = edit;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String type,
                        @RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("resultPage", archive.listAllEntitiesPaged(type, page, 50));
        model.addAttribute("selectedType", type);
        model.addAttribute("typesInUse", archive.typesInUse());
        return "entities/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("classes", ontology.listClasses());
        return "entities/new";
    }

    @PostMapping
    public String create(@RequestParam(required = false) String classIri,
                          @RequestParam(required = false) String customClass) {
        String resolved = (customClass != null && !customClass.isBlank())
                ? ontology.resolveIri(customClass)
                : (classIri != null && !classIri.isBlank() ? classIri : null);
        if (resolved == null) {
            // Neither a class was chosen nor a custom class typed in -- go back
            // to the form rather than trying to create a resource with no type.
            return "redirect:/entities/new";
        }
        String newIri = edit.createEntity(resolved);
        return "redirect:/entities/" + edit.encodeId(newIri) + "/edit";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("id", id);
        model.addAttribute("iri", iri);
        model.addAttribute("title", archive.label(iri));
        model.addAttribute("pageTitle", "Edit: " + archive.label(iri));
        model.addAttribute("types", archive.typesAsOptions(iri));
        List<EditableProperty> properties = archive.editableLiteralProperties(iri);
        List<EditableRelationship> outgoing = archive.editableOutgoingRelationships(iri);
        List<PropertyOption> datatypeProperties = ontology.listDatatypeProperties();
        List<PropertyOption> objectProperties = ontology.listObjectProperties();
        model.addAttribute("properties", properties);
        model.addAttribute("outgoing", outgoing);
        model.addAttribute("incoming", archive.editableIncomingRelationships(iri));
        model.addAttribute("allClasses", ontology.listClasses());
        model.addAttribute("datatypeProperties", datatypeProperties);
        model.addAttribute("objectProperties", objectProperties);
        model.addAttribute("allEntities", archive.listAllEntities());
        model.addAttribute("propertyRangeTypes", ontology.propertyRangeTypeLocalNames());
        model.addAttribute("propertyRangeClasses", ontology.propertyRangeClasses());

        // Property-level bridge correspondences (e.g. rico:technicalCharacteristics
        // relatedMatch im:OtherRepresentationInformation), surfaced two ways:
        // - a rich inline note next to properties/relationships this entity already
        //   has set (propertyBridgeNotes, keyed by property IRI);
        // - a plain-text hover tooltip on the picker <option>s themselves, so the
        //   correspondence is visible before you even add the property
        //   (propertyBridgeTooltips, also keyed by property IRI).
        // Both are driven by the same bridgeMappingsFor() lookup -- no mapping
        // logic is duplicated or hard-coded here, just presented two ways.
        Map<String, List<BridgeMapping>> bridgeNotes = new LinkedHashMap<>();
        for (EditableProperty p : properties) {
            addBridgeNotesIfAny(bridgeNotes, p.propertyIri());
        }
        for (EditableRelationship r : outgoing) {
            addBridgeNotesIfAny(bridgeNotes, r.propertyIri());
        }
        model.addAttribute("propertyBridgeNotes", bridgeNotes);

        Map<String, String> bridgeTooltips = new LinkedHashMap<>();
        for (PropertyOption p : datatypeProperties) {
            addTooltipIfAny(bridgeTooltips, p.iri());
        }
        for (PropertyOption p : objectProperties) {
            addTooltipIfAny(bridgeTooltips, p.iri());
        }
        model.addAttribute("propertyBridgeTooltips", bridgeTooltips);

        return "entities/edit";
    }

    private void addBridgeNotesIfAny(Map<String, List<BridgeMapping>> bridgeNotes, String propertyIri) {
        if (bridgeNotes.containsKey(propertyIri)) {
            return;
        }
        List<BridgeMapping> mappings = archive.bridgeMappingsFor(propertyIri);
        if (!mappings.isEmpty()) {
            bridgeNotes.put(propertyIri, mappings);
        }
    }

    private void addTooltipIfAny(Map<String, String> tooltips, String propertyIri) {
        if (tooltips.containsKey(propertyIri)) {
            return;
        }
        List<BridgeMapping> mappings = archive.bridgeMappingsFor(propertyIri);
        if (mappings.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (BridgeMapping m : mappings) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("skos:").append(m.relationLocalName()).append(" im:").append(m.targetLocalName());
            if (m.rationale() != null && !m.rationale().isBlank()) {
                sb.append(" -- ").append(m.rationale());
            }
        }
        tooltips.put(propertyIri, sb.toString());
    }

    @PostMapping("/{id}/types")
    public String addType(@PathVariable String id,
                           @RequestParam(required = false) String typeIri,
                           @RequestParam(required = false) String customType) {
        String iri = archive.decodeId(id);
        String resolved = (customType != null && !customType.isBlank()) ? ontology.resolveIri(customType) : typeIri;
        if (resolved != null && !resolved.isBlank()) {
            edit.addType(iri, resolved);
        }
        return "redirect:/entities/" + id + "/edit";
    }

    @PostMapping("/{id}/types/delete")
    public String removeType(@PathVariable String id, @RequestParam String typeIri) {
        String iri = archive.decodeId(id);
        edit.removeType(iri, typeIri);
        return "redirect:/entities/" + id + "/edit";
    }

    @PostMapping("/{id}/properties")
    public String addProperty(@PathVariable String id,
                               @RequestParam(required = false) String property,
                               @RequestParam(required = false) String customProperty,
                               @RequestParam String value) {
        String iri = archive.decodeId(id);
        String resolved = (customProperty != null && !customProperty.isBlank())
                ? ontology.resolveIri(customProperty)
                : property;
        if (resolved != null && !resolved.isBlank()) {
            edit.addLiteral(iri, resolved, value);
        }
        return "redirect:/entities/" + id + "/edit";
    }

    @PostMapping("/{id}/properties/delete")
    public String removeProperty(@PathVariable String id,
                                  @RequestParam String propertyIri,
                                  @RequestParam String value) {
        String iri = archive.decodeId(id);
        edit.removeLiteral(iri, propertyIri, value);
        return "redirect:/entities/" + id + "/edit";
    }

    @PostMapping("/{id}/relationships")
    public String addRelationship(@PathVariable String id,
                                   @RequestParam(required = false) String property,
                                   @RequestParam(required = false) String customProperty,
                                   @RequestParam(required = false) String targetId,
                                   @RequestParam(required = false) String customTarget,
                                   @RequestParam(required = false) String newEntityLabel) {
        String iri = archive.decodeId(id);
        String resolvedProperty = (customProperty != null && !customProperty.isBlank())
                ? ontology.resolveIri(customProperty)
                : property;
        String resolvedTarget = resolveOrCreateTarget(targetId, customTarget, newEntityLabel);
        if (resolvedProperty != null && !resolvedProperty.isBlank() && resolvedTarget != null) {
            edit.addRelationship(iri, resolvedProperty, resolvedTarget);
        }
        return "redirect:/entities/" + id + "/edit";
    }

    /**
     * Resolves the relationship-target select's value into an entity IRI --
     * an existing entity's encoded id, a pasted IRI, or (when the picker's
     * value carries {@link #NEW_TARGET_PREFIX}) a brand-new entity of the
     * chosen range class, created on the spot so it can be linked in the
     * same submit instead of requiring a separate trip through
     * {@code /entities/new} first.
     */
    private String resolveOrCreateTarget(String targetId, String customTarget, String newEntityLabel) {
        if (customTarget != null && !customTarget.isBlank()) {
            return ontology.resolveIri(customTarget);
        }
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        if (targetId.startsWith(NEW_TARGET_PREFIX)) {
            String classIri = targetId.substring(NEW_TARGET_PREFIX.length());
            String newIri = edit.createEntity(classIri);
            if (newEntityLabel != null && !newEntityLabel.isBlank()) {
                edit.addLiteral(newIri, Ns.RDFS + "label", newEntityLabel);
            }
            return newIri;
        }
        return archive.decodeId(targetId);
    }

    @PostMapping("/{id}/relationships/delete")
    public String removeRelationship(@PathVariable String id,
                                      @RequestParam String propertyIri,
                                      @RequestParam String otherIri,
                                      @RequestParam String direction) {
        String iri = archive.decodeId(id);
        if ("incoming".equals(direction)) {
            edit.removeRelationship(otherIri, propertyIri, iri);
        } else {
            edit.removeRelationship(iri, propertyIri, otherIri);
        }
        return "redirect:/entities/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        String iri = archive.decodeId(id);
        edit.deleteResource(iri);
        return "redirect:/entities";
    }
}
