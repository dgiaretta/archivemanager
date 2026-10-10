package info.oais.archive.manager.web;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.QueryRunner;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/sparql")
public class SparqlController {

    private static final String DEFAULT_QUERY = Ns.PREFIXES + """

            SELECT ?s ?p ?o WHERE {
              ?s ?p ?o .
              FILTER(STRSTARTS(STR(?s), "http://example.org/archive/"))
            } LIMIT 50
            """;

    private final RdfStore store;
    private final QueryRunner queryRunner;
    private final ArchiveService archive;

    private final info.oais.archive.manager.i18n.Messages messages;

    public SparqlController(RdfStore store, QueryRunner queryRunner, ArchiveService archive, info.oais.archive.manager.i18n.Messages messages) {
        this.messages = messages;
        this.store = store;
        this.queryRunner = queryRunner;
        this.archive = archive;
    }

    @GetMapping
    public String console(Model model) {
        model.addAttribute("queryText", DEFAULT_QUERY);
        return "sparql/console";
    }

    @PostMapping
    public String run(@RequestParam String queryText, Model model) {
        model.addAttribute("queryText", queryText);
        try {
            Query query = QueryFactory.create(queryText);
            if (!query.isSelectType()) {
                model.addAttribute("error", messages.get("error.selectOnly"));
                return "sparql/console";
            }
            List<Map<String, String>> rows = queryRunner.select(store.queryModel(), queryText);
            model.addAttribute("columns", query.getResultVars());
            model.addAttribute("rows", rows);
            List<String> resourceIds = queryRunner.uriResourcesIn(store.queryModel(), queryText).stream()
                    .map(archive::encodeId)
                    .toList();
            model.addAttribute("resourceIds", resourceIds);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "sparql/console";
    }
}
