package info.oais.archive.manager.service.format;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.model.format.Hdf5Node;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.service.EditService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Writes a {@link FormatDefinition}, plus whichever generated Kaitai/DFDL/DRB
 * text the user chose to keep, into the archive as real OAIS Representation
 * Information -- via {@link EditService}'s existing primitives only, so this
 * stays consistent with every other write path in the app.
 *
 * <p>Models one shared {@code im:SemanticRepresentationInformation} (the field
 * meanings don't change depending on which tool reads the bytes) plus one
 * {@code im:RepresentationInformation}/{@code im:StructureRepresentationInformation}
 * pair per generated format actually saved -- {@code im:RepresentationInformation}
 * caps both {@code hasStructureRepresentationInformation} and
 * {@code hasSemanticRepresentationInformation} at one each (see
 * {@code oais_im_schema-sh-v5.ttl}), so two formats for the same data means
 * two RepresentationInformation individuals, both {@code interpretedUsing}
 * from the same DataObject. This exactly mirrors how the bundled
 * {@code oais-structure-adapters-data.ttl} demo data models the same
 * DFDL-vs-Kaitai situation by hand (its {@code repinfo-point-dfdl} and
 * {@code repinfo-point-kaitai} likewise share one {@code seminfo-point-table}).
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
                        .append(field.description() == null || field.description().isBlank() ? "(no description)" : field.description());
                if (field.units() != null && !field.units().isBlank()) {
                    sb.append(" [").append(field.units()).append(']');
                }
                sb.append('\n');
            }
        } else {
            for (Hdf5Node node : def.getNodes()) {
                sb.append("- ").append(node.path()).append(" (").append(node.kind().name().toLowerCase()).append("): ")
                        .append(node.description() == null || node.description().isBlank() ? "(no description)" : node.description())
                        .append('\n');
            }
        }
        return sb.toString();
    }
}
