package info.oais.archive.manager;

import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.service.format.FormatIdentifiers;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.Descriptions;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.Semantics;

/** Building blocks for tests that construct byte-layout definitions field by field. */
final class TestFormats {

    private TestFormats() {
    }

    /** Appends a field to {@code def}'s root record, the way the flat editor does. */
    static void addField(FormatDefinition def, String name, PrimitiveType type, Integer lengthBytes, ByteOrder order,
                         String semanticName, String definition, String units) {
        FieldDescription field = new FieldDescription(ElementDescription.newId(), FormatIdentifiers.snakeCase(name), type,
                type.needsLength() ? new Expression.IntLiteral(lengthBytes == null ? 1 : lengthBytes) : null, order,
                Occurrence.ONCE, Semantics.of(semanticName, definition, units));
        def.editRoot(root -> Descriptions.addChild(root, root.id(), field));
    }

    /** Appends a field with full semantics (codes, scale, valid range, ...) to {@code def}'s root record. */
    static void addField(FormatDefinition def, String name, PrimitiveType type, Integer lengthBytes, Semantics semantics) {
        FieldDescription field = new FieldDescription(ElementDescription.newId(), FormatIdentifiers.snakeCase(name), type,
                type.needsLength() ? new Expression.IntLiteral(lengthBytes == null ? 1 : lengthBytes) : null, null,
                Occurrence.ONCE, semantics);
        def.editRoot(root -> Descriptions.addChild(root, root.id(), field));
    }
}
