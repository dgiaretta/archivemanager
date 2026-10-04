package info.oais.infomodel.structure.manifest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a {@link RepInfoManifest} as Turtle, in the shape the reader
 * expects: each Data Object {@code im:interpretedUsing} an AND group of an
 * OR group of its structure descriptions (equivalent alternatives) and its
 * Semantic Representation Information, which leads to its view
 * specifications and element semantics. Relative locations stay relative,
 * so a manifest written next to its files can be moved with them.
 *
 * <p>Each Data Object's {@link DescribedData#iri()} should be a fragment
 * such as {@code #readings}; the individuals of its Representation
 * Information are named after it ({@code #readings-structure}, ...).</p>
 */
public final class ManifestWriter {

	private ManifestWriter() {
	}

	public static String write(String title, List<DescribedData> dataObjects) {
		StringBuilder sb = new StringBuilder();
		sb.append("# ").append(title.replace('\n', ' ')).append('\n');
		sb.append("""
				#
				# A Representation Information manifest: the Data Object(s) below, where their
				# bits are, and the OAIS Representation Information needed to interpret them --
				# structure descriptions (equivalent alternatives: any one will do), how to view
				# the data, and what its elements mean. Every file is named explicitly; relative
				# locations resolve against this file's own location.

				@prefix im:     <http://ontology.oais.info/im/> .
				@prefix rdfs:   <http://www.w3.org/2000/01/rdf-schema#> .
				@prefix skos:   <http://www.w3.org/2004/02/skos/core#> .

				""");
		for (DescribedData d : dataObjects) {
			writeDataObject(sb, d);
		}
		return sb.toString();
	}

	private static void writeDataObject(StringBuilder sb, DescribedData d) {
		// An absolute IRI (e.g. the archive's own for the Data Object) is kept; anything else is local.
		boolean absolute = d.iri().matches("[A-Za-z][A-Za-z0-9+.-]*:.*");
		String self = absolute || d.iri().startsWith("#") ? d.iri() : "#" + d.iri();
		String base = self.substring(Math.max(self.lastIndexOf('#'), self.lastIndexOf('/')) + 1)
				.replaceAll("[^A-Za-z0-9_.-]", "-");
		String repInfo = "#" + base + "-repinfo";
		String structure = "#" + base + "-structure";
		String semantics = "#" + base + "-semantics";

		sb.append(iri(self)).append(" a im:DigitalObject ;\n");
		sb.append("    rdfs:label ").append(literal(d.name())).append(" ;\n");
		sb.append("    im:hasStorageLocation ").append(iri(d.data())).append(" ;\n");
		sb.append("    im:interpretedUsing ").append(iri(repInfo)).append(" .\n\n");

		List<String> members = new ArrayList<>();
		if (!d.structures().isEmpty()) {
			members.add(structure);
		}
		members.add(semantics);
		sb.append(iri(repInfo)).append(" a im:RepInfoAndGroup , im:RepresentationInformation ;\n");
		sb.append("    rdfs:label ").append(literal("Representation Information for " + d.name())).append(" ;\n");
		sb.append("    im:hasGroupMember ").append(String.join(" , ", members.stream().map(ManifestWriter::iri).toList()))
				.append(" ;\n");
		if (!d.structures().isEmpty()) {
			sb.append("    im:hasStructureRepresentationInformation ").append(iri(structure)).append(" ;\n");
		}
		sb.append("    im:hasSemanticRepresentationInformation ").append(iri(semantics)).append(" .\n\n");

		if (!d.structures().isEmpty()) {
			List<String> alternatives = new ArrayList<>();
			for (int i = 0; i < d.structures().size(); i++) {
				alternatives.add("#" + base + "-structure-" + (i + 1));
			}
			sb.append(iri(structure)).append(" a im:RepInfoOrGroup , im:StructureRepresentationInformation ;\n");
			sb.append("    rdfs:label ").append(literal("Structure of " + d.name() + ": any one of these descriptions"))
					.append(" ;\n");
			sb.append("    im:hasGroupMember ").append(String.join(" , ", alternatives.stream().map(ManifestWriter::iri)
					.toList())).append(" .\n\n");
			for (int i = 0; i < d.structures().size(); i++) {
				StructureDescription s = d.structures().get(i);
				sb.append(iri(alternatives.get(i))).append(" a im:StructureRepresentationInformation ;\n");
				sb.append("    im:specificationLanguage ").append(literal(s.language()));
				if (s.location() != null) {
					sb.append(" ;\n    im:hasStorageLocation ").append(iri(s.location()));
				}
				if (s.generatedClassName() != null) {
					sb.append(" ;\n    im:generatedClassName ").append(literal(s.generatedClassName()));
				}
				sb.append(" .\n\n");
			}
		}

		List<String> parts = new ArrayList<>();
		for (int i = 0; i < d.views().size(); i++) {
			parts.add("#" + base + "-view-" + (i + 1));
		}
		for (int i = 0; i < d.meanings().size(); i++) {
			parts.add("#" + base + "-element-" + (i + 1));
		}
		sb.append(iri(semantics)).append(" a im:SemanticRepresentationInformation ;\n");
		sb.append("    rdfs:label ").append(literal("Semantics of " + d.name()));
		if (!parts.isEmpty()) {
			sb.append(" ;\n    im:interpretedUsingRecurse ")
					.append(String.join(" ,\n        ", parts.stream().map(ManifestWriter::iri).toList()));
		}
		sb.append(" .\n\n");
		for (int i = 0; i < d.views().size(); i++) {
			ViewDescription v = d.views().get(i);
			sb.append(iri(parts.get(i))).append(" a im:ViewSpecification , im:SemanticRepresentationInformation ;\n");
			sb.append("    im:viewKind ").append(literal(v.kind())).append(" ;\n");
			sb.append("    im:hasStorageLocation ").append(iri(v.location())).append(" .\n\n");
		}
		for (int i = 0; i < d.meanings().size(); i++) {
			ElementMeaning e = d.meanings().get(i);
			sb.append(iri(parts.get(d.views().size() + i))).append(" a im:SemanticRepresentationInformation ;\n");
			sb.append("    im:structuralPath ").append(literal(e.path()));
			if (e.label() != null) {
				sb.append(" ;\n    rdfs:label ").append(literal(e.label()));
			}
			if (e.definition() != null) {
				sb.append(" ;\n    skos:definition ").append(literal(e.definition()));
			}
			if (e.units() != null) {
				sb.append(" ;\n    im:hasUnitOfMeasurement [ rdfs:label ").append(literal(e.units())).append(" ]");
			}
			if (e.concept() != null) {
				sb.append(" ;\n    im:representsConcept ").append(iri(URI.create(e.concept())));
			}
			sb.append(" .\n\n");
		}
	}

	private static String iri(String relative) {
		return "<" + relative + ">";
	}

	private static String iri(URI uri) {
		String text = uri.toASCIIString();
		if (text.matches(".*[\\s<>\"{}|^`\\\\].*")) {
			throw new IllegalArgumentException("Not a usable IRI: " + text);
		}
		return "<" + text + ">";
	}

	/** A Turtle string literal, escaped. */
	static String literal(String text) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : text.toCharArray()) {
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case '"' -> sb.append("\\\"");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> sb.append(c);
			}
		}
		return sb.append('"').toString();
	}
}
