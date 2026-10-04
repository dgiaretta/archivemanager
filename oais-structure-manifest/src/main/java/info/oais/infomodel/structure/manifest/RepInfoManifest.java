package info.oais.infomodel.structure.manifest;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * A Representation Information manifest: a Turtle excerpt of one or more
 * Data Objects' OAIS Representation Information, naming every file
 * explicitly -- the data itself and each description -- with
 * {@code im:hasStorageLocation}, relative to the manifest's own location
 * (or as absolute URLs). Every term is from the OAIS Information Model or its
 * local extensions; manifests written before some were moved there say
 * {@code bridge:hasStorageLocation}, {@code bridge:structuralPath},
 * {@code bridge:representsConcept} and {@code rico:hasUnitOfMeasurement}
 * instead, which are still read. It has the same shape the archive saves: a Data
 * Object is {@code im:interpretedUsing} Representation Information, whose
 * AND/OR groups ({@code im:hasGroupMember}) lead to
 * <ul>
 *   <li>structure descriptions -- Structure Representation Information with an
 *       {@code im:specificationLanguage} ("DFDL", "Kaitai Struct", "DRB SDF" or
 *       "DRB") and, for Kaitai Struct, an {@code im:generatedClassName}. The
 *       alternatives of an OR group are equivalent: any one will do;</li>
 *   <li>view specifications -- {@code im:ViewSpecification}, with an
 *       {@code im:viewKind} ("table", "timeSeries", "vector"; "table" if not
 *       given);</li>
 *   <li>element semantics -- Semantic Representation Information with a
 *       {@code im:structuralPath}, its {@code rdfs:label},
 *       {@code skos:definition} and units ({@code im:hasUnitOfMeasurement}
 *       with an {@code rdfs:label}).</li>
 * </ul>
 * Everything else in the manifest (software Other Representation
 * Information, provenance, ...) is allowed and ignored here. A manifest is
 * recognised by what it holds, never by its file name.
 */
public final class RepInfoManifest {

	public static final String IM = "http://ontology.oais.info/im/";
	public static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
	/** Namespaces some of the terms were in before the OAIS local extensions; still read, never written. */
	private static final String BRIDGE = "https://oais.info/bridge#";
	private static final String RICO = "https://www.ica.org/standards/RiC/ontology#";

	private final List<DescribedData> dataObjects;

	private RepInfoManifest(List<DescribedData> dataObjects) {
		this.dataObjects = List.copyOf(dataObjects);
	}

	public List<DescribedData> dataObjects() {
		return dataObjects;
	}

	/**
	 * Reads the manifest at {@code location} (a file or a URL); relative IRIs
	 * in it resolve against that location.
	 *
	 * @throws ManifestException if it isn't Turtle, or describes no Data Object
	 */
	public static RepInfoManifest read(URI location) {
		Model model = ModelFactory.createDefaultModel();
		try {
			RDFParser.create().source(location.toString()).base(location.toString()).lang(Lang.TURTLE).parse(model);
		} catch (RiotException e) {
			throw new ManifestException(location + " isn't a Turtle manifest: " + e.getMessage(), e);
		}
		return of(model, location.toString());
	}

	/** Reads a manifest from {@code in}; relative IRIs in it resolve against {@code base}. */
	public static RepInfoManifest read(InputStream in, URI base) {
		Model model = ModelFactory.createDefaultModel();
		try {
			RDFParser.create().source(in).base(base.toString()).lang(Lang.TURTLE).parse(model);
		} catch (RiotException e) {
			throw new ManifestException(base + " isn't a Turtle manifest: " + e.getMessage(), e);
		}
		return of(model, base.toString());
	}

	/**
	 * Whether {@code text} -- e.g. the start of a file -- looks like a
	 * manifest: Turtle using the OAIS Information Model's namespace and
	 * saying what a Data Object is interpreted using. A quick check for
	 * recognising a file by its content, not a full parse.
	 */
	public static boolean looksLikeManifest(String text) {
		return text.contains("ontology.oais.info/im/") && text.contains("interpretedUsing")
				&& text.contains("hasStorageLocation");
	}

	private static RepInfoManifest of(Model m, String where) {
		Property interpretedUsing = m.createProperty(IM + "interpretedUsing");
		List<DescribedData> found = new ArrayList<>();
		for (Resource dataObject : m.listSubjectsWithProperty(interpretedUsing).toList()) {
			Statement data = storage(m, dataObject);
			if (data == null || !data.getObject().isURIResource()) {
				continue;
			}
			found.add(describe(m, dataObject, URI.create(data.getObject().asResource().getURI())));
		}
		if (found.isEmpty()) {
			throw new ManifestException(where + " describes no Data Object: nothing with both im:interpretedUsing "
					+ "and an im:hasStorageLocation for its bits");
		}
		found.sort(Comparator.comparing(DescribedData::iri));
		return new RepInfoManifest(found);
	}

	private static final List<String> FOLLOWED = List.of("interpretedUsing", "hasGroupMember",
			"hasStructureRepresentationInformation", "hasSemanticRepresentationInformation",
			"hasOtherRepresentationInformation", "interpretedUsingRecurse");

	private static DescribedData describe(Model m, Resource dataObject, URI data) {
		Set<Resource> reached = new LinkedHashSet<>();
		Deque<Resource> todo = new ArrayDeque<>();
		for (Statement s : dataObject.listProperties(m.createProperty(IM + "interpretedUsing")).toList()) {
			if (s.getObject().isResource()) {
				todo.push(s.getObject().asResource());
			}
		}
		while (!todo.isEmpty()) {
			Resource r = todo.pop();
			if (!reached.add(r)) {
				continue;
			}
			for (String p : FOLLOWED) {
				for (Statement s : r.listProperties(m.createProperty(IM + p)).toList()) {
					if (s.getObject().isResource()) {
						todo.push(s.getObject().asResource());
					}
				}
			}
		}
		List<StructureDescription> structures = new ArrayList<>();
		List<ViewDescription> views = new ArrayList<>();
		List<ElementMeaning> meanings = new ArrayList<>();
		for (Resource r : reached) {
			String language = literal(r, m.createProperty(IM + "specificationLanguage"));
			if (language != null) {
				structures.add(new StructureDescription(id(r), language, location(m, r),
						literal(r, m.createProperty(IM + "generatedClassName"))));
			}
			if (r.hasProperty(RDF.type, m.createResource(IM + "ViewSpecification"))) {
				URI location = location(m, r);
				if (location == null) {
					throw new ManifestException(id(r) + " is a view specification without an im:hasStorageLocation");
				}
				String kind = literal(r, m.createProperty(IM + "viewKind"));
				views.add(new ViewDescription(id(r), kind == null ? ViewDescription.TABLE : kind, location));
			}
			Statement pathStatement = property(m, r, "structuralPath", BRIDGE);
			String path = pathStatement == null || !pathStatement.getObject().isLiteral() ? null
					: pathStatement.getObject().asLiteral().getLexicalForm();
			if (path != null) {
				Statement units = property(m, r, "hasUnitOfMeasurement", RICO);
				String unitLabel = units == null || !units.getObject().isResource() ? null
						: literal(units.getObject().asResource(), RDFS.label);
				Statement concept = property(m, r, "representsConcept", BRIDGE);
				meanings.add(new ElementMeaning(path, literal(r, RDFS.label), literal(r, m.createProperty(SKOS + "definition")),
						unitLabel, concept == null || !concept.getObject().isURIResource() ? null
								: concept.getObject().asResource().getURI()));
			}
		}
		String label = literal(dataObject, RDFS.label);
		return new DescribedData(id(dataObject), label != null ? label : lastPart(id(dataObject)), data, structures,
				views, meanings);
	}

	/**
	 * The Data Object {@code which} names -- the fragment of a location like
	 * {@code manifest.ttl#readings}: the end of its IRI, its label, or its
	 * data's file name. With no {@code which}, the manifest's only Data Object.
	 *
	 * @throws ManifestException if nothing, or more than one thing, matches
	 */
	public DescribedData select(String which) {
		if (which == null || which.isBlank()) {
			if (dataObjects.size() == 1) {
				return dataObjects.get(0);
			}
			throw new ManifestException("The manifest describes " + dataObjects.size() + " Data Objects; say which, "
					+ "e.g. manifest.ttl#" + lastPart(dataObjects.get(0).iri()) + " (one of: " + names() + ")");
		}
		List<DescribedData> matches = dataObjects.stream().filter(d -> which.equals(lastPart(d.iri()))
				|| which.equals(d.iri()) || which.equals(d.name()) || which.equals(lastPart(d.data().toString()))).toList();
		if (matches.size() != 1) {
			throw new ManifestException("'" + which + "' names " + (matches.isEmpty() ? "no" : "more than one")
					+ " Data Object in the manifest (" + names() + ")");
		}
		return matches.get(0);
	}

	private String names() {
		return String.join(", ", dataObjects.stream().map(d -> lastPart(d.iri())).toList());
	}

	private static URI location(Model m, Resource r) {
		Statement s = storage(m, r);
		return s == null || !s.getObject().isURIResource() ? null : URI.create(s.getObject().asResource().getURI());
	}

	/** {@code r}'s {@code im:hasStorageLocation}, else its {@code bridge:hasStorageLocation} (the term's old name). */
	private static Statement storage(Model m, Resource r) {
		return property(m, r, "hasStorageLocation", BRIDGE);
	}

	/** {@code r}'s {@code im:} property {@code name}, else the same-named one in {@code oldNamespace}, where it was. */
	private static Statement property(Model m, Resource r, String name, String oldNamespace) {
		Statement s = r.getProperty(m.createProperty(IM + name));
		return s != null ? s : r.getProperty(m.createProperty(oldNamespace + name));
	}

	private static String literal(Resource r, Property p) {
		Statement s = r.getProperty(p);
		if (s == null) {
			return null;
		}
		RDFNode o = s.getObject();
		String text = o.isLiteral() ? o.asLiteral().getLexicalForm() : o.isURIResource() ? o.asResource().getURI() : null;
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static String id(Resource r) {
		return r.isURIResource() ? r.getURI() : "_:" + r.getId().getLabelString();
	}

	private static String lastPart(String iri) {
		int cut = Math.max(Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/')), iri.lastIndexOf(':'));
		return cut < 0 ? iri : iri.substring(cut + 1);
	}

	/** Why a manifest couldn't be read or used. */
	public static class ManifestException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public ManifestException(String message) {
			super(message);
		}

		public ManifestException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/** The Data Object's name as {@link #select} accepts it. */
	public static String selector(DescribedData d) {
		return lastPart(d.iri());
	}
}
