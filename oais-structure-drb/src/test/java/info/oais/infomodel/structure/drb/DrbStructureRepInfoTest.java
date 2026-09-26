package info.oais.infomodel.structure.drb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.drb.fakedrb.FakeDrbFactoryResolver;

/**
 * Exercises the reflective bridge against {@link FakeDrbFactoryResolver}
 * rather than a real DRB jar (not available - see README-DRB.md). This
 * proves the reflection plumbing itself is correct; it cannot prove that a
 * real DRB version's method names match the candidates
 * {@code DrbStructureRepInfo}/{@code DrbStructureNode} try.
 */
class DrbStructureRepInfoTest {

	@Test
	void bridgesAFakeDrbNodeTreeReflectively() {
		String text = "x=42;y=-7;label=hi";
		DigitalObject digitalObject = new DigitalObjectRefImpl(
				new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

		DrbFormatSpecification spec = new DrbFormatSpecification(
				FakeDrbFactoryResolver.class.getName(), null, null);
		DrbStructureRepInfo structureRepInfo = new DrbStructureRepInfo(spec);

		StructureNode point = structureRepInfo.apply(digitalObject);

		assertEquals("42", point.valueAt("x").orElseThrow());
		assertEquals("-7", point.valueAt("y").orElseThrow());
		assertEquals("hi", point.valueAt("label").orElseThrow());
	}
}
