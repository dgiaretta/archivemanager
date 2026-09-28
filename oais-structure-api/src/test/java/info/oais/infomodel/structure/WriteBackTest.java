package info.oais.infomodel.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WriteBackTest {

	@Test
	void parsesAndPrintsElementPaths() {
		ElementPath path = ElementPath.parse(" /header/sample[2]/value ");
		assertEquals(List.of(new ElementPath.Step("header", 1), new ElementPath.Step("sample", 2),
				new ElementPath.Step("value", 1)), path.steps());
		assertEquals("/header/sample[2]/value", path.toString());
		assertEquals(path, ElementPath.parse("header/sample[2]/value[1]"));

		assertThrows(IllegalArgumentException.class, () -> ElementPath.parse("/"));
		assertThrows(IllegalArgumentException.class, () -> ElementPath.parse("/a//b"));
		assertThrows(IllegalArgumentException.class, () -> ElementPath.parse("/a[0]"));
		assertThrows(IllegalArgumentException.class, () -> ElementPath.parse("/a[x]"));
	}

	@Test
	void findsNodesByPath() {
		StructureNode root = DefaultStructureNode.builder("root", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.builder("s", StructureNodeKind.LEAF).value(1).build())
				.addChild(DefaultStructureNode.builder("s", StructureNodeKind.LEAF).value(2).build())
				.build();
		assertEquals(2, ElementPath.parse("/s[2]").find(root).getValue().orElseThrow());
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> ElementPath.parse("/s[3]").find(root));
		assertEquals("There's no element /s[3]: there are only 2 called 's'", e.getMessage());
	}

	@Test
	void comparesWrittenBytes() {
		assertTrue(RoundTrip.compare(new byte[] {1, 2, 3}, new byte[] {1, 2, 3}).identical());

		RoundTrip changed = RoundTrip.compare(new byte[] {1, 2, 3, 4}, new byte[] {1, 9, 3});
		assertFalse(changed.identical());
		assertEquals(1, changed.firstDifference());
		assertEquals(2, changed.differingBytes());
		assertEquals("Different: 3 bytes were written for 4 read; 2 bytes differ, the first at offset 1.",
				changed.describe());

		assertEquals(2, RoundTrip.compare(new byte[] {1, 2}, new byte[] {1, 2, 0}).firstDifference());
	}
}
