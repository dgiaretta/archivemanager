package info.oais.archive.manager.rdf;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thin wrapper around Jena ARQ for running SPARQL SELECT queries and
 * getting back plain, display-ready rows -- one {@code Map<String,String>}
 * per solution, keyed by SPARQL variable name.
 */
@Component
public class QueryRunner {

    /**
     * Runs a SELECT query (which must already include its own PREFIX
     * declarations, see {@link Ns#PREFIXES}) against the given model.
     */
    public List<Map<String, String>> select(Model model, String sparql) {
        Query query = QueryFactory.create(sparql);
        List<Map<String, String>> rows = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                Map<String, String> row = new LinkedHashMap<>();
                for (String var : rs.getResultVars()) {
                    row.put(var, sol.contains(var) ? render(sol.get(var)) : null);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    /** Renders an RDF term as plain text: the lexical form of a literal, or the IRI of a resource. */
    public String render(RDFNode node) {
        if (node == null) {
            return null;
        }
        if (node.isLiteral()) {
            return node.asLiteral().getLexicalForm();
        }
        if (node.isURIResource()) {
            return node.asResource().getURI();
        }
        return node.toString();
    }

    /**
     * Every distinct value bound to any variable in a SELECT query's results that
     * is itself a URI resource (not a literal, and not a blank node) -- e.g. every
     * {@code ?s ?p ?o} triple's subject/object across all rows, but not a literal
     * that merely happens to look like a URL. Re-runs the query rather than
     * reusing {@link #select}'s already-rendered string rows, since those have
     * already lost the URI-vs-literal distinction (see {@link #render}) -- fine
     * for the SPARQL console's occasional, human-triggered queries, where running
     * the query twice is cheap next to the cost of getting this wrong.
     *
     * <p>Powers the SPARQL console's "view as graph" button: every resource any
     * column of the results actually refers to, regardless of which variable name
     * the user's own query happened to bind it to.
     */
    public Set<String> uriResourcesIn(Model model, String sparql) {
        Query query = QueryFactory.create(sparql);
        Set<String> uris = new LinkedHashSet<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                for (String var : rs.getResultVars()) {
                    if (sol.contains(var)) {
                        RDFNode node = sol.get(var);
                        if (node.isURIResource()) {
                            uris.add(node.asResource().getURI());
                        }
                    }
                }
            }
        }
        return uris;
    }

    /** The local name (final path segment / fragment) of an IRI, for compact display. */
    public String localName(String iri) {
        if (iri == null) {
            return null;
        }
        int hash = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        int cut = Math.max(hash, slash);
        return cut >= 0 && cut < iri.length() - 1 ? iri.substring(cut + 1) : iri;
    }
}
