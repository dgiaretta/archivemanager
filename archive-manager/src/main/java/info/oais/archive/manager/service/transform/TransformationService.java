package info.oais.archive.manager.service.transform;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.BitStore;
import info.oais.archive.manager.service.EditService;
import info.oais.archive.manager.service.format.DataObjectViewService;
import info.oais.archive.manager.service.transform.PropertyCheck.Meaning;
import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline.SchemaElement;
import info.oais.infomodel.structure.dfdl.DfdlStructureRepInfo;
import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.StructureDescription;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Transformation, the second of OAIS's ways of preserving digital
 * information: a Data Object rewritten in another format, by decoding it
 * with its own Representation Information, making each element of the new
 * format by a {@link TransformationMapping}, and encoding the result with the
 * new format's DFDL description. The new data is decoded again with that
 * description to check each Transformation Information Property, and the
 * Transformation is recorded: the new Data Object (its bits in the archive's
 * {@link BitStore}, interpreted using the new format's Representation
 * Information) in new Content Information and an AIP Version, whose
 * Preservation Description Information holds Fixity Information and
 * Provenance Information recording the Transformation -- what it started
 * from, the mapping it followed, and its checks. The old Data Object is left
 * as it was.
 */
@Service
public class TransformationService {

    /** The language a {@link TransformationMapping} is written in, as {@code im:specificationLanguage}. */
    public static final String MAPPING_LANGUAGE = "OAIS Transformation Mapping";

    private static final List<String> FOLLOWED = List.of("interpretedUsing", "hasGroupMember",
            "hasStructureRepresentationInformation", "hasSemanticRepresentationInformation",
            "hasOtherRepresentationInformation", "interpretedUsingRecurse");

    private final RdfStore store;
    private final QueryRunner q;
    private final EditService edit;
    private final ArchiveService archive;
    private final DataObjectViewService views;
    private final BitStore bits;

    public TransformationService(RdfStore store, QueryRunner q, EditService edit, ArchiveService archive,
                                 DataObjectViewService views, BitStore bits) {
        this.store = store;
        this.q = q;
        this.edit = edit;
        this.archive = archive;
        this.views = views;
        this.bits = bits;
    }

    /**
     * A format a Data Object can be transformed into: Representation
     * Information some Data Object in the archive is interpreted using,
     * with a DFDL description to write it with.
     *
     * @param iri       the Representation Information the new Data Object will be interpreted using
     * @param dfdl      its DFDL Structure Representation Information
     * @param label     its name
     * @param extension the file name extension its data has, e.g. {@code .bin}
     */
    public record Target(String iri, String dfdl, String label, String extension) {
    }

    /** Every format a Data Object can be transformed into, by name. */
    public List<Target> targets() {
        List<Map<String, String>> rows = q.select(store.dataModel(), Ns.PREFIXES + """
                SELECT ?top ?spec ?do ?storage WHERE {
                  ?do im:interpretedUsing ?top .
                  ?top (im:hasGroupMember|im:hasStructureRepresentationInformation)* ?spec .
                  ?spec im:specificationLanguage "DFDL" ; im:specificationText ?text .
                  OPTIONAL { ?do im:hasStorageLocation ?storage }
                } ORDER BY ?top ?spec
                """);
        Map<String, Target> found = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String top = row.get("top");
            Target existing = found.get(top);
            String extension = extension(row.get("storage"));
            if (existing == null) {
                found.put(top, new Target(top, row.get("spec"), formatName(top, row.get("spec"), row.get("do")),
                        extension));
            } else if (existing.extension() == null && extension != null) {
                found.put(top, new Target(top, existing.dfdl(), existing.label(), extension));
            }
        }
        return found.values().stream().sorted(java.util.Comparator.comparing(t -> t.label().toLowerCase())).toList();
    }

    public Optional<Target> target(String iri) {
        return targets().stream().filter(t -> t.iri().equals(iri)).findFirst();
    }

    private String formatName(String top, String spec, String dataObject) {
        String label = archive.label(top);
        if (label == null || label.isBlank() || label.equals(q.localName(top))) {
            label = archive.label(spec);
        }
        return label + " (as used by " + archive.label(dataObject) + ")";
    }

    private static String extension(String location) {
        if (location == null) {
            return null;
        }
        String path = URI.create(location).getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot) : null;
    }

    /** The element tree of {@code target}'s format, from its DFDL description. */
    public SchemaElement outline(Target target) {
        return DfdlSchemaOutline.read(specificationText(target.dfdl()));
    }

    /**
     * The element tree of {@code dataObject}'s format, from its DFDL or DRB
     * SDF description, if it has one this can read.
     */
    public Optional<SchemaElement> sourceOutline(String dataObject) {
        Optional<DescribedData> described = views.describe(dataObject, URI::create);
        if (described.isEmpty()) {
            return Optional.empty();
        }
        for (String language : List.of(StructureDescription.DFDL, StructureDescription.DRB_SDF)) {
            for (StructureDescription s : described.get().structures()) {
                if (s.language().equals(language)) {
                    try {
                        return Optional.of(DfdlSchemaOutline.read(specificationText(s.iri())));
                    } catch (RuntimeException e) {
                        // not one this can read; try the next
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The element tree of {@code dataObject}'s format: from its description
     * if this can read it, else from its data, decoded -- each element as
     * its first occurrence holds it, repeating if it occurs more than once.
     */
    public SchemaElement sourceStructure(String dataObject) throws IOException {
        Optional<SchemaElement> outline = sourceOutline(dataObject);
        return outline.isPresent() ? outline.get() : views.decode(dataObject, (data, root) -> fromTree(root, 1));
    }

    static SchemaElement fromTree(StructureNode node, int occurrences) {
        Map<String, List<StructureNode>> byName = new LinkedHashMap<>();
        for (StructureNode child : node.getChildren()) {
            List<StructureNode> found = byName.computeIfAbsent(child.getName(), n -> new ArrayList<>());
            if (child.getKind() == StructureNodeKind.ARRAY) {
                found.addAll(child.getChildren());
                found.add(null); // an array may hold one, but repeats
            } else {
                found.add(child);
            }
        }
        List<SchemaElement> children = new ArrayList<>();
        byName.forEach((name, found) -> {
            List<StructureNode> present = found.stream().filter(java.util.Objects::nonNull).toList();
            int count = found.size();
            if (present.isEmpty()) {
                children.add(new SchemaElement(name, null, 0, DfdlSchemaOutline.UNBOUNDED, null, false, 0, null,
                        List.of()));
            } else if (present.get(0).getKind() == StructureNodeKind.LEAF) {
                String type = StructurePaths.floatingType(present.get(0));
                Object value = present.get(0).getValue().orElse(null);
                if (type == null) {
                    type = value instanceof Number ? "long" : "string";
                }
                children.add(new SchemaElement(name, null, 1, count > 1 ? DfdlSchemaOutline.UNBOUNDED : 1, type,
                        false, 0, null, List.of()));
            } else {
                SchemaElement inner = fromTree(present.get(0), count);
                children.add(new SchemaElement(name, null, 1, count > 1 ? DfdlSchemaOutline.UNBOUNDED : 1, null,
                        false, 0, null, inner.children()));
            }
        });
        return new SchemaElement(node.getName(), null, 1, occurrences > 1 ? DfdlSchemaOutline.UNBOUNDED : 1, null,
                false, 0, null, children);
    }

    /** Whether {@code dataObject} can be transformed: its bits have a storage location, and it can be decoded. */
    public boolean transformable(String dataObject) {
        return views.describe(dataObject, URI::create).map(d -> d.structures().stream().anyMatch(s ->
                s.language().equals(StructureDescription.DFDL) || s.language().equals(StructureDescription.DRB_SDF)
                        || s.language().equals(StructureDescription.KAITAI))).orElse(false);
    }

    private String specificationText(String iri) {
        return views.specification(iri).map(DataObjectViewService.Specification::text).orElseThrow(() ->
                new TransformationException(iri + " has no description text"));
    }

    /**
     * What each element means, by its path: the Semantic Representation
     * Information with an {@code im:structuralPath} reachable from
     * {@code start} (a Data Object, or Representation Information).
     */
    public Map<String, Meaning> meanings(String start) {
        Model m = store.dataModel();
        Map<String, Meaning> meanings = new LinkedHashMap<>();
        for (Resource r : reachable(m, m.getResource(start))) {
            String path = literal(r, m.createProperty(Ns.IM + "structuralPath"));
            if (path == null || meanings.containsKey(path)) {
                continue;
            }
            Statement unit = r.getProperty(m.createProperty(Ns.IM + "hasUnitOfMeasurement"));
            meanings.put(path, new Meaning(r.isURIResource() ? r.getURI() : null,
                    literal(r, m.createProperty(Ns.RDFS + "label")),
                    decimal(literal(r, m.createProperty(Ns.IM + "scaleFactor"))),
                    decimal(literal(r, m.createProperty(Ns.IM + "addOffset"))),
                    unit == null || !unit.getObject().isResource() ? null
                            : literal(unit.getObject().asResource(), m.createProperty(Ns.RDFS + "label"))));
        }
        return meanings;
    }

    private static Set<Resource> reachable(Model m, Resource start) {
        Set<Resource> reached = new LinkedHashSet<>();
        Deque<Resource> todo = new ArrayDeque<>(List.of(start));
        while (!todo.isEmpty()) {
            Resource r = todo.pop();
            if (!reached.add(r)) {
                continue;
            }
            for (String p : FOLLOWED) {
                for (Statement s : r.listProperties(m.createProperty(Ns.IM + p)).toList()) {
                    if (s.getObject().isResource()) {
                        todo.add(s.getObject().asResource());
                    }
                }
            }
        }
        return reached;
    }

    /**
     * A Transformation Information Property of a Data Object's Information
     * Object (its Content Information, or an AIP holding that): what it is,
     * and the element of the format whose values it is -- known from the
     * Semantic Representation Information it depends on, or from where an
     * earlier Transformation's check of it found its values.
     */
    public record InformationProperty(String iri, String label, String path) {
    }

    /** {@code dataObject}'s Transformation Information Properties that are the values of an element of its format. */
    public List<InformationProperty> informationProperties(String dataObject) {
        List<Map<String, String>> rows = q.select(store.dataModel(), Ns.PREFIXES + """
                SELECT DISTINCT ?tip ?label ?path WHERE {
                  ?ci im:hasDataObject <%s> .
                  { ?ci im:hasTransformationInformationProperty ?tip }
                  UNION { ?aip im:hasContentInformation ?ci ; im:hasTransformationInformationProperty ?tip }
                  { ?tip im:dependsOnRepresentationInformation ?ri . ?ri im:structuralPath ?path }
                  UNION { ?check im:checksProperty ?tip ; im:sourcePath ?path }
                  OPTIONAL { ?tip rdfs:label ?label }
                } ORDER BY ?path
                """.formatted(dataObject));
        return rows.stream().map(r -> new InformationProperty(r.get("tip"), r.get("label"), r.get("path"))).toList();
    }

    /**
     * What a Transformation would make, before anything is saved.
     *
     * @param written     the new data
     * @param checks      the checks of the Transformation Information Properties asked for
     * @param reversible  whether it is shown to be reversible: every value of the old data is in the new,
     *                    unchanged
     * @param lost        why not, if it isn't: e.g. which values weren't carried over, or were changed
     * @param sourceLabel the old Data Object's name
     */
    public record Trial(byte[] written, List<PropertyCheck> checks, boolean reversible, List<String> lost,
                       String sourceLabel) {

        public String sha256() {
            return BitStore.sha256(written);
        }
    }

    /**
     * Transforms {@code dataObject} into {@code target}'s format by
     * {@code mapping}, and checks it, without saving anything.
     *
     * @param properties the paths of the elements whose values are Transformation Information Properties to
     *                   check, each with its tolerance (null for what the types carry)
     * @throws TransformationException saying why it can't be done
     */
    public Trial run(String dataObject, Target target, TransformationMapping mapping,
                     Map<String, BigDecimal> properties) throws IOException {
        SchemaElement targetOutline = outline(target);
        Optional<SchemaElement> sourceOutline = sourceOutline(dataObject);
        Map<String, Meaning> sourceMeanings = meanings(dataObject);
        Map<String, Meaning> targetMeanings = meanings(target.iri());
        Path schema = Files.createTempFile("transform-target-", ".dfdl.xsd");
        try {
            Files.writeString(schema, specificationText(target.dfdl()), StandardCharsets.UTF_8);
            DfdlStructureRepInfo writer = new DfdlStructureRepInfo(new DfdlFormatSpecification(schema.toUri()));
            return views.decode(dataObject, (data, source) -> {
                InfosetBuilder.Result built = InfosetBuilder.build(targetOutline, source, mapping);
                byte[] written;
                StructureNode reread;
                try {
                    written = writer.encode(built.infoset());
                    reread = writer.apply(new DigitalObjectRefImpl(new ByteArrayInputStream(written)));
                } catch (StructureInterpretationException e) {
                    throw new TransformationException("The new format's DFDL description couldn't write or read "
                            + "back the new data: " + e.getMessage(), e);
                }
                List<PropertyCheck> checks = new ArrayList<>();
                properties.forEach((path, tolerance) -> checks.add(PropertyCheck.check(path, tolerance, mapping,
                        source, reread, targetOutline, sourceMeanings, targetMeanings)));
                List<String> lost = new ArrayList<>(built.notes());
                for (String path : sourceOutline.map(TransformationService::valuePaths)
                        .orElseGet(() -> valuePaths(source))) {
                    Optional<TransformationMapping.Copy> copy = mapping.copyOf(path);
                    if (copy.isEmpty()) {
                        lost.add(path + ": not carried over");
                    } else if (copy.get().changesValue()) {
                        lost.add(path + ": scaled or offset");
                    } else {
                        PropertyCheck exact = PropertyCheck.check(path, BigDecimal.ZERO, mapping, source, reread,
                                targetOutline, Map.of(), Map.of());
                        if (exact.outcome() != PropertyCheck.Outcome.PRESERVED) {
                            lost.add(path + ": " + exact.detail());
                        }
                    }
                }
                return new Trial(written, checks, lost.isEmpty(), lost, data.name());
            });
        } finally {
            Files.deleteIfExists(schema);
        }
    }

    /** The paths of the value elements of a format, in order, leaving out those the format computes. */
    static List<String> valuePaths(SchemaElement root) {
        List<String> paths = new ArrayList<>();
        collect(root, "", paths);
        return paths;
    }

    private static void collect(SchemaElement e, String prefix, List<String> into) {
        for (SchemaElement child : e.children()) {
            String path = prefix.isEmpty() ? child.name() : prefix + "." + child.name();
            if (child.isValue()) {
                if (!child.computed()) {
                    into.add(path);
                }
            } else {
                collect(child, path, into);
            }
        }
    }

    /** The paths of the value elements in a decoded file, in order of first appearance. */
    static List<String> valuePaths(StructureNode root) {
        Set<String> paths = new LinkedHashSet<>();
        collect(root, "", paths);
        return List.copyOf(paths);
    }

    private static void collect(StructureNode node, String prefix, Set<String> into) {
        for (StructureNode child : node.getChildren()) {
            String path = node.getKind() == StructureNodeKind.ARRAY ? prefix
                    : prefix.isEmpty() ? child.getName() : prefix + "." + child.getName();
            if (child.getKind() == StructureNodeKind.LEAF) {
                into.add(path);
            } else {
                collect(child, path, into);
            }
        }
    }

    /**
     * Records a Transformation: stores the new data, and creates the new Data
     * Object, its Content Information and AIP Version, Fixity and Provenance
     * Information, the Transformation with its mapping and checks, and the
     * Transformation Information Properties checked, where they didn't exist.
     * Needs a write transaction.
     *
     * @param archiveAddress the archive's address, under which the new data is served
     * @return the Transformation's IRI
     */
    public String record(String dataObject, Target target, TransformationMapping mapping, Trial trial,
                         Map<String, BigDecimal> properties, String archiveAddress) throws IOException {
        String sourceLabel = archive.label(dataObject);
        String name = sourceLabel + " as " + target.label().replaceFirst(" \\(as used by .*\\)$", "");
        BitStore.StoredBits stored = bits.store(trial.written(),
                fileName(sourceLabel) + (target.extension() == null ? ".bin" : target.extension()));
        try {
            return recordEntities(dataObject, target, mapping, trial, properties, name,
                    archiveAddress.replaceAll("/+$", "") + stored.path(), stored);
        } catch (RuntimeException e) {
            bits.delete(stored);
            throw e;
        }
    }

    private String recordEntities(String dataObject, Target target, TransformationMapping mapping, Trial trial,
                                  Map<String, BigDecimal> properties, String name, String location,
                                  BitStore.StoredBits stored) {
        Model m = store.dataModel();
        String newObject = edit.createEntity(Ns.IM + "DigitalObject");
        edit.addLiteral(newObject, Ns.RDFS + "label", name);
        edit.addRelationship(newObject, Ns.IM + "hasStorageLocation", location);
        edit.addRelationship(newObject, Ns.IM + "interpretedUsing", target.iri());

        String content = edit.createEntity(Ns.IM + "ContentInformation");
        edit.addType(content, Ns.IM + "InformationObject");
        edit.addLiteral(content, Ns.RDFS + "label", name);
        edit.addRelationship(content, Ns.IM + "hasDataObject", newObject);

        String aip = edit.createEntity(Ns.IM + "ArchivalInformationPackage");
        edit.addType(aip, Ns.IM + "AIPVersion");
        edit.addLiteral(aip, Ns.RDFS + "label", "AIP: " + name);
        edit.addRelationship(aip, Ns.IM + "hasContentInformation", content);
        for (String sourceAip : column(Ns.PREFIXES + """
                SELECT DISTINCT ?aip WHERE { ?aip im:hasContentInformation ?ci . ?ci im:hasDataObject <%s> }
                """.formatted(dataObject), "aip")) {
            edit.addRelationship(aip, Ns.IM + "hasSourceAIP", sourceAip);
        }

        String pdi = edit.createEntity(Ns.IM + "PreservationDescriptionInformation");
        edit.addLiteral(pdi, Ns.RDFS + "label", "PDI: " + name);
        edit.addRelationship(aip, Ns.IM + "hasPreservationDescriptiveInformation", pdi);
        String fixity = edit.createEntity(Ns.IM + "FixityInformation");
        edit.addLiteral(fixity, Ns.RDFS + "label", "Fixity: " + name);
        edit.addLiteral(fixity, Ns.RDFS + "comment", "SHA-256: " + stored.sha256() + " (" + stored.size() + " bytes)");
        edit.addRelationship(pdi, Ns.IM + "hasFixityInformation", fixity);

        String mappingIri = edit.createEntity(Ns.IM + "TransformationMapping");
        edit.addLiteral(mappingIri, Ns.RDFS + "label", "Mapping to " + name);
        edit.addLiteral(mappingIri, Ns.IM + "specificationLanguage", MAPPING_LANGUAGE);
        edit.addLiteral(mappingIri, Ns.IM + "specificationText", mapping.text());

        String transformation = edit.createEntity(Ns.IM + "Transformation");
        if (!trial.reversible()) {
            edit.addType(transformation, Ns.IM + "NonReversibleTransformation");
        }
        edit.addLiteral(transformation, Ns.RDFS + "label", "Transformation of " + trial.sourceLabel() + " to "
                + name);
        edit.addRelationship(transformation, Ns.IM + "transformationSource", dataObject);
        edit.addRelationship(transformation, Ns.IM + "transformationResult", newObject);
        edit.addRelationship(transformation, Ns.IM + "followedMapping", mappingIri);
        m.getResource(transformation).addLiteral(m.createProperty(Ns.IM + "performedAt"), m.createTypedLiteral(
                OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString(), XSDDatatype.XSDdateTime));
        edit.addLiteral(transformation, Ns.RDFS + "comment", trial.reversible()
                ? "Shown reversible: every value of the old data is in the new data, unchanged."
                : "Not shown reversible: " + String.join("; ", trial.lost()) + ".");

        String provenance = edit.createEntity(Ns.IM + "ProvenanceInformation");
        edit.addLiteral(provenance, Ns.RDFS + "label", "Provenance: " + name);
        edit.addLiteral(provenance, Ns.RDFS + "comment", "Made from " + trial.sourceLabel() + " by "
                + (trial.reversible() ? "a" : "a non-reversible") + " Transformation, following the mapping "
                + "recorded with it; " + summary(trial.checks()));
        edit.addRelationship(provenance, Ns.IM + "recordsTransformation", transformation);
        edit.addRelationship(pdi, Ns.IM + "hasProvenanceInformation", provenance);

        Map<String, Meaning> sourceMeanings = meanings(dataObject);
        Map<String, Meaning> targetMeanings = meanings(target.iri());
        Map<String, String> existing = new LinkedHashMap<>();
        informationProperties(dataObject).forEach(p -> existing.putIfAbsent(p.path(), p.iri()));
        List<String> sourceContent = column(Ns.PREFIXES + """
                SELECT DISTINCT ?ci WHERE { ?ci im:hasDataObject <%s> }
                """.formatted(dataObject), "ci");
        String sourceRepInfo = firstObject(m, dataObject, Ns.IM + "interpretedUsing");
        for (PropertyCheck check : trial.checks()) {
            String property = existing.get(check.sourcePath());
            if (property == null) {
                property = informationProperty(check.label(), sourceMeanings.get(check.sourcePath()), sourceRepInfo);
                for (String ci : sourceContent) {
                    edit.addRelationship(ci, Ns.IM + "hasTransformationInformationProperty", property);
                }
            }
            if (check.targetPath() != null) {
                String carried = informationProperty(check.label(), targetMeanings.get(check.targetPath()),
                        target.iri());
                edit.addRelationship(content, Ns.IM + "hasTransformationInformationProperty", carried);
            }
            String checkIri = edit.createEntity(Ns.IM + "TransformationInformationPropertyCheck");
            edit.addLiteral(checkIri, Ns.RDFS + "label", check.label() + ": " + check.outcome().text());
            edit.addLiteral(checkIri, Ns.IM + "checkOutcome", check.outcome().text());
            edit.addLiteral(checkIri, Ns.RDFS + "comment", check.detail());
            edit.addLiteral(checkIri, Ns.IM + "sourcePath", check.sourcePath());
            if (check.targetPath() != null) {
                edit.addLiteral(checkIri, Ns.IM + "targetPath", check.targetPath());
            }
            if (properties.get(check.sourcePath()) != null) {
                edit.addLiteral(checkIri, Ns.RDFS + "comment",
                        "Tolerance: " + properties.get(check.sourcePath()).toPlainString());
            }
            edit.addRelationship(checkIri, Ns.IM + "checksProperty", property);
            edit.addRelationship(transformation, Ns.IM + "hasPropertyCheck", checkIri);
        }
        return transformation;
    }

    /** A new Transformation Information Property: the values of the element {@code meaning} is about. */
    private String informationProperty(String label, Meaning meaning, String fallbackRepInfo) {
        String property = edit.createEntity(Ns.IM + "TransformationInformationProperty");
        edit.addLiteral(property, Ns.RDFS + "label", "Values of " + label);
        String dependsOn = meaning != null && meaning.iri() != null ? meaning.iri() : fallbackRepInfo;
        if (dependsOn != null) {
            edit.addRelationship(property, Ns.IM + "dependsOnRepresentationInformation", dependsOn);
        }
        return property;
    }

    static String summary(List<PropertyCheck> checks) {
        if (checks.isEmpty()) {
            return "no Transformation Information Properties were checked.";
        }
        long preserved = checks.stream().filter(c -> c.outcome() == PropertyCheck.Outcome.PRESERVED).count();
        return preserved + " of " + checks.size() + " Transformation Information Properties checked were preserved.";
    }

    private List<String> column(String sparql, String variable) {
        return q.select(store.dataModel(), sparql).stream().map(r -> r.get(variable)).toList();
    }

    private static String firstObject(Model m, String subject, String property) {
        Statement s = m.getResource(subject).getProperty(m.createProperty(property));
        return s == null || !s.getObject().isURIResource() ? null : s.getObject().asResource().getURI();
    }

    static String fileName(String label) {
        String name = label == null ? "" : label.strip().replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^[-.]+|-+$", "");
        return name.isEmpty() ? "data" : name;
    }

    private static String literal(Resource r, Property p) {
        Statement s = r.getProperty(p);
        if (s == null) {
            return null;
        }
        RDFNode o = s.getObject();
        String text = o.isLiteral() ? o.asLiteral().getLexicalForm() : null;
        return text == null || text.isBlank() ? null : text;
    }

    private static BigDecimal decimal(String text) {
        try {
            return text == null ? null : new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
