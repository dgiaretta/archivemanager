package info.oais.archive.manager.web;

import info.oais.archive.manager.model.Page;
import info.oais.archive.manager.model.ResourceSummary;
import info.oais.archive.manager.model.api.AddPropertyRequest;
import info.oais.archive.manager.model.api.AddRelationshipRequest;
import info.oais.archive.manager.model.api.AddTypeRequest;
import info.oais.archive.manager.model.api.CreateEntityRequest;
import info.oais.archive.manager.model.api.CreateRecordRequest;
import info.oais.archive.manager.model.api.RemovePropertyRequest;
import info.oais.archive.manager.model.api.RemoveRelationshipRequest;
import info.oais.archive.manager.model.api.RemoveTypeRequest;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.EditService;
import info.oais.archive.manager.service.OntologyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * The REST counterpart to the read-only endpoints below: every mutating
 * action here is a thin JSON wrapper around the exact same
 * {@link EditService}/{@link ArchiveService} calls the browser-facing
 * {@code EntityController}/{@code RecordController} forms already make --
 * no parallel write logic, just a different transport. Gated behind the
 * same session-cookie login as those MVC forms (see
 * {@code EditAuthInterceptor}, which lists each of these paths explicitly);
 * a REST client authenticates the same way a browser does, by
 * {@code POST}ing the edit password to {@code /login} first and reusing the
 * resulting session cookie.
 */
@RestController
@RequestMapping("/api")
public class ArchiveApiController {

    private final ArchiveService archiveService;
    private final EditService edit;
    private final OntologyService ontology;

    public ArchiveApiController(ArchiveService archiveService, EditService edit, OntologyService ontology) {
        this.archiveService = archiveService;
        this.edit = edit;
        this.ontology = ontology;
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

    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceSummary createRecord(@RequestBody CreateRecordRequest request) {
        String creatorIri = blankToNull(request.creatorId()) == null ? null : archiveService.decodeId(request.creatorId());
        String parentIri = blankToNull(request.parentId()) == null ? null : archiveService.decodeId(request.parentId());
        String iri = archiveService.createRecordResource(request.type(), request.title(), request.description(), creatorIri, parentIri);
        return archiveService.summarize(iri);
    }

    @PostMapping("/entities")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceSummary createEntity(@RequestBody CreateEntityRequest request) {
        String resolved = resolve(request.classIri(), request.customClass());
        if (resolved == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "classIri or customClass is required");
        }
        String iri = edit.createEntity(resolved);
        return archiveService.summarize(iri);
    }

    @DeleteMapping("/entities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntity(@PathVariable String id) {
        edit.deleteResource(archiveService.decodeId(id));
    }

    @PostMapping("/entities/{id}/types")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addType(@PathVariable String id, @RequestBody AddTypeRequest request) {
        String resolved = resolve(request.typeIri(), request.customType());
        if (resolved != null) {
            edit.addType(archiveService.decodeId(id), resolved);
        }
    }

    @DeleteMapping("/entities/{id}/types")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeType(@PathVariable String id, @RequestBody RemoveTypeRequest request) {
        edit.removeType(archiveService.decodeId(id), request.typeIri());
    }

    @PostMapping("/entities/{id}/properties")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addProperty(@PathVariable String id, @RequestBody AddPropertyRequest request) {
        String resolved = resolve(request.property(), request.customProperty());
        if (resolved != null) {
            edit.addLiteral(archiveService.decodeId(id), resolved, request.value());
        }
    }

    @DeleteMapping("/entities/{id}/properties")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProperty(@PathVariable String id, @RequestBody RemovePropertyRequest request) {
        edit.removeLiteral(archiveService.decodeId(id), request.propertyIri(), request.value());
    }

    @PostMapping("/entities/{id}/relationships")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRelationship(@PathVariable String id, @RequestBody AddRelationshipRequest request) {
        String resolvedProperty = resolve(request.property(), request.customProperty());
        String resolvedTarget = blankToNull(request.customTarget()) != null
                ? ontology.resolveIri(request.customTarget())
                : (blankToNull(request.targetId()) != null ? archiveService.decodeId(request.targetId()) : null);
        if (resolvedProperty != null && resolvedTarget != null) {
            edit.addRelationship(archiveService.decodeId(id), resolvedProperty, resolvedTarget);
        }
    }

    @DeleteMapping("/entities/{id}/relationships")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRelationship(@PathVariable String id, @RequestBody RemoveRelationshipRequest request) {
        String iri = archiveService.decodeId(id);
        if ("incoming".equals(request.direction())) {
            edit.removeRelationship(request.otherIri(), request.propertyIri(), iri);
        } else {
            edit.removeRelationship(iri, request.propertyIri(), request.otherIri());
        }
    }

    /** {@code custom} (a free-typed IRI/prefixed name, resolved via the ontology) wins over {@code picked} (an option's raw IRI) if both are set -- same convention EntityController's forms use. */
    private String resolve(String picked, String custom) {
        if (blankToNull(custom) != null) {
            return ontology.resolveIri(custom);
        }
        return blankToNull(picked);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
