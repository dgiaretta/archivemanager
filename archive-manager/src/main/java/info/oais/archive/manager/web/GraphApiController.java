package info.oais.archive.manager.web;

import info.oais.archive.manager.model.GraphData;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GraphApiController {

    private final GraphService graphService;
    private final ArchiveService archive;

    public GraphApiController(GraphService graphService, ArchiveService archive) {
        this.graphService = graphService;
        this.archive = archive;
    }

    /** Full relationship graph (all resource-to-resource triples in the data graph). */
    @GetMapping("/api/graph")
    public GraphData full() {
        return graphService.fullGraph();
    }

    /** Subgraph reachable from one resource within {@code depth} hops (default 2). */
    @GetMapping("/api/graph/{id}")
    public GraphData subgraph(@PathVariable String id, @RequestParam(defaultValue = "2") int depth) {
        String iri = archive.decodeId(id);
        return graphService.subgraph(iri, Math.min(Math.max(depth, 1), 5));
    }
}
