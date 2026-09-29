package info.oais.infomodel.implementation.representationinformation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.oais.infomodel.implementation.OtherRepInfoRefImpl;
import info.oais.infomodel.interfaces.RepresentationInformation;

/**
 * Groups of Representation Information as JSON: every member written, as an
 * array named after the kind of group (no repeated keys), nesting included.
 */
class RepInfoGroupJsonTest {

	private static RepInfoAndGroupRefImpl excelAndMeanings() {
		RepInfoOrGroupRefImpl software = new RepInfoOrGroupRefImpl(new ArrayList<>(List.of(new OtherRepInfoRefImpl(),
				new OtherRepInfoRefImpl())));
		return new RepInfoAndGroupRefImpl(new ArrayList<RepresentationInformation>(List.of(software,
				new OtherRepInfoRefImpl())));
	}

	@Test
	void writesEveryMemberOfNestedGroupsWithoutRepeatedKeys() throws Exception {
		PrintStream out = System.out;
		ByteArrayOutputStream printed = new ByteArrayOutputStream();
		System.setOut(new PrintStream(printed));
		JsonNode json;
		try {
			json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(excelAndMeanings()));
		} finally {
			System.setOut(out);
		}

		assertEquals(2, json.get("RepInfoAndGroup").size());
		JsonNode or = json.get("RepInfoAndGroup").get(0);
		assertTrue(or.get("RepInfoOrGroup").isArray());
		assertEquals(2, or.get("RepInfoOrGroup").size());
		assertFalse(json.has("RepInfoGroup"));
		assertEquals("", printed.toString(), "nothing printed while writing JSON");
	}
}
