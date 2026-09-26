package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.model.format.Hdf5Node;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.service.EditService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a {@link FormatDefinition}, plus whichever generated Kaitai/DFDL/DRB
 * text the user chose to keep, into the archive as real OAIS Representation
 * Information -- via {@link EditService}'s existing primitives only, so this
 * stays consistent with every other write path in the app.
 *
 * <p>Models one overall {@code im:SemanticRepresentationInformation} per save
 * (field meanings don't change depending on which tool reads the bytes) plus
 * one {@code im:RepresentationInformation}/{@code im:StructureRepresentationInformation}
 * pair per generated format actually saved -- {@code im:RepresentationInformation}
 * caps both {@code hasStructureRepresentationInformation} and
 * {@code hasSemanticRepresentationInformation} at one each (see
 * {@code oais_im_schema-sh-v5.ttl}), so two formats for the same data means
 * two RepresentationInformation individuals, both {@code interpretedUsing}
 * from the same DataObject.
 *
 * <p><strong>Per-field structure.</strong> Underneath that one overall
 * Semantic Representation Information, every field (byte-layout) or tree row
 * (logical-tree) that carries a semantic name, definition, or units gets its
 * own {@code im:SemanticRepresentationInformation} individual -- {@code
 * rdfs:label} for the semantic name (falling back to the field's structural
 * name/path if none was given), {@code skos:definition} for the definition,
 * and {@code rico:hasUnitOfMeasurement} to a shared {@code
 * rico:UnitOfMeasurement} individual (one per distinct unit string in this
 * save, so two fields both in "K" point at the same one) for the units. Each
 * is linked from the overall Semantic Representation Information via {@code
 * im:interpretedUsingRecurse} -- the OAIS Information Model's own property
 * for one Representation Information needing further Representation
 * Information to interpret it (figure 4-10), reused here rather than
 * inventing a new one, and unrestricted in cardinality unlike
 * hasSemanticRepresentationInformation.
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
        edit.addLiteral(semanticRi, Ns.RDFS + "comment", semanticSummary(def));
        addFieldSemantics(def, semanticRi);

        for (Map.Entry<String, String> entry : generatedByFormat.entrySet()) {
            String formatLabel = entry.getKey();
            String generatedText = entry.getValue();
            if (generatedText == null || generatedText.isBlank()) {
                continue;
            }

            String structureRi = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
            edit.addLiteral(structureRi, Ns.RDFS + "comment",
                    "Byte/logical layout of \"" + def.getName() + "\" as a " + formatLabel + " description:\n\n" + generatedText);

            String repInfo = edit.createEntity(Ns.IM + "RepresentationInformation");
            edit.addLiteral(repInfo, Ns.RDFS + "comment",
                    "\"" + def.getName() + "\" interpreted via its " + formatLabel + " description.");
            edit.addRelationship(repInfo, Ns.IM + "hasStructureRepresentationInformation", structureRi);
            edit.addRelationship(repInfo, Ns.IM + "hasSemanticRepresentationInformation", semanticRi);
            edit.addRelationship(dataObject, Ns.IM + "interpretedUsing", repInfo);
        }

        return dataObject;
    }

    /**
     * Creates one {@code im:SemanticRepresentationInformation} per field/row,
     * linked from the overall {@code semanticRi} via {@code
     * interpretedUsingRecurse}, and one shared {@code rico:UnitOfMeasurement}
     * per distinct unit string used along the way.
     */
    private void addFieldSemantics(FormatDefinition def, String semanticRi) {
        Map<String, String> unitsByLabel = new LinkedHashMap<>();
        if (def.getKind() == FormatDefinitionKind.BYTE_LAYOUT) {
            for (FormatField field : def.getFields()) {
                String label = field.semanticName() == null || field.semanticName().isBlank()
                        ? field.name() : field.semanticName();
                addFieldSemantic(semanticRi, label, field.definition(), field.units(), unitsByLabel);
            }
        } else {
            for (Hdf5Node node : def.getNodes()) {
                String label = node.semanticName() == null || node.semanticName().isBlank()
                        ? node.name() : node.semanticName();
                addFieldSemantic(semanticRi, label, node.definition(), node.units(), unitsByLabel);
            }
        }
    }

    private void addFieldSemantic(String semanticRi, String label, String definition, String units,
                                   Map<String, String> unitsByLabel) {
        String fieldRi = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        edit.addLiteral(fieldRi, Ns.RDFS + "label", label);
        edit.addLiteral(fieldRi, Ns.SKOS + "definition", definition);
        if (units != null && !units.isBlank()) {
            String unitIri = unitsByLabel.computeIfAbsent(units, u -> {
                String iri = edit.createEntity(Ns.RICO + "UnitOfMeasurement");
                edit.addLiteral(iri, Ns.RDFS + "label", u);
                return iri;
            });
            edit.addRelationship(fieldRi, Ns.RICO + "hasUnitOfMeasurement", unitIri);
        }
        edit.addRelationship(semanticRi, Ns.IM + "interpretedUsingRecurse", fieldRi);
    }

    private String semanticSummary(FormatDefinition def) {
        StringBuilder sb = new StringBuilder();
        sb.append("Field semantics for \"").append(def.getName()).append("\":\n");
        if (!def.getNotes().isBlank()) {
            sb.append('\n').append(def.getNotes()).append('\n');
        }
        sb.append('\n');
        if (def.getKind() == FormatDefinitionKind.BYTE_LAYOUT) {
            for (FormatField field : def.getFields()) {
                sb.append("- ").append(field.name()).append(": ")
                        .append(field.definition() == null || field.definition().isBlank() ? "(no definition)" : field.definition());
                if (field.units() != null && !field.units().isBlank()) {
                    sb.append(" [").append(field.units()).append(']');
                }
                sb.append('\n');
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
}
