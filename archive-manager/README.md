# Archive Manager

A small Spring Boot application for browsing and managing archival holdings
described in **RiC-O 1.1** (Records in Contexts Ontology), with a live view of
how each record maps onto the **OAIS Information Model**, via the custom
bridging ontology (`oais-ric-bridge.ttl`) developed alongside this app.

There is no relational database here: the archive *is* an RDF graph, stored
in [Apache Jena TDB2](https://jena.apache.org/documentation/tdb2/), a real
disk-backed, transactional triple store (see [Storage](#storage) below) --
not an in-memory model with hand-rolled file persistence. Spring Boot +
Thymeleaf handle the web UI.

## What's in the box

```
run.bat                     Windows launch script with -Dfile.encoding=UTF-8
                              -Dsun.jnu.encoding=UTF-8 already set -- use this
                              instead of a bare "java -jar" if any of your data
                              is in Dhivehi or another non-Latin script; see the
                              internationalisation section for why that matters
                              specifically on Windows with Java 17.
run.sh                      The same, for Linux/macOS (chmod +x first). This
                              matters on Linux too, not only Windows -- it
                              depends on the actual locale environment
                              (LANG/LC_ALL/LC_CTYPE) wherever the jar gets
                              launched from, which a systemd unit, cron job, or
                              container doesn't always inherit the same way an
                              interactive shell does.
src/main/resources/rdf/
  oais_im_schema-sh-v5.ttl   OAIS Information Model schema (classes/properties).
                              Class/property IRIs were renamed from the original
                              opaque OQ-2 identifiers (c-0011, s-0014, ...) to
                              readable names (AccessRightsInformation,
                              hasDataObject, ...) derived from their rdfs:label.
                              The original identifiers are preserved as an
                              oais:identifier annotation on every class/property
                              for traceability back to the OAIS spec.
  oais-im-local-extensions.ttl
                              Local extensions to that schema, same im:
                              namespace, loaded alongside it: groups of
                              Representation Information (im:RepInfoAndGroup,
                              im:RepInfoOrGroup, im:hasGroupMember), and
                              Designated Community, Preservation Objective and
                              Transformation Information Property with their
                              properties, moved out of the schema (now marked
                              local-extension).
  oais-ric-bridge.ttl        The bridging ontology: RiC-O <-> OAIS class
                              correspondences (SKOS mapping relations) plus
                              bridge:hasOAISCounterpart / bridge:hasRiCDescription,
                              two object properties for linking individuals
  rico-vocabulary.ttl        Companion RiC-O stub: all 107 RiC-O 1.1 classes, a
                              curated set of common object/datatype properties, and
                              rdfs:subClassOf hierarchy for the branches this app's
                              own queries walk generically (Agent, RecordResource,
                              Event, Rule) -- not the full RiC-O OWL file; see the
                              note at the top of the file for exactly what's and
                              isn't covered. Powers the entity editor's pickers and
                              the ontology-driven class-membership queries in
                              ArchiveService/GraphService (see Design notes below).
  dc-vocabulary.ttl          The real, standard Dublin Core Elements 1.1 namespace
                              (not a project invention) -- declares the 15 elements
                              NAM's Bulk Upload Spreadsheet uses as column headers,
                              so they appear in the entity editor's pickers.
  nam-vocabulary.ttl         A small extension vocabulary for the handful of NAM/
                              Eternal-specific fields (Record No, storage Location,
                              Received/Accessioned Date, TransferredRecord, and the
                              nam:Accession class) with no home in RiC-O, OAIS, or
                              Dublin Core. See "National Archives of Maldives"
                              below.
  RiC-O_1-1.rdf               NOT included -- optional. Drop a downloaded copy of
                              the real RiC-O 1.1 OWL file here (exact filename) to
                              use it instead of the stub above; see "Is the real
                              RiC-O ontology loaded?" below for where to get it and
                              what changes when you do.
  sample-data.ttl            Example instance data: a small fictional fonds
                              (Bridport Harbour Commissioners) described BOTH
                              natively in RiC-O and as an OAIS preservation
                              description, cross-linked individual-by-individual
  sample-data-science.ttl    A second, independent example in a different domain
                              (a fictional Earth-observation mission's data
                              products) exercising OAIS structure the first
                              example doesn't: the full Submission/Archival/
                              Dissemination Information Package lifecycle, and
                              Representation Information split into its Structure
                              and Semantic facets. Loaded automatically alongside
                              sample-data.ttl on a fresh install (only when the
                              data graph is empty -- see "Updating the ontologies"
                              for what "fresh" means here); harmless to delete if
                              you don't want the extra example data.
  sample-data-pds.ttl        A third example tying the app's theme to a real
                              system: NASA's Planetary Data System (PDS), whose
                              PDS4 architecture is explicitly OAIS-based. Includes
                              one REAL, web-search-verified fact (J. Steven Hughes,
                              PDS's real Chief Information Architect and chief
                              architect of the PDS4 Information Model, also a
                              co-author of the OAIS Reference Model itself) kept
                              strictly separate from an illustrative fictional
                              mission/dataset used for the RiC-O/OAIS structure --
                              see the file's own header for exactly what's real
                              and what's invented. Also loaded automatically on a
                              fresh install, alongside the other two.
```

On first run, `sample-data.ttl` (and `sample-data-science.ttl` and
`sample-data-pds.ttl`, since they're present) are loaded into TDB2's data
graph (a real, disk-backed, transactional store -- see [Storage](#storage)
below), which is then left alone on every subsequent startup: your data
persists across restarts, and
neither bundled file is ever modified after that first load. Delete the
`archive.tdb-location` directory (default `data/tdb2/`) to reset back to the
sample data.

## Running it

Requires JDK 17+ and Maven (or use the wrapper if you generate one with
`mvn -N wrapper:wrapper`).

This app is one module of a larger Maven reactor (see `../README.md`): it
depends on the sibling `oaiscore`, `oais-structure-dfdl` and
`oais-structure-drb` modules (the OAIS core model, and the DFDL and DRB
adapters used by RepInfo Tools) and on DRB itself from `../third-party`, so
install those once from the repository root before running this module on
its own:

```
cd .. && mvn install -DskipTests && cd archive-manager
mvn spring-boot:run
```

Then open http://localhost:9090 (port is set in `application.yml`).

**On Windows, if any of your data is in Dhivehi (or any non-Latin script),
run the packaged jar via `run.bat`** from the project root, not a bare
`java -jar`. It explicitly sets `-Dfile.encoding=UTF-8
-Dsun.jnu.encoding=UTF-8` -- Java 17 predates Java 18's "UTF-8 by default"
change (JEP 400), so on Windows specifically, an unconfigured JVM's default
charset follows the Windows ANSI code page rather than UTF-8, and anything
that doesn't explicitly specify UTF-8 for a given operation silently
substitutes `?` for characters it can't represent (Thaana script has no
representation in that code page at all). `mvn spring-boot:run` during
development isn't affected the same way, since Maven typically launches
the forked JVM with its own encoding already set correctly -- this
specifically matters for the *packaged, standalone jar* on Windows.
Visit `/diagnostics/encoding` on a running instance to check definitively
whether this is (or isn't) affecting your particular setup, rather than
guessing.

Browsing is always open. Creating, editing, or deleting anything requires
logging in with the shared password configured for `archive.edit-password`.
For deployment, keep the password set in the runtime environment or the
server's config, since the app must have the actual value available when it
starts.

### Behind a reverse proxy (HTTPS)

The archive itself speaks plain HTTP. On a network you don't trust, put a
reverse proxy in front of it to provide HTTPS -- otherwise the edit password,
the session cookie, the data, and OpenWebStart launch files all travel
unencrypted. For example, with [Caddy](https://caddyserver.com/), which gets
and renews a Let's Encrypt certificate itself:

```
archive.example.org {
    reverse_proxy 127.0.0.1:9090
}
```

or nginx, with a certificate from Let's Encrypt's `certbot`:

```
location / {
    proxy_pass http://127.0.0.1:9090;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port $server_port;
    client_max_body_size 512m;
}
```

Keep port 9090 closed to the outside. `server.forward-headers-strategy: native`
(set in `application.yml`) makes every link the archive writes -- launch files,
manifests, VOTable and storage locations -- use the address the proxy was
reached at, from its `X-Forwarded-*` headers; Tomcat trusts those only from a
proxy on the same machine or a private network address. Used directly, without
a proxy, it changes nothing (`ForwardedHeadersTest`).

## National Archives of Maldives: Catalogue and Accession Register

Built directly from NAM's own policy documents (Preservation Policy and
Implementation Plan; Preservation Strategic Plan; Records Management Plan),
specifically the "Upload Spreadsheet and Accession Register" section and
the Records Management Plan's transfer workflow -- this isn't a generic
guess at what an archive catalogue needs, it's a real, specified
NAM system:

- **The Bulk Upload Spreadsheet.** NAM's documents specify an exact
  26-column format used to configure bulk uploads to their Eternal digital
  archive (an OAISCloud/Archivematica-based system), accepted here as
  either `.xlsx` or `.csv` (see the dedicated note on which to prefer,
  just below). The columns are Dublin Core's 15 elements (Title, Subject,
  Description, Creator, Publisher, Contributor, Date, Type, Format,
  Identifier, Source, Language, Relation, Coverage, Rights) plus
  preservation/administrative extensions (Record No, Location, Received
  Date, Accessioned Date, Provenance, FixityHashSHA256, FormatInfo,
  Semantics, OtherRI, TransferredRecord). `CatalogueImportService` parses
  either format (Apache Commons CSV for `.csv`, Apache POI for `.xlsx`,
  both feeding the exact same row-processing logic so there's one place
  the column mapping is decided, not two copies to keep in sync;
  case/whitespace-insensitive header matching; "repeatable" columns split
  on `;`, per the spreadsheet's own specification) and maps every column
  onto real RDF: RiC-O where it has a closer-fitting property than a bare
  literal (Title, Creator -> a real Agent, Description, Identifier -> real
  Identifier individuals), the real Dublin Core namespace for the columns
  that literally are DC elements with no RiC-O equivalent, a small `nam:`
  extension vocabulary (`nam-vocabulary.ttl`) for the handful of columns
  with no home anywhere else, and a genuine (if intentionally lightweight)
  OAIS structure -- an ArchivalInformationPackage wrapping a Content
  Information/Information Object and a Preservation Description
  Information populated with whichever of Provenance/Fixity/AccessRights/
  Reference Information the row actually supplied, plus a Representation
  Information split into Structure/Semantic/Other facets if those columns
  were present -- bridged to the record exactly like this project's other
  sample data, so the existing `/oais/{id}` mapping page works on every
  imported record unchanged.
- **Prefer `.xlsx` over `.csv` whenever the source data has non-Latin
  script in it (Dhivehi, for NAM).** Excel's plain "CSV (Comma delimited)"
  export writes the file using the system's ANSI code page, not UTF-8,
  unless "CSV UTF-8 (Comma delimited)" is chosen specifically -- and the
  Windows ANSI code page has no representation for Thaana script at all,
  so that export step silently replaces every Dhivehi character with a
  literal `?` *in the file itself*, before this app ever sees it. That's
  the "lots of question marks instead of Dhivehi script" symptom exactly,
  and it's permanent once it's happened: no amount of correct encoding
  handling on this app's side can recover text a lossy CSV export already
  destroyed -- re-uploading the same corrupted CSV will not help; the fix
  has to use the original `.xlsx` (or a CSV re-exported with the UTF-8
  option specifically chosen), not the already-corrupted file. Reading the
  `.xlsx` directly (`CatalogueImportService.importXlsx`, via Apache POI)
  avoids the lossy step altogether, since XLSX stores text as UTF-8 XML
  internally with no ANSI code page involved anywhere in the path. Only
  modern `.xlsx` is supported, not the legacy binary `.xls` format.
- **The Catalogue** (`/catalogue`) -- public, anonymous, keyword search
  (title/description/subject/type) over every Record/RecordPart/RecordSet,
  paginated. The search term is escaped via `Ns.escapeSparqlLiteral` before
  being embedded in the query -- this is the first genuinely
  anonymous-input search surface in the app, so getting that escaping right
  actually matters, unlike the admin-only forms elsewhere that already sit
  behind a login.
- **Explore** (`/catalogue/explore`) -- for asking more specific questions
  than a keyword search can answer, without needing to know SPARQL or use
  the SPARQL console. Two parts, both public and anonymous:
  - A **filter form** (creator, type, language, subject, coverage, rights,
    date range, transferred-record status, any combination, all optional
    and AND-combined) that builds a SPARQL query behind the scenes
    (`ArchiveService.advancedSearch`) -- the same escaping discipline as
    the plain keyword search applies to every field.
  - **Quick reports** -- records grouped by creator, type, language, and
    year, each a live `GROUP BY`/`COUNT` SPARQL query
    (`ArchiveService.recordsByCreator`/`recordsByType`/`recordsByLanguage`/
    `recordsByYear`), not canned/precomputed examples. "By year" is
    explicitly approximate: it takes the first 4 characters of `dc:date`,
    so it's only meaningful if dates are consistently `YYYY-...`
    formatted, which the spreadsheet's own documentation specifies but
    this app can't enforce on upload -- stated in the UI itself, not just
    here.
- **The Accession Register** (`/accession-register`) -- public, anonymous,
  paginated list of accessions. Per NAM's own definition (quoting TNA): "a
  body of records transferred to an archives service at one time from the
  same source." Modelled as `nam:Accession`, a subclass of `rico:Activity`
  -- one per CSV upload, linking every record that upload created via
  `rico:hasOrganicOrFunctionalProvenance` (the same property this app
  already uses everywhere else for "what activity produced this record").
  Because it's a real Activity subclass, it needs no separate detail-view
  code at all: each entry links straight to the existing `/activities/{id}`
  page, which already shows attributes (depositor, quantity received,
  recording archivist -- all in the `nam:` vocabulary), participants, and
  every record it's the provenance of. Admins (logged in) also get a
  delete button per accession here -- **deliberately a cascading delete**,
  covering the accession plus every record and OAIS-counterpart resource
  that import created (`EditService.deleteAccessionCascade`), not the
  generic single-resource delete used elsewhere in the app. That
  distinction matters in practice: plain delete only removes the accession
  itself, leaving its records (with all their original data) fully intact
  in the catalogue, orphaned but still findable via search or a
  previously-visited link -- exactly the kind of stale data that makes a
  "delete and re-import" testing cycle unreliable, since you can end up
  looking at leftovers from an earlier import without realizing it. There's
  also a **"Delete ALL catalogue import data"** button for clearing
  everything at once -- and it deliberately does *not* work by iterating
  live accessions the way the per-accession button does
  (`EditService.deleteAllCatalogueImportData`, not
  `deleteAllAccessionsCascade`, which still exists as a narrower option):
  once an accession has already been removed by the older, non-cascading
  delete, there's no accession entity left to iterate from, so an
  accession-based bulk delete silently can't reach records from batches
  that were ever cleaned up that way -- a real gap found by testing, not a
  hypothetical. The bulk delete works from IRI structure instead (every
  resource an import creates is named `ex:record-<batchSlug>-...` or
  `ex:<batchSlug>-...`), which survives regardless of whether the
  accession entity itself still exists.
- **Uploading** (`/catalogue/upload`, admin-gated -- same login as
  everything else that writes data) accepts the CSV plus a few
  accession-level fields (description, depositor, recording archivist,
  accession date) not present per-row. No column is treated as mandatory --
  real NAM spreadsheets routinely have several blank cells, and importing a
  record with fewer properties is far more useful than rejecting the row --
  so a blank cell just means that property isn't recorded, not an error.
  "Original Title" is accepted as an alternate header for "Title" (some
  real spreadsheets use that name for the same column NAM's policy
  documents call "Title"). Rows are only skipped, individually and with a
  reported reason (never silently, never failing the whole upload), if
  something genuinely unexpected happens reading them; the accession and
  every successfully-read row still commit together as one transaction
  (see [Storage](#storage)) -- a bad row can leave itself out, but never
  leaves the graph in a half-written state.
- **"Administrators should be able to add extra relationships and
  properties"** -- already true, with no new code needed for it: every
  Catalogue record and every Accession is a normal entity in this app's
  data graph, so the existing, general-purpose entity editor
  (`/entities/{id}/edit`, see above) already works on them exactly as it
  does on anything else -- add a property, add a relationship, change its
  type, all through the same admin-gated UI, with the same ontology-driven
  pickers (now including the `dc:`/`nam:` namespaces alongside RiC-O/OAIS).

**What this deliberately doesn't do**, so it's not a surprise later: it
doesn't implement the Catalogue's separate Department/Series/Subseries/
Piece/Item reference-number hierarchy (that's a different table in NAM's
Records Management Plan, not a column in the Bulk Upload Spreadsheet
itself -- the `rico:hasOrHadConstituent` nesting this app already supports
would model it well, but populating it isn't part of *this* CSV import); it
doesn't call Eternal or Archivematica itself (this app manages the
Catalogue/Accession Register side, not the preservation-system side NAM's
documents describe Eternal handling); and Contributor is stored as a plain
`dc:contributor` literal rather than linked to a real Agent the way Creator
is, for simplicity -- upgrading that is a small, contained change if
wanted.

## What to look at

- **Statistics** (`/statistics`) -- live class/property usage counts across
  the whole data graph (`ArchiveService.typesInUse`/`propertyUsageCounts`),
  not a static list of what the ontologies define. Only classes/properties
  actually in use appear; a property's triple count and its
  distinct-subject count are both shown when they differ, which happens for
  repeatable fields (e.g. `dc:subject`) where one record can contribute
  several triples. Each entity type links through to `/entities?type=...`
  (see below) for the actual individuals. Public, same as the rest of
  browsing.
- **Entity type filter** (`/entities?type=...`) -- a dropdown, populated
  from `ArchiveService.typesInUse()` (the same live usage data driving the
  Statistics page, not a separate hard-coded list), for narrowing the
  generic entity listing to one class at a time -- an exact type match, not
  the `rdfs:subClassOf*` family lookups the rest of this app uses for
  "list all Agents"/"list all Record Resources"/etc., since this is
  deliberately a blunt "what IS this thing" filter rather than an
  ontology-aware one. Reachable directly by picking a type from the
  dropdown, or by clicking through from the Statistics page.
- **Graph highlighting** (`/graph`, `/graph/{id}`) -- three checkbox panels,
  populated from whatever's actually in the currently-loaded graph (not a
  fixed list), for highlighting by entity type, node property, and by
  relationship type independently. Selecting entity types or node properties
  dims every node that doesn't match, and also dims edges where *neither*
  endpoint matches; selecting relationship types dims edges whose property
  isn't selected, independent of node type/property. All three apply at once
  when more than one has selections. Pure client-side (`vis-network`'s own
  `DataSet.update`), no extra requests -- the type/property data was already
  being sent to the browser as JSON for the graph itself, this just uses more
  of it (`GraphNode` gained a `types` field for exactly this).
- **"View this page as a graph"** (`/graph/multi`, `GraphService.subgraph(Collection<String>, int)`)
  -- every listing page (`/entities`, `/agents`, `/records`, `/activities`,
  `/mandates`, `/accession-register`, `/catalogue` search/explore, and the
  SPARQL console's own results) has a button that graphs exactly the items
  currently on screen, one `id` per row, at depth 1 by default. A single BFS
  seeded from every given IRI at once (not one `subgraph()` call per id
  unioned afterward), so a node reachable from more than one focus still
  counts once against the shared `MAX_NODES` budget; `subgraph(String, int)`
  is now a one-element-collection convenience call over the same method. The
  listing templates need no controller changes to get this -- a shared
  `graph-button.html` fragment (`viewAsGraph(ids)`) takes the page's own
  `resultPage.items.![id]` projection directly, a plain Thymeleaf GET form
  (hidden `id` input per item, `depth=1`) rather than a hand-built link, since
  the id list is exactly what a page of listing rows already is. The SPARQL
  console is the one exception that *does* need a controller-side change,
  since its results are arbitrary `Map<String,String>` rows with no `.id`
  field at all: `QueryRunner.uriResourcesIn(Model, String)` re-runs the query
  to collect every distinct `RDFNode` that `isURIResource()` across all
  variables (the already-rendered display rows have lost that distinction --
  see `QueryRunner.render()` -- so reusing them isn't an option), and
  `SparqlController` encodes each into an id the same way every other page
  does.
- **Home page downloads** -- direct links to download `oais-ric-bridge.ttl`,
  `oais_im_schema-sh-v5.ttl` and `oais-im-local-extensions.ttl`
  (`/download/bridge-ontology`, `/download/oais-ontology`,
  `/download/oais-local-extensions`). These stream the exact classpath resource
  bytes the app itself loaded at startup -- not a re-serialization -- so
  what you download is guaranteed to match what's actually running, comments
  and formatting included. Open, no login needed (schema/documentation, not
  archive data). There's no equivalent download link for the RiC-O file
  itself here, since redistributing it isn't this app's place -- get it from
  the source in [Is the real RiC-O ontology loaded?](#is-the-real-ric-o-ontology-loaded).
- **`/diagnostics/encoding`** -- renders a Dhivehi phrase hard-coded
  directly in this app's own Java source (never touched an uploaded file)
  alongside the JVM's actual charset configuration and locale environment
  variables (LANG/LC_ALL/LC_CTYPE), specifically to distinguish "the source
  spreadsheet was already corrupted before upload" from "something in this
  app's own pipeline is at fault" when Dhivehi text shows as question
  marks -- see the internationalisation section for the full explanation
  of both failure modes and why they look identical. Not Windows-specific:
  the underlying cause (a non-UTF-8 JVM default charset) can come from an
  unconfigured locale on Linux/WSL just as easily.
- **`/diagnostics/xlsx-test`** (admin-gated, since it accepts a file
  upload) -- a more targeted companion: upload the actual `.xlsx` giving
  you trouble and see exactly what Apache POI extracts from its first few
  rows, with nothing else involved (no data graph, no storage, no
  rendering). Isolates whether corruption is present the moment the file
  is read versus introduced later in the pipeline, which the hard-coded
  test string on `/diagnostics/encoding` can't tell you, since it never
  goes through file reading at all.
- **Records** (`/records`) -- browse/create `rico:Record`, `rico:RecordPart`,
  `rico:RecordSet` individuals: title, description, creator, constituents,
  instantiations, regulating mandate, provenance activity.
- **Agents** (`/agents`), **Provenance events** (`/activities`),
  **Mandates** (`/mandates`) -- the supporting entities, each with reverse
  lookups (e.g. an agent's page lists what it created).
- **OAIS mapping** (link from any record) -- this is the interesting one.
  It has two parts:
  1. **Class-level correspondences**, read live out of `oais-ric-bridge.ttl`
     via SPARQL (`skos:closeMatch` / `broadMatch` / `narrowMatch` /
     `relatedMatch` plus `bridge:mappingRationale`) for whatever RiC-O
     class(es) the record has. Nothing about *which* OAIS class a RiC-O class
     maps to is hard-coded in Java -- the app just asks the ontology.
  2. **The linked OAIS structural tree**, if the record has a
     `bridge:hasOAISCounterpart` individual in the data graph. The app walks
     it by following any outgoing property in the OAIS namespace
     (`http://ontology.oais.info/im/`) whose object is a resource -- again,
     no property names like "has Data Object" are hard-coded, it is driven
     entirely by the `im:` namespace convention. For the sample record you
     will see: Information Object -> Data Object -> Bit, and Preservation
     Description Information -> Provenance / Context / Fixity / Reference /
     Access Rights Information, with each OAIS node showing its
     `bridge:hasRiCDescription` link(s) back to the RiC-O individual(s) it
     corresponds to -- except Representation Information, which is shown
     with no RiC-O link, illustrating the one deliberate gap the bridge
     documents.
- **Interactive graph** (`/graph` for everything, or "View in graph" from any
  record/agent/activity/mandate/OAIS-node page for a focused view) -- a
  [vis-network](https://visjs.github.io/vis-network/) visualization of the
  data graph, loaded from `GET /api/graph` or `GET /api/graph/{id}?depth=N`
  (plain JSON, reusable outside the UI too). RiC-O nodes are blue, OAIS nodes
  are orange, and edges crossing between the two ontologies (i.e. the
  `bridge:hasOAISCounterpart` / `bridge:hasRiCDescription` links) are drawn as
  dashed red lines, so the bridge is visible directly in the graph rather than
  just in a table. Double-click a node to open its detail page; nodes that
  aren't a Record/Agent/Activity/Mandate (OAIS individuals, Date and Relation
  instances, etc.) fall back to a generic `/resource/{id}` page that also
  exists purely so every graph node is clickable. Depth (1-4 hops) is
  adjustable from the toolbar on a focused view; the full graph has no depth
  limit, so keep an eye on `MAX_NODES` in `GraphService` if you load in a much
  larger archive.
- **Entities** (`/entities`) -- the generic editor. Unlike the other
  sections (which only know about a handful of hard-coded RiC-O classes),
  this works for **any** RiC-O or OAIS class:
  - `/entities/new` creates a new individual of any class. The class picker
    is populated live via SPARQL from the ontology graph -- all 107 RiC-O
    1.1 classes (from the bundled `rico-vocabulary.ttl` stub, see below) and
    every OAIS class (from the full `oais_im_schema-sh-v5.ttl` and its
    local extensions, `oais-im-local-extensions.ttl`) -- plus a
    free-text field for a custom class IRI/prefixed name if you need
    something outside either vocabulary.
  - `/entities/{id}/edit` manages an existing individual's type(s), literal
    properties, and relationships (both outgoing, which you can add here,
    and incoming, shown for context and deletable but added from the other
    end). Property pickers are populated the same ontology-driven way, from
    a curated set of common RiC-O properties (RiC-O's full 480 object +
    75 datatype properties aren't bundled -- see the note in
    `rico-vocabulary.ttl`) and the complete OAIS property set, again with a
    free-text fallback for anything else -- so in practice every relation
    from either vocabulary is reachable, just not all pre-populated as a
    dropdown suggestion.
  - Every write goes straight through `EditService` onto the Jena data
    model via `Resource.addProperty` / `Model.removeAll`. There's no
    explicit save step -- the request is already running inside a TDB2
    write transaction (opened by `TransactionInterceptor` before the
    controller method ran) that commits automatically once the response is
    complete (see [Storage](#storage) below).
  - "Edit" links are wired in from every other detail page (records,
    agents, activities, mandates, the generic resource page, and graph
    double-click via the resource page) so you rarely need to visit
    `/entities` directly except to create something new or browse
    everything at once.
  - **`/entities/import`** -- bulk-add data by pasting complete Turtle
    (including its own `@prefix` lines) rather than building it up one
    field at a time. Pasted content is parsed into a throwaway model first;
    if it doesn't parse, nothing touches the real data graph. The content
    of any of this project's own `rdf/*.ttl` files pastes in directly,
    since they're already complete, self-contained documents -- this is
    the fast way to load `sample-data-science.ttl`, `sample-data-pds.ttl`
    (or a file of your own) into an already-running instance that
    auto-seeding won't touch because its data graph isn't empty anymore.
  - **Property-level bridge correspondences.** Not every bridge mapping is
    at the class level -- e.g. `rico:technicalCharacteristics` has a
    `skos:relatedMatch` to `im:OtherRepresentationInformation` (see
    `oais-ric-bridge.ttl`). These are surfaced two ways in the editor,
    driven by the same generic `ArchiveService.bridgeMappingsFor(iri)`
    lookup (no per-property logic hard-coded): as a hover tooltip on the
    property/relationship picker's `<option>`s (visible before you add
    anything), and as a small inline note under any property/relationship
    the entity already has, if that property happens to have a documented
    correspondence.
- **RepInfo Tools** (`/repinfo-tools`, admin-gated like every other
  create/edit flow; formerly "Format description tools" -- renamed since
  building Representation Information, not just a format description, is the
  point) -- a guided editor for building Kaitai Struct (`.ksy`), DFDL
  (`.dfdl.xsd`), and/or DRB descriptions for a binary/self-describing data
  format, with field-by-field semantic name/definition/units, saved into the
  archive as real `im:RepresentationInformation`.
  - Two starting shapes: **byte-layout** (a format read in file order, like
    FITS or a telemetry stream -- described as a tree, below) or
    **logical-tree** (a self-describing container's group/dataset/attribute
    schema, like HDF5 -- Kaitai/DFDL don't apply here, see below). Built-in
    templates: FITS's primary header required keyword cards with their real
    FITS Standard meanings (`FormatTemplates.fits()`); a worked-example
    **telemetry** stream that uses every structural feature
    (`FormatTemplates.telemetry()`: a packet count, a choice on a coded
    packet type, a length-prefixed message, a checksum present only for
    science packets, scaled values with units and code lists); a **CSV**
    table (`FormatTemplates.csv()`); and a worked-example HDF5
    group/dataset/attribute tree (`FormatTemplates.hdf5()`) -- HDF5 has no
    fixed universal schema to template byte-for-byte, so that one is a
    starting shape to replace, not a standard.
  - **The description model** is engine-neutral and lives in
    `oais-structure-api` (package `info.oais.infomodel.structure.description`),
    so every generator, the sample tests and anything else built on the
    adapters share it. A `FormatDescription` has a root `RecordDescription`
    whose children are:
    - **fields** (`FieldDescription`): a primitive type (8- to 64-bit
      integers, 32/64-bit floats, text, raw bytes), a length for text/bytes,
      and an optional byte-order override;
    - **records** (`RecordDescription`): a named group of elements, which
      can be a **delimited-text** record (`TextLayout`, e.g. one CSV line:
      comma-separated fields ended by a newline);
    - **choices** (`ChoiceDescription`): one of several branch records,
      picked by comparing an earlier field (the discriminator) with each
      branch's key.

    Every element has an **occurrence**: once, optional (present only when a
    condition holds), repeated a computed number of times, or repeated to
    the end of the data (last element only). Lengths, counts and conditions
    are small **expressions** (`Expression`, parsed by `ExpressionParser`):
    references to fields read earlier, integer and string literals,
    `+ - * / %`, comparisons `= != < <= > >=`, and `and`/`or`/`not` -- e.g.
    `message_length`, `sample_pairs * 2`, `packet_type = 2`. A dotted
    reference names a field inside a record read earlier, going down through
    records that occur once -- e.g. `primary_header.data_length + 1`. `Scope`
    works out which fields an expression can see (earlier siblings and the
    enclosing records' earlier fields, read exactly once, and fields inside
    those that are records occurring once) and how far up each engine has
    to navigate to reach them; `EngineSyntax` then renders
    the expression as XPath for DFDL/DRB or as Kaitai's expression language.
    `DescriptionValidator` explains anything that can't be generated (an
    unknown or later field, a repeat to the end that isn't last, a text
    record containing raw bytes, ...) next to the element concerned.
  - **Semantics.** Every element carries `Semantics`: a semantic name
    (what a human calls it, e.g. "Detector temperature" for a field named
    `temperature`), a definition, units (with an optional vocabulary IRI,
    e.g. a QUDT unit), a concept IRI, a **code list** (value = meaning, e.g.
    `1 = housekeeping`), a **scale factor and offset** (physical value = raw
    × scale + offset), a **fill value**, and a **valid range**. These go
    into the generated descriptions as documentation (Kaitai `doc`/`doc-ref`,
    `xs:documentation` in DFDL and DRB, node attributes in drb-python) and
    into the archive as Semantic Representation Information (see *Saving*).
  - **Languages, and what only some of them can do.** A format's details
    include the **languages to generate** (`FormatDefinition.getTargets()`):
    Kaitai Struct, DFDL, Java DRB and drb-python, all four by default. With
    all four, the description is limited to the core every language can
    express. Choosing fewer unlocks the **features** (`Feature`, in
    `oais-structure-api`) that all the chosen languages support:

    | Feature | Languages |
    |---|---|
    | Bit fields (`BITS`, 1 to 64 bits, most significant bit first) | DFDL, Kaitai Struct |
    | Records of a stated size (elements read within it, unused bytes skipped) | DFDL, Kaitai Struct |
    | Nil values in delimited text (e.g. `NA`, or an empty field) | DFDL |
    | Quoted values in delimited text (RFC 4180 style, `""` inside) | DFDL |
    | Number formats in delimited text (e.g. `#,##0.00`, decimal and grouping separators) | DFDL |
    | Elements at an absolute offset (read out of sequence, e.g. an index) | Kaitai Struct |
    | zlib-compressed records | Kaitai Struct |

    The editor only offers a feature's options when every chosen language
    supports it; if the languages change afterwards, `DescriptionValidator`
    flags each element using something one of them can't express, a
    generator asked for such a language refuses
    (`Feature.UnsupportedFeatureException`), and the preview page lists each
    language not generated and why. Choosing Kaitai Struct also rules out
    element names that are Java reserved words (e.g. `class`), since its
    descriptions are compiled to Java.
  - **Opening data in TOPCAT and SPLAT.** Data is found by its Representation
    Information, never by file naming: a *Representation Information
    manifest* (module `oais-structure-manifest`) is a Turtle excerpt of a Data
    Object's Representation Information that names its bits and every
    description explicitly (`im:hasStorageLocation`, relative to the
    manifest or as URLs) -- see the root README's "TOPCAT example description".
    - *Local files:* the preview page's "Open in TOPCAT, SPLAT or an image viewer" downloads a
      zip (`/repinfo-tools/download/viewers?dataFile=...`, `ViewerBundle`):
      the manifest, the DFDL and DRB SDF descriptions (generated or written by
      hand; equivalent alternatives), a table view generated from the element
      tree (the first repeated record in the root as rows, its fields as
      columns, with units and meanings), and a README. Put the data file next
      to the manifest under the name given. Kaitai Struct isn't included:
      TOPCAT needs a class compiled from the `.ksy` in advance, and Kaitai
      shows repeated records as one array, so one table view can't serve it
      and the others.
    - *Archive data:* saving a byte layout now records each description's
      text and language (`im:specificationText`, `im:specificationLanguage`,
      local extensions) instead of burying the text in `rdfs:comment` --
      older saves are converted at startup (`RepInfoGroupMigration`) -- and
      saves the table view as an `im:ViewSpecification` under the Semantic
      Representation Information. A Data Object whose bits have a storage
      location then gets, on its page, a link to its manifest
      (`/api/data-objects/{id}/repinfo.ttl`, each description served at
      `/api/specifications/{id}`), which TOPCAT's reader and SPLAT open by
      URL; its data as VOTable (`/api/data-objects/{id}/votable`,
      `DataObjectViewService`), decoded on the server with the TOPCAT reader's
      own pipeline, with column units and descriptions; and "View in TOPCAT",
      which sends that VOTable to TOPCAT on the viewer's computer over SAMP
      (Web Profile, `static/js/samp-send.js`; TOPCAT asks the viewer to allow
      it). No plugin is needed for VOTable.
    - *Which viewers:* `DataObjectViewService.viewers` follows a Data Object's
      Representation Information network through the triples
      (`im:interpretedUsing`, then its AND/OR groups and
      `im:interpretedUsingRecurse`) and offers each application whose needs
      it meets: a storage location, a DFDL or DRB SDF description the server
      can apply and a table view give TOPCAT (`table.load.votable`); SPLAT
      (`spectrum.load.ssa-generic`, VOTable) also needs the table view's
      columns all numeric, as a spectrum's are. The Data Object's page shows
      a "View with ..." button for each, and so does its right-click menu in
      the graph ("View the data", with its manifest). The data goes to the
      named application only, found through the SAMP hub, since TOPCAT also
      accepts spectra. More viewers are a `Viewer` in that service.
    - *Images:* a byte layout with a repeated record holding a repeated
      number field (rows of pixels) also gets an image view
      (`ViewerBundle.imageView`, saved with `im:viewKind "image"`, and in the
      bundle). Such a Data Object is served as FITS
      (`/api/data-objects/{id}/fits`, `DataObjectViewService.writeFits`,
      module `oais-structure-image`) and offered to DS9 and Aladin
      (`image.load.fits`); Fiji/ImageJ, which has no SAMP, gets "Download as
      FITS" on the graph menu and the FITS link on the page. Help's "Table,
      spectrum or image" section says how each kind is recognised.
    - *Fetching:* serving VOTable makes the server fetch the bits from the
      storage location (`StorageFetcher`): http(s) only, at most
      `archive.fetch.max-bytes` within `archive.fetch.timeout-seconds`, and not
      from loopback or private-network addresses -- checked for every redirect
      -- unless `archive.fetch.allow-private-addresses` is true. Storage
      locations are set by editors but fetched for anyone, so leave that off
      unless every editor may make the server reach its own network.
    - `ViewersTest` downloads a bundle and opens it with the real TOPCAT
      reader, then saves the description, serves a storage location from a
      local web server, and checks the Data Object page, its manifest, a served
      description and its VOTable; and for an image, decodes RepInfo Tools'
      generated descriptions with DFDL and with DRB, and checks the FITS the
      archive serves and the DS9 and Aladin offers.
  - **Describing data whose format is already known** (`/repinfo-tools/known`,
    `KnownFormatController`, "Or describe data whose format is already
    known" on the start page). For a spreadsheet or delimited text -- .xlsx,
    .xls, .ods, .csv (`KnownFormat`) -- whose structure is defined by a
    published specification and which software already reads, only the
    meaning is described: one variable per column, each column found by its
    header on a sheet's header row, with the same semantics as an element
    (semantic name, definition, units, codes, scale and offset, fill value,
    valid range, concept). The sheets and columns can be taken from a sample
    file, and the description tested against one (`SpreadsheetReader`, with
    Apache POI for .xlsx/.xls): each column found, its first values and what
    they mean, and counts of values outside their code list or valid range,
    fill values and blanks. Saving (`FormatDescriptionRdfService.saveKnownFormat`)
    makes an AND group of three: the columns' Semantic Representation
    Information (each column's recording where it is, e.g.
    `Readings!"Air temperature"`); Structure Representation Information --
    a new `im:FormatProfile`, or existing Structure Representation
    Information chosen from the archive; and Other Representation Information
    -- the format's shared OR group of the software that reads it (e.g.
    Microsoft Excel, LibreOffice Calc, or any application that reads OOXML
    spreadsheets), or existing Other Representation Information. A format
    profile refines the format's registry identifier (e.g. `PRONOM fmt/214`)
    with what it leaves out -- the specification's version or conformance
    class, and for text the character encoding, line endings, delimiter and
    quote -- since a registry identifier alone generally isn't enough to read
    a file (PRONOM's `x-fmt/111`, plain text, says nothing about encoding).
  - **EAST**, the CCSDS data description language (CCSDS 644.0-B-3), is
    one of the languages, by the `oais-structure-east` module (see
    `oais-structure-east/README-EAST.md`, also on the Help page):
    - **Generated** from the element tree by `EastWriter`, with each
      element's meaning, units, scaling and codes as comments; tested
      against a sample file with the module's EAST interpreter, which shows
      EAST's own tree (an optional element or a choice is a record of its
      own there, as a variant part comes last in an EAST record);
      downloaded as `.east`, and saved as Structure Representation
      Information whose `im:specificationLanguage` is `EAST`. EAST can't
      describe delimited text, offsets, compression, records of a computed
      size or choices on text, so it isn't generated for those (the CSV
      template leaves it out).
    - **Read in** ("Or read a description in EAST" on the start page,
      `/repinfo-tools/start-east`): the description is kept as written, as
      the draft's EAST (used for the sample test, download and saving
      instead of what's generated), and `EastReader` reads its logical and
      physical packages into the element tree, for the other languages:
      records become records and arrays repeated elements (the first index
      varying fastest, unless `ARRAY_STORAGE` says otherwise); enumerations
      become integers whose codes (from an enumeration representation
      clause, or 0, 1, 2...) have the literals as their meanings; integer and
      real ranges become valid ranges; record representation clauses fix the
      order of components and add the unused space between them as
      `spare_n` fields; a variant part becomes a choice when each
      alternative has one value, and otherwise an optional record per
      alternative (for `|`, ranges, `others`, `null` and true/false
      discriminants); and virtual discriminants are replaced by their actual
      values' expressions, in which an EAST path into a record read earlier
      (`LAST_DATE.DAY`) becomes a dotted reference (`last_date.day`).
      `OCTET_STORAGE` gives the byte order, and a field's physical
      representation its own when its subfields are whole octets in reverse.
      Since a description describes one set of data, the sets repeat to the
      end of the data (in a record `set`) unless an EOF marker ends the last
      variable's repetition. When the element tree can't express a
      description - markers other than EOF, `**` and the EAST functions on
      values from the data, signed or `LOW_ORDER_FIRST` bit fields, integers
      in pieces or not in two's complement, reals in conventions other than
      IEEE 754 - it's kept as EAST only, with a note saying why, and the EAST
      interpreter reads it. A description the interpreter can't use is
      refused with the line and why.
    - **Written by hand** (`/repinfo-tools/hand/east`, "Write EAST" on the
      start page), with worked examples of markers and of VAX reals and
      ones'-complement integers.
  - **Writing a description by hand.** For what the element tree can't
    express -- checksums, encryption, other compression, records spread over
    several lines, anything else a language can do -- a Kaitai Struct, DFDL
    or DRB SDF description can be written by hand (`/repinfo-tools/hand/{kaitai,dfdl,drb}`),
    either from scratch ("Or write a description by hand" on the start page)
    or starting from what the tree generates ("Write or extend it by hand"
    on the preview page). The hand-written text then replaces the generated
    one in the preview, the sample tests, downloads and saving (labelled
    "written by hand"); the tree, if any, still provides the meanings saved
    as Semantic Representation Information, and the preview notes when the
    tree has changed since the text was written. A sample test of a
    hand-written description shows the engine's own tree rather than lining
    it up with the element tree.

    For drb-python, what's written by hand is instead a Python **add-in**
    (`/repinfo-tools/hand/drb-python`, "Write an add-in in Python" in the
    preview's drb-python section): code the generated driver calls, rather
    than a replacement for it. It becomes the driver's `addin.py`, and may
    define any of `prepare(data)` (the file's bytes before the element tree
    reads them: decrypt or decompress them), `check(root)` (after decoding:
    checksums, CRCs; its problems are added to the `checks` add-on's) and
    `metadata(root)` (merged into the `metadata` add-on's result). `root`
    gives `original_bytes`, `decoded_bytes` and the decoded elements. It's
    saved to the archive after the driver module (labelled "with an add-in
    written by hand").

    The page has worked examples (`HandWrittenDescriptions`, files in
    `src/main/resources/repinfo-tools/examples/`), each run on sample data by
    `HandWrittenDescriptionsTest`: Kaitai with a magic number, a range check
    and XOR decryption, and with records of several lines; DFDL with header
    assertions and a header checksum, a gzip-compressed section (Daffodil's
    `fixedLength` and `gzip` layers), and records of several lines; DRB with
    records of several lines; drb-python add-ins checking a CRC-32,
    decompressing xz and bzip2, and decrypting AES-256-CTR (key from the
    server's environment; needs the `cryptography` package), run by
    `DrbPythonSampleRunnerTest`. What each language can't do is said there
    too: neither DFDL nor Kaitai can compute a CRC or decrypt anything but
    XOR/rotations without custom Java code, and DRB's SDF has no checksums,
    encryption or compression.

    **Any FITS file** (FITS Standard 4.0) has a DFDL and a Kaitai Struct
    description among the examples (`dfdl-fits.dfdl.xsd`, `kaitai-fits.ksy`),
    tested by `FitsDescriptionsTest` on files written to the Standard's layout
    and by STIL, and checked on the FITS Support Office's sample files (every
    HDU read, and written back byte for byte with DFDL). They read every HDU:
    the header's keyword records to `END`, filled to a 2880-byte block, and
    the data, sized by the mandatory keywords at their fixed positions
    (`BITPIX`, `NAXISn`, and `PCOUNT`, `GCOUNT` in extensions) as the
    Standard's Eqs. 1 and 2 say, for up to 9 axes: the primary array and
    IMAGE extensions as values of `BITPIX`'s type, a BINTABLE's rows as bytes
    and its heap, a TABLE's rows as text, any other extension as bytes. A
    binary table's columns (`TFORMn`) and random groups (`GROUPS`, `PCOUNT`,
    `GCOUNT`) are given by keywords that can be anywhere in the header, which
    neither language can look up by name (Daffodil's paths take only index
    predicates), so they aren't read; a primary HDU with `NAXIS1 = 0` is
    refused. Kaitai's `to_i` doesn't take the spaces before a right-justified
    integer, so it's read from its first non-space byte. EAST can't express the
    header's fill (it needs a count of the records read), and DRB SDF can't
    read a number from a keyword record. Daffodil reads a large array slowly
    (an element per value: minutes for a million values).

    **A sample test runs the description inside this application**, so
    hand-written text is checked first (`HandWrittenDescriptions.check`) and
    refused if it could reach beyond describing data: a DOCTYPE, or a schema
    included from elsewhere (DFDL may include Daffodil's own built-in schemas,
    `/org/apache/daffodil/...`); Kaitai `meta/imports`, custom `process:`
    routines (which are Java classes; `zlib`, `xor`, `rol` and `ror` are
    built in), `ks-opaque-types`, or `*/` (the compiler copies text into Java
    comments); and DRB queries calling Java (DRB's XQuery calls any static
    Java method through a `java:` namespace) or reading files and URLs
    (`doc()`, `collection()`, `unparsed-text()`, ...), including when spelled
    with character references.

    **A drb-python add-in is Python code, and nothing can make it safe to
    run.** Saving one only parses it (`DrbPythonSampleRunner.checkAddIn`,
    Python's `ast`, which doesn't run it) for syntax errors and the hooks.
    Sample tests run it only if `archive.drb-python.run-hand-written-add-ins`
    (env `ARCHIVE_DRB_PYTHON_RUN_HAND_WRITTEN_ADD_INS`) is `true`; it's
    `false` by default, since it lets anyone with the edit password run code
    on the server as the app's user. When it's off, the add-in can still be
    written, downloaded in the package and saved, and the sample test says
    why it won't run.
  - The definition being built lives in the HTTP session
    (`RepInfoToolController`, a session-scoped `FormatDefinition`), not the
    archive, until you explicitly save it. The editor shows the description
    as a tree (`DescriptionEditorView`); select an element to edit it, and
    add, delete or reorder elements one small POST at a time
    (`/repinfo-tools/elements/add`, `/elements/{id}/update`, `/delete`,
    `/move`), the same pattern as `/entities/{id}/edit`. An expression that
    doesn't parse is explained rather than saved.
  - **Generators** (`KaitaiGenerator`/`DfdlGenerator`/`DrbGenerator`, package
    `service.format`) hand-build their output text the same way every SPARQL
    query elsewhere in this app is built, rather than through a generic
    YAML/XML serializer. Kaitai and DFDL only apply to byte-layout
    definitions -- a logical tree has no sequential byte order for either
    language to describe. DRB has two *unrelated* generated targets, since
    drb-python (https://gitlab.com/drb-python) and the original Java DRB
    (`fr.gael.drb`, reflection-based, matching the sibling
    `oais-structure-drb` module) are different libraries with different APIs.
    - **drb-python** has no schema language: a format is supported by a
      driver package (a `DrbFactory` plus `DrbNode`s, registered through the
      `drb.driver`/`drb.topic` entry points). For a byte-layout definition,
      `DrbGenerator.pythonDriverPackage` therefore generates a real,
      pip-installable driver (downloaded as a `.zip`; `pip install <name>.zip`)
      in the same layout as drb-python's own published drivers. Its module is
      a small generic interpreter (`drb-python/interpreter.py`, shared by
      every driver) plus the description itself as a Python data literal, so
      counts, choices, conditions and text records all work without
      generating format-specific code. Each decoded element is a child node
      whose attributes carry its byte `offset`/`length`, `type`, its
      semantics, and -- where the semantics say -- the code's `meaning`, the
      scaled `physical_value`, or `fill`; its topic
      (`cortex.ttl`) matches the definition's **file extensions** (a new,
      optional field in the editor's details -- the FITS template sets
      `fits, fit, fts`), so drb's own resolver picks the driver
      automatically for those files.

      The package also registers three **drb-python add-ons** (the
      `drb.addon` entry point; `SemanticsAddon`, `MetadataAddon` and
      `ChecksAddon` in the driver module), named after the driver id and
      applying to files of its topic:
      `node.get_impl(list, "<id>_semantics")` lists every value with its
      semantic name, definition, units, code meaning and physical value;
      `node.get_impl(dict, "<id>_metadata")` gives each value by its semantic
      name (else its element path) as meant -- a code's meaning, a physical
      value, `None` for a fill value; and `node.get_impl(list, "<id>_checks")`
      reports values outside their valid range or code list and bytes the
      description doesn't cover. They're also attached when the factory is
      used directly, without drb's resolver.

      User-entered text reaches the Python,
      TOML and Turtle files only as escaped ASCII string literals
      (`DrbGenerator.quotedLiteral`), never as code. A logical-tree
      (HDF5-style) definition still gets a documented schema only -- no HDF5
      driver for drb-python is published on PyPI.
    - **Java DRB** (GAEL's `fr.gael.drb` 2.5.13, LGPL v3, bundled -- see
      `../third-party/README.md`) *does* have a declarative language: for a
      byte-layout definition the output is a **DRB SDF schema** (`.drb.xsd`),
      an XML Schema whose `sdf:block` annotations give each field's
      `sdf:length`, `sdf:byteOrder` (`MSB`/`LSB`) and `sdf:encoding`, counts
      and conditions as `sdf:occurrence` XPath queries, choices as
      `sdf:signature` queries on the discriminator, and text records as
      `sdf:delimiter`s, with the element's semantics as its
      `xs:documentation`. DRB has no raw-bytes type, so a `BYTES` field
      becomes repeated `xs:unsignedByte`. The file works with DRB on its own
      too (e.g. DRB's XQuery `doc("file")/(schema.drb.xsd)root`). A
      logical-tree definition gets a documented reference class only -- DRB
      2.5 has no HDF5 implementation. See `../oais-structure-drb/README-DRB.md`
      for DRB's one known gap here (a CSV file's last line needs its newline).

    The generated
    DFDL includes Daffodil's built-in `GeneralFormat` (the same idiom as
    `oais-structure-dfdl`'s own test schemas): Daffodil refuses to compile a
    schema that leaves properties like `leadingSkip`/`initiatedContent`
    unset, which an earlier version of this generator did.
  - **Test against a sample file** (`POST /repinfo-tools/test-dfdl`,
    `DfdlSampleRunner`) -- on the preview page, upload a sample data file and
    the generated DFDL is run against it by real Apache Daffodil, through the
    sibling `oais-structure-dfdl` module's `DfdlStructureRepInfo` (an
    executable Structure Representation Information), showing the decoded
    element tree or Daffodil's diagnostics. The sample is only held for that
    one request. `DfdlSampleRunnerTest` round-trips the generator's output
    through Daffodil, including the built-in FITS template against a real
    FITS header.

    The drb-python section has the same test (`POST /repinfo-tools/test-drb-python`,
    `DrbPythonSampleRunner`), for byte-layout definitions: the generated
    driver module is run by drb-python itself, in a separate Python process
    with a 60-second limit, loaded straight from a temporary folder (nothing is
    pip-installed) and applied through drb's own file node; the page then
    shows what the `metadata` and `checks` add-ons return. **This needs
    Python 3 with drb-python on the machine running the app**
    (`pip install drb`): the app uses `archive.drb-python.executable`
    (env `ARCHIVE_DRB_PYTHON_EXECUTABLE`) if set, otherwise the first of
    `python3`/`python` that can import drb; without one, the page says so
    instead of offering the upload. `DrbPythonSampleRunnerTest` runs the
    generated drivers through real drb-python when one is available -- set
    `DRB_PYTHON` to its interpreter -- and is skipped otherwise.

    **Writing back.** Each engine's section also has a "Write back" form
    (`POST /repinfo-tools/write-back/{kaitai,dfdl,drb-java,drb-python}`): the
    sample is decoded with that section's description (generated or written
    by hand), written back with it, and compared with the sample
    (`RoundTrip`). Unchanged, identical bytes show the description is enough
    to re-create the file from its values. Changes are `path = value` lines
    (`/packet[2]/temp = 1000`, a value in double quotes keeps its spaces),
    naming elements as the sample test shows them; counts and lengths must be
    changed to match, or the engine refuses. The written file can then be
    downloaded (`GET /repinfo-tools/write-back/download`; kept in the session
    until the next write). DFDL, Kaitai Struct and Java DRB write through the
    adapters' `WritableStructureRepInfo` (Kaitai compiles the `.ksy` again in
    read-write mode, `-w`); drb-python through the driver's `write` add-on,
    which re-encodes the decoded nodes by the description -- text values
    unchanged are written exactly as read -- and, with a hand-written add-in,
    calls its `restore(data)` to undo `prepare()` (e.g. encrypt again; without
    one the result is compared with what `prepare()` produced).
    `GeneratedDescriptionsMatrixTest` writes every reference sample back with
    every engine and expects identical bytes, except where noted: unused bytes
    in a record of a stated size (DFDL, Kaitai write zeros) and a last line
    without its newline (DFDL writes one).

    The Java DRB section has one too (`POST /repinfo-tools/test-drb-java`,
    `DrbSampleRunner`), in-process with no setup needed: the generated SDF
    schema is applied by DRB through the sibling `oais-structure-drb`
    module, and the decoded tree shows each field's byte position (DRB
    reports them). Data shorter than the schema describes is reported as an
    error naming the field. `DrbSampleRunnerTest` round-trips every field
    type in both byte orders, raw bytes, and the FITS template through real
    DRB.

    The Kaitai Struct section has one too (`POST /repinfo-tools/test-kaitai`,
    `KaitaiSampleRunner`), with no setup needed. A `.ksy` isn't read at run
    time, so the runner first compiles it: the **Kaitai Struct compiler is
    bundled** in the jar (under `kaitai-compiler/`, copied there by the
    `maven-dependency-plugin` from Maven Central) and run as a **separate
    Java process** with the app's own `java`. It's GPL-3.0, so it's kept a
    separate program rather than linked into the app; its source jar ships in
    the jar too (see `../third-party/README.md`). The Java it writes is then
    compiled in-process, by the JDK's compiler, or by the bundled Eclipse
    compiler (`ecj`, EPL-2.0) when the app runs on a JRE, and loaded in its own
    class loader. It takes a few seconds; the compiler is stopped after
    `archive.kaitai.timeout-seconds` (default 120). It's compiled with
    `--debug`, so the result shows byte positions. `KaitaiSampleRunnerTest`
    covers both Java compilers.

    Every engine names and nests what it decodes a little differently
    (Kaitai camel-cases names, DRB turns raw bytes into runs of values, some
    engines leave out a choice's branch level). `StructureAligner` (in
    `oais-structure-api`) walks the decoded tree alongside the description
    and lines the two up, so all three tests show the same rows: each
    element's value, its **meaning** from the semantics (a code's meaning,
    the scaled physical value with units, "fill value"), which **branch** a
    choice took, and elements that are **absent** because their condition was
    false. Each test also reports **bytes left over** after the description
    ends (`StructureNode.TRAILING_BYTES`, set by every adapter), since an
    engine that silently stops early would otherwise look like a success.
    `GeneratedDescriptionsMatrixTest` decodes the same samples -- including
    the built-in templates -- with DFDL, Java DRB, drb-python (when
    `DRB_PYTHON` is set) and Kaitai (through the bundled compiler) and checks
    that they agree. Cases using a feature are decoded by the languages that
    support it, after checking that the others' generators refuse it.
  - **Saving** (`FormatDescriptionRdfService`) writes real OAIS structure via
    `EditService`'s existing primitives only: one overall
    `im:SemanticRepresentationInformation` per save, plus one
    `im:RepresentationInformation`/`im:StructureRepresentationInformation`
    pair per format you chose to keep (that class caps Structure/Semantic RI
    at one each, so two formats means two RepresentationInformation
    individuals sharing the one overall Semantic RI) -- linked to an existing
    or newly-created `im:DigitalObject` via `interpretedUsing`. Underneath
    that one overall Semantic RI, every element of the description (field,
    record, choice, branch; or every row of a logical tree) gets its **own**
    `im:SemanticRepresentationInformation` individual, nested to mirror the
    description and linked from its parent's via
    `im:interpretedUsingRecurse` -- the Information Model's own property for
    one Representation Information needing further Representation
    Information to interpret it (figure 4-10), reused here rather than
    inventing a new one. Each carries `rdfs:label` (the semantic name,
    falling back to the element's name), `im:structuralPath` (e.g.
    `packet.body.science.temperature`), `skos:definition`, and
    `im:hasUnitOfMeasurement` to an `im:UnitOfMeasurement` individual
    shared across elements with the same unit string (with `skos:exactMatch`
    to the unit's vocabulary IRI, if given). The rest of the semantics use
    the data-element properties in `oais-im-local-extensions.ttl` (all of
    these are OAIS local extensions, so the Representation Information uses
    nothing from RiC-O or the RiC bridge):
    `im:scaleFactor`, `im:addOffset`, `im:fillValue`,
    `im:validMin`/`validMax`, `im:representsConcept`, and
    `im:hasCodeList` to a `skos:ConceptScheme` whose `skos:Concept`s
    pair each code (`skos:notation`) with its meaning (`skos:prefLabel`). The
    overall Semantic RI's `rdfs:comment` still carries a plain-text summary
    of every element, for a one-glance read without following the links.
    See `FormatDescriptionRdfServiceTest` for the exact shape this produces.
- **Transformation** (`/transform/{id}`, admin-gated; "Transform..." on a
  Data Object's page) -- the second of OAIS's three ways of preserving
  digital information (adding Representation Information, Transformation,
  handing complete AIPs to another archive). A Data Object whose bits have a
  storage location is rewritten in another format that has a DFDL
  description in the archive (any Data Object's Representation Information
  with one):
  1. It's decoded with its own Representation Information (DFDL, DRB SDF or
     Kaitai Struct).
  2. Each element of the new format -- read from its DFDL schema
     (`DfdlSchemaOutline`) -- is made by a rule of a *Transformation
     Mapping*: repeated once per occurrence of an old element
     (`for star in catalogue.entry`), copied with an optional scale and
     offset (`star.ra_rad = catalogue.entry.ra * 0.0174532925`), counted
     (`n = count(catalogue.entry)`), or a constant. The page fills in a
     first guess by matching names and meanings; the mapping can also be
     edited as text.
  3. The result is encoded with the new format's DFDL description
     (Daffodil's unparser, `DfdlStructureRepInfo.encode`), then decoded
     again with it, and each chosen Transformation Information Property --
     the values of an element of the old format -- is checked against the
     new data: physical values (each side's `im:scaleFactor`/`im:addOffset`
     applied) within a tolerance, given or else what the types carry; values
     whose units differ aren't compared. It counts as reversible only if
     every value of the old data is in the new, unchanged.
  4. "Try it" shows all this and offers the new data to download; nothing
     is saved. "Transform" keeps the new bits in the archive's own bit store
     (`archive.bits-location`, served at `/api/bits/{id}/{name}`) and
     records a new `im:DigitalObject` interpreted using the new format's
     Representation Information, in new Content Information and an
     `im:AIPVersion` (with `im:hasSourceAIP` to the old AIP, if any), with
     a new Package Description (`im:describedBy`, `im:derivedFrom` the AIP
     Version) saying what it is and how it was made, and carrying over the
     source package's description. Its PDI has copies of the source AIP's
     Reference, Context and Access Rights Information (new individuals, each
     noting where it was carried over from, so editing one AIP's never
     changes the other's); the Reference Information also names the new
     Content Data Object, and the Context Information says it was made from
     the old one -- each is created with just that if the source has none,
     except Access Rights Information, which can't be made up. The PDI also
     has Fixity Information (SHA-256) and Provenance Information that
     `im:recordsTransformation` an `im:Transformation` (or
     `im:NonReversibleTransformation`): its source and result, the mapping
     it followed (`im:TransformationMapping`, as text), when, and one
     `im:TransformationInformationPropertyCheck` per property. Properties
     the old Information Object didn't have are added to it, and the new
     Content Information gets the same properties for the new format. The
     old Data Object is left as it is. These terms are local extensions to
     the OAIS Information Model (`oais-im-local-extensions.ttl`).
- **Viewing data without installing a viewer** (a Data Object's page) --
  two options beside "View with ..." (SAMP), for those who'd rather not
  download, start and connect TOPCAT or SPLAT themselves:
  - **"Launch TOPCAT" / "Launch SPLAT"** (`/launch/{id}/topcat.jnlp`,
    `/launch/{id}/splat.jnlp`, `LaunchService`): a JNLP file for
    [OpenWebStart](https://openwebstart.com/), the open-source successor to
    Java Web Start (removed from Java in version 11), which the viewer
    installs once. It starts the application with the data loaded, as
    VOTable (`/api/data-objects/{id}/votable`, or `.../data.vot` for SPLAT,
    which goes by the extension), from jars the archive serves under
    `/launch/files/`: TOPCAT's single jar (`archive.launch.topcat-jar`,
    default `../topcat-full.jar`), and, for SPLAT, every jar under a SPLAT-VO
    installation's `lib` folder (`archive.launch.splat-home`), with the
    native libraries in each of `lib`'s subfolders packed into a jar per
    platform (`/launch/files/splat-native/{os}-{arch}.jar`) for JNLP's
    `<nativelib>`. Each is offered only if it's configured and found, for a
    Data Object offered to that application. The jars are served signed
    (`JarSigning`) with the archive's own self-signed certificate: made on
    first use with the JDK's `keytool` (RSA 3072, ten years, code signing)
    and kept in `archive.launch.signing-location` (default `data/launch/`:
    `signing.p12`, its password in `signing.password`); each jar is signed
    once with the JDK's `jdk.security.jarsigner` API, with
    `Permissions: all-permissions` and `Application-Name` added to its
    manifest as OpenWebStart expects, and the signed copy kept in `signed/`
    until the original changes. TOPCAT's jar is signed in the background at
    startup. OpenWebStart asks the viewer to trust the certificate once
    (`/launch/certificate.cer` downloads it, e.g. to check its fingerprint);
    a certificate from a recognised authority would avoid even that. Under a
    JRE without `keytool` the jars are served unsigned. SPLAT's own
    `splat.etc.dir` settings aren't passed, so it starts with its defaults.
  - **"See the image here"**: the archive's own image viewer, in the page
    (`static/js/image-viewer.js`), for any Data Object with an image view.
    The pixels are decoded on the server once
    (`/api/data-objects/{id}/pixels.json`); stretch (linear, square root,
    logarithmic, asinh), display limits, colour map, zoom and panning are
    done in the browser, with each pixel's value and units under the
    cursor. Aladin Lite isn't used because it places images on the sky, and
    refuses one whose FITS has no sky coordinates (WCS) -- which the archive's
    images don't, since Representation Information can't yet describe them.
  - **"See the values here"**: the Data Object's data values printed in the
    page (`static/js/values-viewer.js`), as RepInfo Tools prints a sample's --
    each element's name, kind, value, position in the bits, and meaning --
    for any Data Object whose bits have a storage location and that has a
    structure description the server can use (DFDL, DRB SDF, Kaitai Struct
    with its class, DRB, or EAST, chosen in the page when there are several).
    The meanings come from its Semantic Representation Information
    (`DataObjectViewService.elementSemantics`): the semantic name, a code's
    meaning, a scaled value with its units, a fill value; they're found by
    structural path, or by the saved path whose names appear in order in the
    decoded one, for engines whose trees have extra levels (EAST's). The
    values are served a page at a time
    (`/api/data-objects/{id}/values.json?page=&size=&language=`, at most
    1000 rows a page) by `DecodedValuesService`, which decodes the data once
    and writes its rows to a temporary file with the position of every 64th
    row, so later pages are read straight from the file: neither is a large
    file sent to the browser whole nor decoded again for each page. The rows
    of the last 8 Data Objects are kept for 30 minutes, and dropped when the
    storage location or a structure description changes; at most 5 million
    rows are written. Decoding itself still holds the engine's whole tree in
    memory, as the VOTable and FITS do.
- **Transformation with another application** (the same page, "Or
  transform it with another application") -- when another program does the
  Transformation: download the Data Object's bits
  (`/api/data-objects/{id}/bits`, fetched from its storage location) and its
  data description, transform them there, then upload the result
  (`POST /transform/{id}/external`) with a form saying what was done: the
  application, its version and where to find it; who did it and on which
  day; the method (steps, settings, commands); the Representation
  Information the result is interpreted using (any in the archive, or none
  yet -- the AIP is then incomplete until it's added); whether it is shown
  reversible, and why; and, for each Transformation Information Property,
  the outcome and how it was checked. Where the form says which element of
  the new format a property's values went to and the archive can decode both
  the old and new data, the archive checks the values itself
  (`TransformationService.checkExternal`) and records its own result;
  otherwise it records what was reported, by whom. It is recorded like a
  mapped Transformation -- bits in the bit store, new Data Object, Content
  Information, AIP Version with carried-over PDI, Fixity and a Package
  Description -- with the person and the application as `im:Agent`s
  (`im:performedBy`), the method as the Transformation's `rdfs:comment`, and
  `im:performedAt` as an `xsd:date`. Uploads can be up to 512 MB
  (`spring.servlet.multipart.max-file-size`).
- **Writing out** (open, like browsing) -- what the archive holds, as files
  to use without this application (`PackageExportService`):
  - **A data description**: "Download the data description" on a Data
    Object's page (or any Representation Information's),
    `/api/descriptions/{id}/description.zip`. A zip of `description.ttl` --
    the Representation Information and everything reachable from it through
    OAIS properties, in Turtle -- each description and view specification as
    a file of its own (`descriptions/`, named in the Turtle by an
    `im:hasStorageLocation` relative to it), the OAIS ontologies, and a
    README.
  - **An AIP as a BagIt bag** (RFC 8493): "Download as BagIt" on an
    Archival Information Package's page (or an AIP Version's),
    `/api/packages/{id}/bagit.zip` -- the third OAIS preservation
    technique, handing a complete AIP to another archive. The zip holds one
    bag: `bagit.txt`, `bag-info.txt` (the AIP's IRI and description,
    `Payload-Oxum`, and any Data Objects whose bits have no storage
    location), `manifest-sha256.txt` and `tagmanifest-sha256.txt`, and the
    payload: `data/aip.ttl` (the AIP and everything reachable from it
    through OAIS properties -- Content Information, Data Objects,
    Representation Information, PDI -- except where a Transformation came
    from, an AIP Version's source AIP, and what a package was derived from
    (e.g. its SIP), which are only referred to),
    `data/objects/` (the Data Objects' bits, fetched from their storage
    locations; the export fails rather than write an incomplete AIP if one
    can't be fetched), `data/descriptions/` and `data/ontologies/`. Files
    are named in `aip.ttl` by an `im:hasStorageLocation` relative to it,
    beside the original location. `BagIt` writes the bag.

    "BagIt, referring to the bits" (`?bits=refer`) leaves the Data Objects'
    bits out and lists them in `fetch.txt` (RFC 8493 section 2.2.3) -- the
    URL to fetch each from (its storage location), its length, and its path
    in the bag -- for large data, or bits already kept where the receiver
    can fetch them. They are still in `manifest-sha256.txt` and
    `Payload-Oxum` (the RFC requires it), so the archive still reads each
    once for its digest; the bag is complete, and validates, once the
    receiver has fetched them (e.g. `bagit.py` fetches and validates).

    The bag identifies every component an AIP must have (`AipComponents`,
    from the restrictions in `oais_im_schema-sh-v5.ttl`): exactly one
    Content Information, with its Content Data Object, that Data Object's
    bits and its Representation Information; exactly one PDI, with
    Reference, Provenance, Context, Fixity and Access Rights Information;
    exactly one Packaging Information (`im:delimitedBy`); and at least one
    Package Description (`im:describedBy`). The tag file
    `oais-aip-components.txt` lists each with whether it's present, the
    individuals in `aip.ttl` that are it and the files in the bag that hold
    it; `bag-info.txt` says `OAIS-AIP-Complete: yes` or `no`, with
    `OAIS-AIP-Missing` naming what's missing. An incomplete AIP is still
    written out, so it can be seen and completed. The bag *is* the AIP's
    Packaging Information -- it binds the components together and says how
    to extract them -- so an AIP with none of its own gets an
    `im:PackagingInformation` in `aip.ttl` describing the bag (IRI: the
    AIP's plus `#bagit-packaging`). The same checklist is on the AIP's page.
- **Mapping a packaged AIP to the AIP components** (`/packages/{id}/contents`,
  open; "Map its package to the AIP components" on the page of anything whose
  `im:hasStorageLocation` is a `.7z`, `.zip`, `.tar` or `.tar.gz` file) -- for
  AIPs whose components are inside the package they're stored as rather than
  described in the archive, e.g. AIPs made by Eternal / Archivematica.
  `PackageInspector` fetches and opens the package (7-Zip through Commons
  Compress, which needs XZ for Java -- a dependency -- to read 7-Zip at all),
  finds the BagIt bag and checks it (every payload file's checksum against
  its manifest, unlisted and missing files, `Payload-Oxum`), and finds each
  component OAIS requires of an AIP, saying where in the package it is and
  what it says, as in the NAM-DPP3 validation report's mapping table:
  Packaging Information (the archive file, the bag, its manifests, METS);
  Package Description (the upload spreadsheet, `metadata.csv`; METS Dublin
  Core; `README.html`); the Data Object (the spreadsheet's `filename`);
  Structure (`Format`, `FormatInfo`; the METS PREMIS format with its PRONOM
  identifier), Semantic (`Semantics`, `Language`) and Other (`OtherRI`)
  Representation Information; and the PDI -- Fixity (`FixityHashSHA256`, the
  bag manifest's entry, verified against the file, the PREMIS digest,
  `checksum_audit.xml`), Provenance (`Provenance`, `Creator`, `Publisher`,
  `Contributor` ...; the PREMIS events; the original file's technical
  metadata before normalisation), Context (`Relation`), Reference (the AIP's
  name, `External-Identifier`, the record number and identifier) and Access
  Rights (`Rights`), plus any real values in
  `preservation_description_information.xml`. Spreadsheet columns match
  whether they have the upload spreadsheet's names or Dublin Core ones
  (`dc.rights`, `dc.relation` ...); in older packages that fold the record
  number into `dc.subject`, `metadata_definition.xml` says so and it's found
  there. Each component also has the upload spreadsheet's entries expected
  to hold it: Structure -- `Format`, `FormatInfo`; Semantic -- `Semantics`
  (often just the UUID of a separate AIP holding the Semantic Representation
  Information, which is looked for in this archive and linked when it's
  described here), `Language`; Other -- `OtherRI` (optional, so its absence
  isn't counted as missing); Fixity -- `FixityHashSHA256`; Provenance --
  `Provenance`, `Creator`, `Publisher`, `Contributor`; Context -- `Relation`;
  Access Rights -- `Rights`. Any not filled in is listed under its component,
  even when the component is found elsewhere in the package (e.g. Fixity in
  the bag's manifest), with why: the column is blank; it's defined in
  `metadata_definition.xml` but left out of `metadata.csv` (packages leave out
  blank columns); or the spreadsheet used for that package has no such
  column. The page also lists the spreadsheet's columns with their
  definitions and every file with its checksum; `/packages/{id}/contents.csv`
  gives the mapping as CSV (UTF-8 with a byte-order mark, for Excel).
- **SPARQL console** (`/sparql`) -- run arbitrary SELECT queries against the
  union of the data graph and the ontology graph. Try, for instance:

  ```sparql
  PREFIX rico:   <https://www.ica.org/standards/RiC/ontology#>
  PREFIX im:     <http://ontology.oais.info/im/>
  PREFIX bridge: <https://oais.info/bridge#>
  PREFIX skos:   <http://www.w3.org/2004/02/skos/core#>

  SELECT ?ricoClass ?oaisClass ?rationale WHERE {
    ?ricoClass ?rel ?oaisClass .
    FILTER(?rel IN (skos:closeMatch, skos:broadMatch, skos:relatedMatch))
    FILTER(STRSTARTS(STR(?ricoClass), "https://www.ica.org/standards/RiC/"))
    OPTIONAL { ?ricoClass bridge:mappingRationale ?rationale }
  }
  ```

## Login / editing password

**`application.yml` currently has a real password checked into it**
(`archive.edit-password`), not a placeholder like `changeme` -- worth fixing
before this repo/JAR goes anywhere it might be shared or committed
somewhere visible, since anyone with the source or the built JAR can read
it directly (it's a plain YAML value, not hashed -- there's nothing to
"crack," just read). The app already supports the fix: set
`ARCHIVE_EDIT_PASSWORD` as an environment variable at deploy time instead
of editing the file, and remove the value from `application.yml` (or leave
it as an intentionally-harmless local-dev fallback) -- then the real
password lives only in whatever secret-management your deployment already
has, never in version control or the artifact itself.

A single shared password gates every endpoint that creates, edits, or
deletes data (`/records/new`, `/entities/new`, `/entities/{id}/edit`, and
all the POST endpoints those pages submit to). Everything else -- browsing
records/agents/activities/mandates, the OAIS mapping view, the graph, the
SPARQL console -- stays open with no login at all, on the theory that
reading an archive's description shouldn't require an account, but changing
it should require *something*.

How it works: `EditAuthInterceptor` checks an explicit list of
(path, HTTP method) pairs against a session flag on every request. If a
gated GET is reached while logged out, it redirects to `/login?redirect=...`
and comes back to the original page after a correct password; a gated POST
reached while logged out (not a flow the UI itself produces, since you'd
already have had to get past the GET page it's submitted from) is refused
outright with 403 rather than trying to replay the request after login.

**What this is not**, to be clear about what a "plain password" gate does
and doesn't buy you:

- **One shared secret, not accounts.** Everyone who edits uses the same
  password; there's no per-user identity, so there's nothing to show for
  "who changed what" beyond what's in `rdfs:comment`/notes you add yourself.
- **No rate limiting or lockout.** Nothing stops repeated password guesses
  beyond how fast a browser can submit a form.
- **No CSRF protection.** This app doesn't include Spring Security, so none
  of the POST forms (this feature's own login form included) carry a CSRF
  token. Low-stakes for a single-shared-password tool behind a login wall,
  but worth knowing.
- **No transport security of its own.** The password is submitted as a plain
  form field; without HTTPS in front of it (a reverse proxy is the usual
  answer -- this app doesn't terminate TLS itself), it's readable by anyone
  who can see the network traffic.
- **The session cookie is the only credential that matters after login.**
  Standard Spring Boot session-cookie behavior applies (HttpOnly by default;
  add `server.servlet.session.cookie.secure: true` once you're serving over
  HTTPS).

If you need real access control -- per-user accounts, audit trails, CSRF
protection, rate limiting -- the honest answer is to add Spring Security
rather than extend this further; what's here is intentionally the smallest
thing that could be called a "password login," matching what was asked for.

## Storage

The archive is a real triple store, not an in-memory model with hand-rolled
file persistence: [Apache Jena TDB2](https://jena.apache.org/documentation/tdb2/),
a disk-backed, ACID-transactional RDF database, opened by `RdfStore` at
`archive.tdb-location` (default `data/tdb2/`, resolved to an absolute path
and logged at startup; also shown on the home page).

It holds two named graphs inside that one TDB2 database:

- **The ontology graph** -- the OAIS schema, the bridging ontology, and the
  RiC-O vocabulary stub. Cleared and reloaded from the bundled classpath
  files on *every* startup, so it always matches whatever version of those
  files ships with the running code; there's no risk of a stale copy
  surviving an app upgrade.
- **The data graph** -- the archive's actual instance data. Seeded from
  `sample-data.ttl` only the very first time it's found empty; left alone on
  every later startup, so your edits persist across restarts.

Bits the archive makes itself -- a transformed Data Object's -- are kept as
plain files in `archive.bits-location` (default `data/bits/`), one folder per
file, never changed once written, and served at `/api/bits/{id}/{name}`. That
address is the Data Object's `im:hasStorageLocation`; when the archive itself
needs the bits (to decode them), it reads them from that folder, whatever host
the address names, so moving the archive to another address doesn't break
that -- though viewers given the old address would need the new one. Every
other Data Object's bits stay wherever their storage location says.

**Transactions.** TDB2 requires every read or write to happen inside an
explicit transaction -- there's no auto-commit fallback. Rather than have
every SPARQL call site across `ArchiveService`, `GraphService`,
`OntologyService`, and `EditService` manage its own transaction,
`TransactionInterceptor` opens one for the *whole request* before it reaches
a controller (READ for GET, WRITE for POST/PUT/DELETE/PATCH) and commits it
(or aborts it, if the request failed) once the response -- including view
rendering -- is complete. Everything downstream just calls
`store.dataModel()` / `store.queryModel()` as it always did, and
transparently runs inside whatever transaction is already open on that
thread. The one place a transaction is managed by hand rather than through
that interceptor is `RdfStore.init()` itself, which runs during application
startup, outside any HTTP request.

One real consequence of the method-based READ/WRITE rule: it doesn't
distinguish *which* POST endpoint is being called, so the SPARQL console's
POST (a read-only SELECT) briefly holds TDB2's single write-transaction slot
just like an actual edit would. Harmless at this app's single-user scale;
worth knowing if this ever needs to serve concurrent editors, in which case
that rule is the first thing to make smarter.

## Scale

TDB2 itself is a production-grade store -- tens to hundreds of millions of
triples is routine for it, more with adequate hardware. This app's UI/query
layer is deliberately more conservative than that, on the theory that a web
page or a force-directed graph rendering thousands of rows is a worse
experience long before TDB2 itself would notice the load:

- **Records/Agents/Provenance events/Mandates/Entities** are paginated (50
  per page, capped at 200/page if you override it), with a companion SPARQL
  `COUNT` query backing the "page X of Y" / Previous / Next controls
  (`ArchiveService.pagedSummaries`). Dropdowns that just need "some" options
  rather than the full paginated list (the creator/parent pickers on the
  record-creation form, the relationship-target picker in the entity editor)
  use a capped (200-row) unpaginated query instead of true pagination, since
  a `<select>` doesn't have a natural "next page" affordance; past that cap,
  use the free-text IRI field next to it.
- **The graph.** The focused subgraph view (`/graph/{id}`) was already capped
  at 300 nodes. The full-graph view (`/graph`) now caps at 1,500 triples too
  -- previously unbounded, which was the first thing to actually break at
  scale, since it ships the whole result to the browser as JSON and hands it
  to vis-network's physics simulation. Both views report whether they hit
  their cap via `GraphData.truncated()`, shown as a banner in the UI; the
  focused view's depth selector is the way to stay under it on a large graph
  the full view can no longer show in one go.
- **The SPARQL console** only bounds what you get if you write `LIMIT`
  yourself (the default query does; ones you write are your own).

## Updating the ontologies

`oais_im_schema-sh-v5.ttl`, `oais-im-local-extensions.ttl` and
`oais-ric-bridge.ttl` are plain Turtle files under `src/main/resources/rdf/`.
The schema's header says it's generated from the OAIS Information Model
knowledge base, whose workflow isn't in this repository: keep it in step
with that knowledge base, and put local additions in
`oais-im-local-extensions.ttl` (the knowledge base needs the same changes, or
regenerating the schema would undo them). The other two can be edited
directly. A few things matter more than they might look like they do:

**The ontology graph reloads from these files on every startup** (cleared
first, then re-read -- see [Storage](#storage)), so a plain edit + restart is
enough to pick up a change; there's no separate migration or rebuild step for
the ontology side specifically. If you're running via `mvn spring-boot:run`
from source, Maven refreshes `target/classes` on the next run automatically.

**Validate before you deploy.** These files load during `RdfStore.init()`,
which runs at application startup -- a syntax error in any of them fails the
whole app to start, not just an isolated feature. Check with
`riot --validate path/to/file.ttl` (from Jena's command-line tools) or any
Turtle validator before restarting something that matters.

**Adding a class or property** (new OAIS revision, new correspondence, an
extension you need): just add the triples, following the existing pattern in
each file. Nothing else needs to change --
`OntologyService.listClasses()`/`listObjectProperties()`/`listDatatypeProperties()`
and the `rdfs:subClassOf*` closure queries in `ArchiveService`/`GraphService`
all read the ontology graph generically, so a new class or property is
picked up everywhere it should be (pickers, listings, grouping) with no Java
changes.

**Renaming or removing a class/property is the case that needs care.**
IRIs are exact-match; renaming one breaks every existing reference to the old
IRI. Check, in order:
1. **Within the same file** -- other triples referencing the old IRI (e.g. a
   `rdfs:domain`/`rdfs:range`, or another class's `rdfs:subClassOf`).
2. **`oais-ric-bridge.ttl`**, if you renamed something in the OAIS schema or
   in `rico-vocabulary.ttl` -- every `skos:*Match` triple, and every
   `bridge:hasOAISCounterpart`/`hasRiCDescription` link in `sample-data.ttl`,
   that mentions the old IRI needs updating to the new one.
3. **The one hard-coded reference left in Java**: `OaisController` calls
   `archive.classGapNote("RepresentationInformation")` to show the
   Representation Information gap note specifically. If you ever rename that
   class, this is the one line in the whole app that won't pick the change
   up automatically (everything else about the bridge mapping display is
   ontology-driven). If you add other gaps beyond that one, you don't need to
   touch this at all -- a class with no `skos:*Match` triple already shows as
   "No bridge mapping declared for this class" in the UI on its own;
   `bridge:noCorrespondingClass` is only for attaching an explanation to a
   gap, not what makes the gap visible.
4. **Any data that already references the old IRI.** The *data* graph, unlike
   the ontology graph, is **not** cleared and reloaded on restart (see
   [Storage](#storage)) -- it's seeded once and then left alone. If you
   rename a class after your archive already has individuals typed with the
   old IRI, those individuals keep the old (now-undeclared) type: they won't
   silently disappear, but anything that depends on `rdfs:subClassOf*`
   closure (e.g. `listAgents()`) will stop matching them, since there's no
   longer a subclass chain connecting the old IRI to the anchor class. For a
   handful of affected individuals, fix them up through the entity editor
   (remove the old type, add the new one). For many at once: `/entities/import`
   (see below) bulk-*adds* triples from pasted Turtle, but doesn't help with
   bulk rename/delete -- for that you're outside what this app's own UI
   supports -- the SPARQL console is SELECT-only, no UPDATE -- so you'd need
   an external tool talking to the TDB2 database directly (e.g. Jena's
   `tdb2.tdbupdate` command-line tool, run while the app isn't running so
   nothing else is holding a write transaction), or
   scripted use of `EditService`-equivalent SPARQL UPDATE if you add that
   capability. Ask if you want that added -- it wasn't built here since it
   wasn't asked for and a bulk-rename tool is a meaningfully different (and
   riskier) thing than the single-entity edits the rest of the app does.

**`rico-vocabulary.ttl` specifically** needs the same care as the OAIS
schema, plus one more thing: it's already a partial, best-effort stub (see
its own header comment and [Is the real RiC-O ontology
loaded?](#is-the-real-ric-o-ontology-loaded) below) -- extending its
`rdfs:subClassOf` hierarchy is the main reason you'd edit it, and doing so
directly improves what `listAgents()`/`listRecordResources()`/etc. and the
graph's node-grouping can see, with no other code changes.

## Is the real RiC-O ontology loaded?

**Yes, as of this version -- `RiC-O_1-1.rdf` is bundled directly in
`src/main/resources/rdf/`.**

The official OWL 2 file (also mirrored at `ICA-EGAD/RiC-O` on GitHub,
raw file at
https://raw.githubusercontent.com/ICA-EGAD/RiC-O/master/ontology/current-version/RiC-O_1-1.rdf,
CC BY 4.0) was provided directly rather than fetched -- worth recording,
since an earlier attempt to fetch it via URL truncated at a fixed size
regardless of how large a token limit was requested, so a chat-mediated
fetch genuinely can't retrieve a file this size; having the actual file
available sidestepped that entirely. Its own metadata confirms 105-107
classes (counting conventions vary slightly) and on the order of 480 object
properties and 75-76 datatype properties, each with English/French/Spanish
labels (classes also German), most with `rdfs:comment` definitions, many
with `skos:scopeNote` / `skos:example` / `skos:changeNote`. It's RDF/XML,
not Turtle, and it's 1.7MB.

**How it's loaded**: `RdfStore.loadRicoVocabulary()` checks for a file at
exactly `src/main/resources/rdf/RiC-O_1-1.rdf` on every startup: since it's
now present, it loads the real ontology (`Lang.RDFXML`) instead of the
bundled `rico-vocabulary.ttl` stub. If you ever remove that file, the app
falls back to the stub automatically -- nothing else needs to change either
way. Its namespace
(`https://www.ica.org/standards/RiC/ontology#`) is exactly the `rico:`
namespace this app already uses everywhere, so there's no mismatch to
reconcile.

What changed once it was loaded:
- **Classes**: counted precisely against the actual file this time: 105
  `owl:Class` declarations (the "107" figure earlier in this section came
  from web search rather than the file itself; take 105 as the accurate
  count). The stub already had all of them as bare class+label
  declarations, so the practical difference is everything below, not the
  class list itself.
- **Properties**: complete instead of the ~110-property curated subset --
  all 480 object properties and 75 datatype properties become dropdown
  suggestions in the entity editor, not just the common ones. One wrinkle:
  the real file also declares 48 `*_role` "rolification" object properties
  (OWL2 modeling plumbing that lets the n-ary Relation classes be queried
  more easily -- not something an archivist would normally set by hand)
  under the same `rico:` namespace, so they'll appear as dropdown options
  too. Filtering those out specifically wasn't built, since it wasn't asked
  for and is easy to add later (e.g. exclude property local names ending in
  `_role`) if the extra entries in the picker turn out to be annoying rather
  than just occasionally ignorable.
- **Hierarchy**: complete instead of 4 branches -- every `rdfs:subClassOf`
  relationship RiC-O actually defines is present, so the
  `rdfs:subClassOf*` closure queries in `ArchiveService`/`GraphService`
  (see "Ontology-driven, not hard-coded" below) become accurate to the
  whole ontology, not just the Agent/RecordResource/Event/Rule branches the
  stub covers -- including, notably, `rico:Relation`'s roughly 90
  subclasses.
- **Still no OWL reasoning.** Loading the real file's asserted
  `rdfs:subClassOf` triples doesn't add inference -- domain/range
  constraints, disjointness, property chain axioms (RiC-O uses these for
  its "shortcut" properties, e.g. `hasOrganicProvenance`) are all present in
  the file as data but nothing evaluates them; the app still only ever
  walks explicit `rdfs:subClassOf*` paths in its own SPARQL, same as with
  the stub.
- **Startup will be measurably slower.** The ontology graph is cleared and
  reloaded from every bundled file on each startup (see
  [Storage](#storage)/[Updating the ontologies](#updating-the-ontologies)),
  which is fine for the small stub but means re-parsing a multi-megabyte
  RDF/XML file every time you restart. Not benchmarked here (nothing in
  this project has been run, let alone timed), but if it becomes
  noticeable, the fix is to stop calling `ontology.removeAll()` ahead of
  loading this specific file and instead load it once, the same way the
  *data* graph is seeded once and left alone -- trading "always fresh from
  a re-downloaded file on restart" for "fast restart."

## Language

Classes and properties can carry labels in more than one language --
concretely, the real RiC-O ontology (see above) labels every class and
property in English, French, and Spanish, and every class additionally in
German. Without language handling, a class with four labels would show up
as four duplicate entries in the class picker; instead, `OntologyService`
groups labels by resource and `LabelPicker` picks one per resource,
preferring (in order): an exact match for the current language, then
English, then an untagged label, then whatever's available.

The current language is a per-**session** preference (`LanguagePreference`,
session-key `preferredLanguage`, default English), switchable from a small
EN / FR / ES / DE / DV control in the top nav (`GET /language/{lang}`, a
plain link-driven GET since it only changes a display preference, not
archive data, redirecting back via the Referer header rather than a
passed-through URL parameter). It's read via `RequestContextHolder` rather
than passed as a method parameter, so it can reach label-resolution code
(`ArchiveService.label()`, `OntologyService.listClasses()`/
`listObjectProperties()`/`listDatatypeProperties()`) without threading a
language argument through every call site that might resolve a label --
which also means, same lesson as the transaction-per-request fix, that
anything running outside an HTTP request (startup-time ontology queries in
particular) has no session to read and falls back to English rather than
throwing.

This only affects **labels** -- the dropdown's underlying `rico:`/`im:`
IRIs and the bundled OAIS/stub schema (which only ever has untagged English
labels) are unaffected either way; the language switcher only matters once
the real, multilingual RiC-O file is loaded. **Dhivehi (DV) is listed but
currently inert**: the mechanism is fully generic (any language code
works, not a hard-coded set), but none of this project's bundled ontology
files have an `@dv`-tagged `rdfs:label` yet -- accurately translating
~150 ontology/technical terms needs a qualified native-speaker translator,
not something to fabricate here. Selecting it today just falls back to
English rather than erroring; the moment real `rdfs:label ...@dv` triples
exist anywhere in the ontology graph, it starts working immediately, no
code changes needed. This is entirely separate from whether *catalogue
data itself* can be in Dhivehi, covered next -- it already fully can.

## Internationalisation and Dhivehi (Thaana script) support

Distinct from the ontology-label language switcher above, this is about
the actual *content* NAM staff and the public will read and enter --
catalogue titles, descriptions, names -- which routinely will be in
Dhivehi, written in Thaana script (Unicode block U+0780-U+07BF,
right-to-left). Four concrete things make that work, none of them a token
gesture:

- **Encoding is UTF-8 end to end, explicitly, not just by relying on
  defaults.** `server.servlet.encoding` (force=true, so both request and
  response bodies are UTF-8 regardless of what a client's headers claim)
  and `spring.thymeleaf.encoding` are set explicitly in `application.yml`;
  the CSV importer reads files as UTF-8 (`CatalogueImportService`,
  `new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)`);
  every page already had `<meta charset="UTF-8">`. RDF literals themselves
  need no special handling -- Jena stores and serializes Unicode text
  natively, this was never a triple-store-level concern. **This only
  covers encoding once data reaches this app** -- it can't fix a source
  file that's already corrupted before upload, which is exactly what a
  plain (non-UTF-8) Excel CSV export does to Thaana script; see "Prefer
  .xlsx over .csv" in the Catalogue/Accession Register section above for
  why `.xlsx` import exists specifically to avoid that.
- **A Thaana-capable font.** Not every system font covers Thaana, so
  relying purely on the OS default is a gamble; Google's "Noto Sans
  Thaana" is loaded (`fragments.html`, alongside the existing
  vis-network CDN dependency -- same offline caveat applies, see the graph
  view's own note) and appended to the CSS font stack. Font fallback in
  CSS happens per-*character*, not per-element, so this changes nothing
  visually for English content and only engages for the specific
  characters Thaana needs.
- **Right-to-left rendering, automatically, per element.** Thaana is
  RTL; this catalogue mixes English and Dhivehi content record by record,
  so a single fixed page direction would be wrong -- what's needed is each
  piece of text figuring out its own direction from its own content, which
  is exactly what `unicode-bidi: plaintext` does (the same standard
  Unicode Bidi Algorithm auto-detection as the HTML `dir="auto"`
  attribute, just applied once via CSS across headings, table cells,
  pills, form inputs, and textareas, rather than needing `dir="auto"`
  added to dozens of templates individually with the attendant risk of
  missing some). Again, no visible change for English-only content --
  it only engages when text actually contains strong-RTL characters.
- **Search already works correctly on Thaana text without any change.**
  `searchCatalogue()`'s case-insensitive matching uses SPARQL's `LCASE()`;
  Thaana (like Arabic and many other scripts) has no case distinction at
  all, so `LCASE()` on Dhivehi text is a harmless no-op and plain substring
  matching (`CONTAINS`) works correctly on the raw characters -- verified
  by reasoning through Unicode's case-folding rules rather than assumed.

### Still seeing question marks instead of Dhivehi script?

Two genuinely different problems produce the exact same symptom, and
telling them apart matters because only one of them is fixable after the
fact:

1. **The source file was already corrupted before this app read it.**
   Once a lossy, non-UTF-8 export has replaced Thaana characters with
   literal `?`, that's permanent -- no amount of correct handling on this
   app's side can recover text that's already gone. Re-uploading the same
   corrupted file will not help. If you generated an `.xlsx` *from* an
   already-corrupted `.csv` (rather than from the original pristine
   source), the `.xlsx` carries the same corruption forward.
2. **The JVM's own default charset isn't UTF-8** (Java 17 and earlier only
   -- Java 18+'s JEP 400 makes UTF-8 the default everywhere regardless of
   locale, so this cause doesn't apply at all once you're on 18+), so
   something in the app's pipeline that doesn't explicitly specify a
   charset falls back to the platform default and substitutes `?` there
   instead. `/diagnostics/encoding` checks the JVM version and actual
   charset/locale configuration directly rather than assuming based on OS
   or the project's compile target -- confirm what's actually running
   before concluding this is (or isn't) the cause; a project built against
   Java 17 can still be *run* on a newer JRE, and only the runtime version
   matters here, not the compile target in `pom.xml`.

**`/diagnostics/encoding`** exists specifically to tell these apart rather
than guessing: it renders a Dhivehi phrase that's hard-coded directly in
this app's own Java source (so it never touched any uploaded file) and
reports the JVM's actual charset configuration. If that hard-coded string
also renders as question marks, the problem is (2) -- the JVM -- and fixable
by launching with `run.bat`. If it renders correctly, the app's own
pipeline is fine and the problem is (1) -- something already wrong in the
specific source file you uploaded -- and the fix is to go back to the
original, pristine source rather than a file that's already passed through
a lossy CSV export at some point.

One thing worth being precise about, since it's an easy but incorrect
inference: seeing *some* Dhivehi render correctly and *some* show as `?`
within the same mixed English/Dhivehi string doesn't mean "mixing the
scripts confuses the system" as a mechanism -- a non-UTF-8 encoder (in
either failure mode above) replaces *every* Thaana character it encounters
with `?` while leaving ASCII/English characters in the very same string
completely untouched, because the problem encoding usually can represent
ASCII fine and simply has no Thaana glyphs at all. A mixed string showing
partial corruption and a pure-Dhivehi string showing total corruption are
the same underlying bug, not two different ones -- the mixing just makes
the pattern more visible side by side.

**What this app cannot do anything about**: if problem (1) is what
happened, the data is gone from that specific file. There's no recovery
step to offer here beyond re-sourcing the original file, and it would be
dishonest to imply otherwise.

#### A confirmed third cause, found by direct inspection of a real NAM file

For one specific report against a real file (`1__Manuscripts_President_s_Office.xlsx`,
record R00098), the source file was directly inspected two independent
ways -- parsing the raw XML inside the `.xlsx` (it's a ZIP archive of XML
parts) by hand, and separately loading it with a completely different
library (`openpyxl`, unrelated to Apache POI's code path). Both agreed: the
Dhivehi text for that record (Title, Description) was 100% intact in the
source, zero `?` characters anywhere in it. That ruled out cause (1) for
this file conclusively, not just by assumption.

That pointed at this app's own XLSX-reading code specifically. Looking at
it again with that in mind: `CatalogueImportService.importXlsx` and
`DiagnosticsController.xlsxTest` were both routing *every* cell, string
content included, through `DataFormatter.formatCellValue()` -- a POI class
whose actual job is turning a raw numeric or date cell value into the
formatted text Excel would display (honoring currency symbols, decimal
places, date patterns, and so on). For genuine text content -- the
overwhelming majority of archival metadata -- that's the wrong tool: POI's
direct `Cell.getStringCellValue()` is the simpler, more appropriate API,
and it's what both are now changed to use for string-typed cells
specifically, falling back to `DataFormatter` only for the numeric/date/
formula cells that actually need its formatting logic (so a numeric-
looking Record No like "00042" still reads back correctly rather than as
the number 42).

This is a genuine, targeted fix to a real code path, not just another
diagnostic -- but it's stated carefully rather than declared as *the*
confirmed root cause, since there was no way to run the actual Java/POI
code in the environment that built this fix to verify it against the real
file before delivering it. If question marks persist after this change,
`/diagnostics/xlsx-test` (now using the same corrected code path) against
the actual problem file remains the next concrete step, and would be worth
reporting back either way -- confirmation the fix worked is as useful to
know as a sign it wasn't the whole story.

One unrelated thing worth knowing about, found during the same
inspection: the same file has roughly 19 shared strings (out of over
7,000) containing a literal `?`, but every one of them found was in
Latin-script transliterated text (e.g. "Boaga?", "Buenos Aires?"), not
Dhivehi -- almost certainly a pre-existing, minor data-entry or earlier-
digitization quirk in the original spreadsheet, unrelated to the Thaana
issue and not something this app introduced or can safely auto-correct
without knowing what character was actually intended.

**What this doesn't cover**, stated plainly: the UI chrome itself (nav
labels, button text, page headings like "Catalogue"/"Upload"/"Search") is
still English-only. Translating that is a real, separate undertaking
(Spring's `MessageSource`/resource-bundle mechanism is the standard way to
do it) that would need actual Dhivehi translations for every UI string,
which -- same reasoning as the ontology labels above -- isn't something to
fabricate without a qualified translator. Nor does the page layout itself
flip to RTL (nav bar order, table column order) -- only the *text content*
within it does. Both are legitimate follow-on work if wanted, just outside
what "the data can be in Dhivehi and renders/searches correctly" required.

## Design notes / known limitations

- **Ontology-driven, not hard-coded, class/property enumeration.** Nothing
  in the Java code hard-codes "which RiC-O classes count as an Agent" or
  similar. `listAgents()`, `listRecordResources()`, `listActivities()`, and
  `listMandates()` in `ArchiveService` all run a
  `?type rdfs:subClassOf* rico:SomeTopClass` SPARQL query against the
  ontology graph instead of matching a fixed list of leaf class names; the
  same subclass-closure query backs `GraphService`'s node grouping/routing.
  This depends on `rdfs:subClassOf` triples actually being present in the
  ontology graph for the branches being queried (Agent, RecordResource,
  Event, Rule) -- see the note in `rico-vocabulary.ttl` for how much of
  RiC-O's real hierarchy that stub does and doesn't reconstruct (short
  version: just those four branches, best-effort, not verified against the
  authoritative OWL file). `EntityController`'s class and property pickers
  were already fully ontology-driven before this (see `OntologyService`).
- **IDs** are the URL-safe-base64 encoding of the full IRI (`IdCodec`), so
  any resource from any namespace is directly linkable without a separate
  ID-minting scheme.
- The bridging ontology intentionally avoids `owl:equivalentClass` between
  RiC-O and OAIS classes -- see the comments at the top of
  `oais-ric-bridge.ttl` for why.
- **The graph view loads `vis-network` from a CDN** (`unpkg.com`), so it
  needs internet access in the browser. If you're running somewhere offline,
  download `vis-network.min.js` and change the `<script src="...">` in
  `templates/graph/view.html` to point at a local copy under `static/js/`.
- **What RepInfo Tools' description model leaves out.** Its core covers
  what all four engines can express the same way: sequences, counts,
  lengths and conditions computed from earlier fields, choices on a
  discriminator, and simple delimited text. Bit fields, records of a stated
  size, nil values, quoted text, number formats, absolute offsets and zlib
  compression are there for the languages that support them (see
  *Languages, and what only some of them can do*). Checksums, encryption,
  other compression schemes and records spread over several lines aren't in
  the model; describe them by hand in the engine's own language, from
  scratch or by extending what the model generates (see *Writing a
  description by hand*).

