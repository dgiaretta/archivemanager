package info.oais.infomodel.structure.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepInfoManifestTest {

	private static final String MANIFEST = """
			@prefix im:     <http://ontology.oais.info/im/> .
			@prefix bridge: <https://oais.info/bridge#> .
			@prefix rdfs:   <http://www.w3.org/2000/01/rdf-schema#> .
			@prefix rico:   <https://www.ica.org/standards/RiC/ontology#> .

			<#readings> a im:DigitalObject ;
			    bridge:hasStorageLocation <data/readings-2026.bin> ;
			    im:interpretedUsing <#repinfo> .

			<#repinfo> a im:RepInfoAndGroup ;
			    im:hasGroupMember <#structure> , <#semantics> , <#software> ;
			    im:hasStructureRepresentationInformation <#structure> ;
			    im:hasSemanticRepresentationInformation <#semantics> .

			<#structure> a im:RepInfoOrGroup , im:StructureRepresentationInformation ;
			    im:hasGroupMember <#dfdl> , <#kaitai> .
			<#dfdl> a im:StructureRepresentationInformation ;
			    im:specificationLanguage "DFDL" ;
			    bridge:hasStorageLocation <station.dfdl.xsd> .
			<#kaitai> a im:StructureRepresentationInformation ;
			    im:specificationLanguage "Kaitai Struct" ;
			    bridge:hasStorageLocation <station.ksy> ;
			    im:generatedClassName "com.example.Station" .
			<#software> a im:OtherRepresentationInformation ; rdfs:label "Apache Daffodil" .

			<#semantics> a im:SemanticRepresentationInformation ;
			    im:interpretedUsingRecurse <#view> , <#temp> .
			<#view> a im:ViewSpecification ; bridge:hasStorageLocation <views/station-table.xml> .
			<#temp> a im:SemanticRepresentationInformation ;
			    bridge:structuralPath "reading.temp" ; rdfs:label "Air temperature" ;
			    rico:hasUnitOfMeasurement [ rdfs:label "K" ] .

			<#other> a im:DigitalObject ;
			    bridge:hasStorageLocation <https://example.org/data/other.bin> ;
			    im:interpretedUsing <#repinfo> .
			""";

	@Test
	void readsFilesRelativeToTheManifestAndTheAlternatives(@TempDir Path dir) throws Exception {
		Path manifest = Files.writeString(dir.resolve("anything I like.ttl"), MANIFEST, StandardCharsets.UTF_8);

		RepInfoManifest m = RepInfoManifest.read(manifest.toUri());

		assertEquals(2, m.dataObjects().size());
		DescribedData readings = m.select("readings");
		assertEquals(dir.resolve("data/readings-2026.bin").toUri(), readings.data());
		assertEquals(2, readings.structures().size());
		StructureDescription dfdl = readings.structure(StructureDescription.LANGUAGES, s -> true).orElseThrow();
		assertEquals("DFDL", dfdl.language());
		assertEquals(dir.resolve("station.dfdl.xsd").toUri(), dfdl.location());
		StructureDescription kaitai = readings.structure(StructureDescription.LANGUAGES,
				s -> !s.language().equals("DFDL")).orElseThrow();
		assertEquals("com.example.Station", kaitai.generatedClassName());
		assertEquals(dir.resolve("views/station-table.xml").toUri(), readings.view(ViewDescription.TABLE).orElseThrow().location());
		ElementMeaning temp = readings.meaningOf("reading", "temp").orElseThrow();
		assertEquals("Air temperature", temp.label());
		assertEquals("K", temp.units());
		assertEquals(URI.create("https://example.org/data/other.bin"), m.select("other.bin").data());
	}

	@Test
	void asksWhichWhenThereIsMoreThanOne() {
		RepInfoManifest m = RepInfoManifest.read(new ByteArrayInputStream(MANIFEST.getBytes(StandardCharsets.UTF_8)),
				URI.create("file:///x/manifest.ttl"));
		RepInfoManifest.ManifestException e = assertThrows(RepInfoManifest.ManifestException.class, () -> m.select(null));
		assertTrue(e.getMessage().contains("other, readings"), e.getMessage());
		assertThrows(RepInfoManifest.ManifestException.class, () -> m.select("nothing"));
	}

	@Test
	void refusesWhatIsNotAManifest() {
		assertThrows(RepInfoManifest.ManifestException.class, () -> RepInfoManifest.read(
				new ByteArrayInputStream("this is not turtle".getBytes(StandardCharsets.UTF_8)), URI.create("file:///x.ttl")));
		assertThrows(RepInfoManifest.ManifestException.class, () -> RepInfoManifest.read(
				new ByteArrayInputStream("<#a> <#b> <#c> .".getBytes(StandardCharsets.UTF_8)), URI.create("file:///x.ttl")));
		assertTrue(RepInfoManifest.looksLikeManifest(MANIFEST));
		assertFalse(RepInfoManifest.looksLikeManifest("42,-7,hi\n"));
	}

	@Test
	void writesWhatItReads(@TempDir Path dir) throws Exception {
		DescribedData written = new DescribedData("#points", "Points \"test\"", URI.create("points.csv"),
				List.of(new StructureDescription(null, StructureDescription.DFDL, URI.create("points.dfdl.xsd"), null),
						new StructureDescription(null, StructureDescription.DRB_SDF, URI.create("sdf/points.drb.xsd"), null)),
				List.of(new ViewDescription(null, ViewDescription.TABLE, URI.create("points-table-view.xml"))),
				List.of(new ElementMeaning("point.x", "X ordinate", "Along the axis\nin metres", "m", null)));
		Path manifest = Files.writeString(dir.resolve("points.ttl"), ManifestWriter.write("Points", List.of(written)));

		DescribedData read = RepInfoManifest.read(manifest.toUri()).select(null);

		assertEquals("Points \"test\"", read.name());
		assertEquals(dir.resolve("points.csv").toUri(), read.data());
		assertEquals(List.of("DFDL", "DRB SDF"), read.structures().stream().map(StructureDescription::language).sorted().toList());
		assertEquals(dir.resolve("sdf/points.drb.xsd").toUri(), read.structures().stream()
				.filter(s -> s.language().equals("DRB SDF")).findFirst().orElseThrow().location());
		assertEquals("Along the axis\nin metres", read.meaningOf("point", "x").orElseThrow().definition());
		assertNull(read.meaningOf("point", "y").orElse(null));
	}
}
