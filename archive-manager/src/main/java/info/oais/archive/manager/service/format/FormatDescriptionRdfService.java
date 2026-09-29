package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.Hdf5Node;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.service.EditService;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a {@link FormatDefinition}, plus whichever generated Kaitai/DFDL/DRB
 * text the user chose to keep, into the archive as real OAIS Representation
 * Information -- via {@link EditService}'s existing primitives only, so this
 * stays consistent with every other write path in the app.
 *
 * <p><strong>Groups.</strong> Saved as groups of Representation Information
 * ({@code im:RepInfoAndGroup}, {@code im:RepInfoOrGroup}, local extensions in
 * {@code oais-im-local-extensions.ttl}): the Data Object is
 * {@code interpretedUsing} one AND group of its Structure and its Semantic
 * Representation Information. The structure is an OR group of the saved
 * descriptions -- DFDL, Kaitai Struct, DRB are equivalent alternatives, any
 * one of which is enough -- and each alternative is itself an AND group of
 * the description and the software that applies it (a shared
 * {@code im:OtherRepresentationInformation} per kind of description, see
 * {@link Processor}). One overall {@code im:SemanticRepresentationInformation}
 * serves them all, since field meanings don't depend on which tool reads the
 * bytes. Alongside the groups, the AND group also has the usual
 * {@code hasStructureRepresentationInformation} (the OR group, itself typed
 * as Structure Representation Information) and
 * {@code hasSemanticRepresentationInformation}, one each as the schema allows.
 * Descriptions saved before groups existed -- one RepresentationInformation
 * per format, sharing a Semantic Representation Information -- are converted
 * by {@link RepInfoGroupMigration}.
 *
 * <p><strong>Per-element structure.</strong> Underneath that one overall
 * Semantic Representation Information, every element of a byte-layout
 * description -- field, record, choice and choice branch -- gets its own
 * {@code im:SemanticRepresentationInformation} individual, nested to mirror
 * the description: each is linked from its parent's (or, at the top, the
 * overall one) via {@code im:interpretedUsingRecurse}, the OAIS Information
 * Model's own property for one Representation Information needing further
 * Representation Information to interpret it (figure 4-10), unrestricted in
 * cardinality unlike hasSemanticRepresentationInformation. Each carries
 * {@code rdfs:label} (the semantic name, falling back to the element's name),
 * {@code bridge:structuralPath} (where it sits, e.g. {@code packet.body.temp}),
 * {@code skos:definition}, and {@code rico:hasUnitOfMeasurement} to a shared
 * {@code rico:UnitOfMeasurement} individual (one per distinct unit string, with
 * {@code skos:exactMatch} to a vocabulary term when one was given). The rest of
 * its semantics use the bridge ontology's data-element properties
 * ({@code oais-ric-bridge.ttl}): {@code bridge:scaleFactor}/{@code addOffset},
 * {@code bridge:fillValue}, {@code bridge:validMin}/{@code validMax},
 * {@code bridge:representsConcept}, and {@code bridge:hasCodeList} to a
 * {@code skos:ConceptScheme} whose concepts pair each code ({@code skos:notation})
 * with its meaning ({@code skos:prefLabel}). A logical-tree definition gets one
 * individual per row.
 */
@Service
public class FormatDescriptionRdfService {

    private final EditService edit;

    public FormatDescriptionRdfService(EditService edit) {
        this.edit = edit;
    }

    /**
     * @param dataObjectIri the DataObject this description interprets; a new
     *                      {@code im:DigitalObject} is created if blank.
     * @param generatedByFormat e.g. {@code {"kaitai": "<.ksy text>", "dfdl": "<.dfdl.xsd text>"}} --
     *                          only the formats the user chose to persist.
     * @return the DataObject IRI (existing or newly created), to redirect to afterward.
     */
    public String saveToArchive(FormatDefinition def, String dataObjectIri, Map<String, String> generatedByFormat) {
        String dataObject = (dataObjectIri == null || dataObjectIri.isBlank())
                ? edit.createEntity(Ns.IM + "DigitalObject")
                : dataObjectIri;

        String semanticRi = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        edit.addLiteral(semanticRi, Ns.RDFS + "label", "Semantics of " + def.getName());
        edit.addLiteral(semanticRi, Ns.RDFS + "comment", semanticSummary(def));
        addFieldSemantics(def, semanticRi);

        Map<String, String> structures = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : generatedByFormat.entrySet()) {
            String formatLabel = entry.getKey();
            String generatedText = entry.getValue();
            if (generatedText == null || generatedText.isBlank()) {
                continue;
            }
            String structureRi = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
            edit.addLiteral(structureRi, Ns.RDFS + "label", def.getName() + ": " + formatLabel + " description");
            edit.addLiteral(structureRi, Ns.RDFS + "comment",
                    "Byte/logical layout of \"" + def.getName() + "\" as a " + formatLabel + " description:\n\n" + generatedText);
            structures.put(formatLabel, structureRi);
        }
        linkAsGroups(dataObject, def.getName(), semanticRi, structures, def.getKind() == FormatDefinitionKind.BYTE_LAYOUT);
        return dataObject;
    }

    /**
     * Links {@code dataObject} to its Representation Information as groups
     * (see this class's comment): an AND group of the Semantic Representation
     * Information and an OR group of the structure descriptions, each
     * alternative an AND group with the software that applies it.
     *
     * @param structures  structure description individuals by their format label (e.g. "DFDL")
     * @param applyWithSoftware whether the descriptions are applied by software (a byte layout's are; a
     *                          logical tree's generated DRB outputs are documentation)
     * @return the AND group
     */
    String linkAsGroups(String dataObject, String name, String semanticRi, Map<String, String> structures,
                        boolean applyWithSoftware) {
        String top = edit.createEntity(Ns.IM + "RepInfoAndGroup");
        edit.addType(top, Ns.IM + "RepresentationInformation");
        edit.addLiteral(top, Ns.RDFS + "label", "Representation Information for " + name);
        edit.addLiteral(top, Ns.RDFS + "comment", "Everything needed to interpret \"" + name + "\", used together: "
                + (structures.isEmpty() ? "" : "its structure (any one of the descriptions) and ") + "its semantics.");
        edit.addRelationship(dataObject, Ns.IM + "interpretedUsing", top);
        edit.addRelationship(top, Ns.IM + "hasGroupMember", semanticRi);
        edit.addRelationship(top, Ns.IM + "hasSemanticRepresentationInformation", semanticRi);
        if (structures.isEmpty()) {
            return top;
        }
        String alternatives = edit.createEntity(Ns.IM + "RepInfoOrGroup");
        edit.addType(alternatives, Ns.IM + "StructureRepresentationInformation");
        edit.addLiteral(alternatives, Ns.RDFS + "label", "Structure of " + name + ": any one of "
                + String.join(", ", structures.keySet()));
        edit.addLiteral(alternatives, Ns.RDFS + "comment", "Equivalent descriptions of the structure of \"" + name
                + "\", generated from the same engine-neutral description by archive-manager's RepInfo Tools"
                + " (or written by hand where the label says so); any one of them is enough.");
        edit.addRelationship(top, Ns.IM + "hasGroupMember", alternatives);
        edit.addRelationship(top, Ns.IM + "hasStructureRepresentationInformation", alternatives);
        for (Map.Entry<String, String> structure : structures.entrySet()) {
            Processor processor = applyWithSoftware ? Processor.forLabel(structure.getKey()) : null;
            String member = structure.getValue();
            if (processor != null) {
                member = edit.createEntity(Ns.IM + "RepInfoAndGroup");
                edit.addType(member, Ns.IM + "RepresentationInformation");
                edit.addLiteral(member, Ns.RDFS + "label", name + ": " + structure.getKey() + " description, applied with "
                        + processor.label);
                edit.addRelationship(member, Ns.IM + "hasGroupMember", structure.getValue());
                edit.addRelationship(member, Ns.IM + "hasGroupMember", software(processor));
                edit.addRelationship(member, Ns.IM + "hasStructureRepresentationInformation", structure.getValue());
                edit.addRelationship(member, Ns.IM + "hasOtherRepresentationInformation", software(processor));
            }
            edit.addRelationship(alternatives, Ns.IM + "hasGroupMember", member);
        }
        return top;
    }

    /** The software that applies each kind of description, as shared Other Representation Information. */
    enum Processor {
        DFDL("Apache Daffodil 3.11", "https://daffodil.apache.org/",
                "Apache Daffodil, the reference implementation of the Open Grid Forum's Data Format Description "
                        + "Language (DFDL 1.0): compiles a DFDL schema and uses it to parse data into an infoset (XML or "
                        + "JSON) and to write it back. The descriptions were tested with version 3.11."),
        KAITAI("Kaitai Struct compiler 0.11", "https://kaitai.io/",
                "The Kaitai Struct compiler, which turns a .ksy description into a parser in one of several "
                        + "programming languages (Java, Python, C++, ...), together with that language's Kaitai "
                        + "Struct runtime. The descriptions were tested with version 0.11 and its Java runtime."),
        DRB_JAVA("GAEL DRB 2.5.13 (Java)", "https://www.gael-systems.com/",
                "GAEL Systems' Data Request Broker for Java (fr.gael.drb), which applies a DRB SDF schema to data "
                        + "and presents it as a tree of nodes. The descriptions were tested with version 2.5.13."),
        DRB_PYTHON("drb-python 2", "https://gitlab.com/drb-python",
                "Python 3 with drb-python 2 (pip install drb), into which the generated driver package is "
                        + "installed; drb-python then decodes files of the format with it.");

        final String label;
        final String url;
        final String description;

        Processor(String label, String url, String description) {
            this.label = label;
            this.url = url;
            this.description = description;
        }

        /** The software for a saved description's format label, e.g. "DFDL, written by hand"; null if none. */
        static Processor forLabel(String formatLabel) {
            if (formatLabel.startsWith("Kaitai Struct")) {
                return KAITAI;
            }
            if (formatLabel.startsWith("DFDL")) {
                return DFDL;
            }
            if (formatLabel.startsWith("DRB SDF schema")) {
                return DRB_JAVA;
            }
            if (formatLabel.startsWith("DRB (Python")) {
                return DRB_PYTHON;
            }
            return null;
        }

        String iri() {
            return Ns.EX + "software-" + name().toLowerCase().replace('_', '-');
        }
    }

    /** The shared Other Representation Information for {@code processor}, created the first time it's needed. */
    private String software(Processor processor) {
        String iri = processor.iri();
        if (edit.createEntityIfAbsent(iri, Ns.IM + "OtherRepresentationInformation")) {
            edit.addLiteral(iri, Ns.RDFS + "label", processor.label);
            edit.addLiteral(iri, Ns.RDFS + "comment", processor.description);
            edit.addRelationship(iri, Ns.RDFS + "seeAlso", processor.url);
        }
        return iri;
    }

    /**
     * Creates the per-element Semantic Representation Information under the
     * overall {@code semanticRi}: for a byte layout, one per element of the
     * description, nested to mirror it (a record's or choice's individual links
     * to its elements' via {@code im:interpretedUsingRecurse}); for a logical
     * tree, one per row. Units are shared {@code rico:UnitOfMeasurement}
     * individuals, one per distinct unit string.
     */
    private void addFieldSemantics(FormatDefinition def, String semanticRi) {
        Map<String, String> unitsByLabel = new LinkedHashMap<>();
        if (def.getKind() == FormatDefinitionKind.BYTE_LAYOUT) {
            for (ElementDescription child : def.getRoot().children()) {
                addElementSemantics(child, "", semanticRi, unitsByLabel);
            }
        } else {
            for (Hdf5Node node : def.getNodes()) {
                String label = node.semanticName() == null || node.semanticName().isBlank()
                        ? node.name() : node.semanticName();
                String rowRi = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
                edit.addLiteral(rowRi, Ns.RDFS + "label", label);
                edit.addLiteral(rowRi, Ns.SKOS + "definition", node.definition());
                addUnits(rowRi, node.units(), null, unitsByLabel);
                edit.addRelationship(semanticRi, Ns.IM + "interpretedUsingRecurse", rowRi);
            }
        }
    }

    private void addElementSemantics(ElementDescription e, String parentPath, String parentRi,
                                     Map<String, String> unitsByLabel) {
        String path = parentPath.isEmpty() ? e.name() : parentPath + "." + e.name();
        Semantics s = e.semantics();
        String ri = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        edit.addLiteral(ri, Ns.RDFS + "label", s.semanticName() == null ? e.name() : s.semanticName());
        edit.addLiteral(ri, Ns.BRIDGE + "structuralPath", path);
        edit.addLiteral(ri, Ns.SKOS + "definition", s.definition());
        addUnits(ri, s.units(), s.unitsUri(), unitsByLabel);
        if (s.conceptUri() != null) {
            edit.addRelationship(ri, Ns.BRIDGE + "representsConcept", s.conceptUri().toString());
        }
        if (s.scale() != null) {
            edit.addLiteral(ri, Ns.BRIDGE + "scaleFactor", s.scale().toPlainString());
        }
        if (s.offset() != null) {
            edit.addLiteral(ri, Ns.BRIDGE + "addOffset", s.offset().toPlainString());
        }
        edit.addLiteral(ri, Ns.BRIDGE + "fillValue", s.fillValue());
        if (s.validMin() != null) {
            edit.addLiteral(ri, Ns.BRIDGE + "validMin", s.validMin().toPlainString());
        }
        if (s.validMax() != null) {
            edit.addLiteral(ri, Ns.BRIDGE + "validMax", s.validMax().toPlainString());
        }
        if (!s.codes().isEmpty()) {
            String scheme = edit.createEntity(Ns.SKOS + "ConceptScheme");
            edit.addLiteral(scheme, Ns.RDFS + "label", "Codes for " + path);
            s.codes().forEach((code, meaning) -> {
                String concept = edit.createEntity(Ns.SKOS + "Concept");
                edit.addLiteral(concept, Ns.SKOS + "notation", code);
                edit.addLiteral(concept, Ns.SKOS + "prefLabel", meaning);
                edit.addRelationship(concept, Ns.SKOS + "inScheme", scheme);
            });
            edit.addRelationship(ri, Ns.BRIDGE + "hasCodeList", scheme);
        }
        edit.addRelationship(parentRi, Ns.IM + "interpretedUsingRecurse", ri);

        if (e instanceof RecordDescription r) {
            for (ElementDescription child : r.children()) {
                addElementSemantics(child, path, ri, unitsByLabel);
            }
        } else if (e instanceof ChoiceDescription c) {
            for (ChoiceDescription.Branch b : c.branches()) {
                addElementSemantics(b.record(), path, ri, unitsByLabel);
            }
        }
    }

    private void addUnits(String ri, String units, java.net.URI unitsUri, Map<String, String> unitsByLabel) {
        if (units == null || units.isBlank()) {
            return;
        }
        String unitIri = unitsByLabel.computeIfAbsent(units, u -> {
            String iri = edit.createEntity(Ns.RICO + "UnitOfMeasurement");
            edit.addLiteral(iri, Ns.RDFS + "label", u);
            return iri;
        });
        if (unitsUri != null) {
            edit.addRelationship(unitIri, Ns.SKOS + "exactMatch", unitsUri.toString());
        }
        edit.addRelationship(ri, Ns.RICO + "hasUnitOfMeasurement", unitIri);
    }

    private String semanticSummary(FormatDefinition def) {
        StringBuilder sb = new StringBuilder();
        sb.append("Semantics of \"").append(def.getName()).append("\":\n");
        if (!def.getNotes().isBlank()) {
            sb.append('\n').append(def.getNotes()).append('\n');
        }
        sb.append('\n');
        if (def.getKind() == FormatDefinitionKind.BYTE_LAYOUT) {
            for (ElementDescription child : def.getRoot().children()) {
                summarise(child, "", sb);
            }
        } else {
            for (Hdf5Node node : def.getNodes()) {
                sb.append("- ").append(node.path()).append(" (").append(node.kind().name().toLowerCase()).append("): ")
                        .append(node.definition() == null || node.definition().isBlank() ? "(no definition)" : node.definition());
                if (node.units() != null && !node.units().isBlank()) {
                    sb.append(" [").append(node.units()).append(']');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static void summarise(ElementDescription e, String parentPath, StringBuilder sb) {
        String path = parentPath.isEmpty() ? e.name() : parentPath + "." + e.name();
        String meaning = KaitaiGenerator.describe(e.semantics());
        sb.append("- ").append(path).append(": ").append(meaning.isEmpty() ? "(no definition)" : meaning).append('\n');
        if (e instanceof RecordDescription r) {
            r.children().forEach(child -> summarise(child, path, sb));
        } else if (e instanceof ChoiceDescription c) {
            c.branches().forEach(b -> summarise(b.record(), path, sb));
        }
    }
}
