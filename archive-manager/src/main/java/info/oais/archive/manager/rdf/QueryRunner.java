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
import java.util.List;
import java.util.Map;

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
