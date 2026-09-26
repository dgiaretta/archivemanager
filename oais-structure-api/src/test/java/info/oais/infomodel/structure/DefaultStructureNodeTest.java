package info.oais.infomodel.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DefaultStructureNodeTest {

	@Test
	void buildsACompositeWithLeafChildren() {
		DefaultStructureNode x = DefaultStructureNode.leaf("x", 42);
		DefaultStructureNode y = DefaultStructureNode.leaf("y", -7);
		DefaultStructureNode label = DefaultStructureNode.leaf("label", "hello");

		DefaultStructureNode point = DefaultStructureNode.builder("point", StructureNodeKind.COMPOSITE)
				.addChild(x).addChild(y).addChild(label)
				.sourceRange(ByteRange.ofBytes(0, 9))
				.build();

		assertEquals(StructureNodeKind.COMPOSITE, point.getKind());
		assertEquals(3, point.getChildren().size());
		assertEquals(42, point.valueAt("x").orElseThrow());
		assertEquals("hello", point.valueAt("label").orElseThrow());
		assertEquals(List.of(x), point.childrenNamed("x"));
		assertTrue(point.valueAt("does-not-exist").isEmpty());
		assertEquals(72L, point.getSourceRange().orElseThrow().bitLength());
	}

	@Test
	void leafCannotHaveChildren() {
		assertThrows(IllegalStateException.class, () ->
				DefaultStructureNode.builder("bad", StructureNodeKind.LEAF)
						.addChild(DefaultStructureNode.leaf("child", 1))
						.build());
	}

	@Test
	void compositeCannotCarryAValue() {
		assertThrows(IllegalStateException.class, () ->
				DefaultStructureNode.builder("bad", StructureNodeKind.COMPOSITE)
						.value("not allowed")
						.build());
	}

	@Test
	void copyOfDetachesAnArbitraryStructureNodeImplementation() {
		StructureNode handwritten = new StructureNode() {
			@Override public String getName() { return "wrapped"; }
			@Override public java.util.Optional<String> getTypeName() { return java.util.Optional.of("int32"); }
			@Override public StructureNodeKind getKind() { return StructureNodeKind.LEAF; }
			@Override public java.util.Optional<Object> getValue() { return java.util.Optional.of(7); }
			@Override public List<StructureNode> getChildren() { return List.of(); }
			@Override public java.util.Map<String, Object> getAttributes() { return java.util.Map.of("unit", "m"); }
			@Override public java.util.Optional<ByteRange> getSourceRange() { return java.util.Optional.empty(); }
		};

		DefaultStructureNode copy = DefaultStructureNode.copyOf(handwritten);

		assertEquals("wrapped", copy.getName());
		assertEquals("int32", copy.getTypeName().orElseThrow());
		assertEquals(7, copy.getValue().orElseThrow());
		assertEquals("m", copy.getAttributes().get("unit"));
	}
}
