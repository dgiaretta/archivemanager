# Example: a binary star catalogue in TOPCAT

`bright-stars.bin` is a small binary catalogue of 15 bright stars: one 36-byte
big-endian record per star, to the end of the file -- a 12-character name,
then right ascension and declination (float64, degrees), V magnitude
(float32) and parallax (float32, milliarcseconds). TOPCAT can't read that
format itself. Here it reads it through the data's OAIS Representation
Information.

| File | What it is |
|---|---|
| `bright-star-catalogue.ttl` | The **Representation Information manifest**: a Turtle excerpt naming the data file, its structure descriptions (DFDL or DRB SDF -- equivalent alternatives, either will do), the table view, and what each field means. Every file is named explicitly; rename or move them by editing it. |
| `bright-star-catalogue.dfdl.xsd` | The structure as a DFDL schema. |
| `bright-star-catalogue.drb.xsd` | The same structure as a DRB SDF schema. |
| `bright-star-catalogue-table-view.xml` | How to show the decoded data as a table: one row per `star` record, its fields as columns, with units, descriptions and IVOA UCDs. |
| `bright-stars.bin` | The data. |

The manifest, the two schemas and the table view were made with archive-manager's
RepInfo Tools, not by hand: a blank byte layout with a `star` record repeated
to the end of the file, its five fields given types, units and definitions, then
**Preview → Open in TOPCAT or SPLAT** with the data file name
`bright-stars.bin`. Only the UCDs in the table view were added afterwards, so
TOPCAT recognises the sky coordinates for its sky plots.

## Installing

Once, from the repository root:

1. Download TOPCAT's standalone jar into the repository root (it's in
   `.gitignore`):

       curl -L -o topcat-full.jar https://www.star.bris.ac.uk/~mbt/topcat/topcat-full.jar

2. Build the TOPCAT reader and collect its dependencies next to it (TOPCAT
   brings its own STIL, so that's left out):

       mvn -pl oais-structure-topcat -am install -DskipTests
       mvn -pl oais-structure-topcat dependency:copy-dependencies -DincludeScope=runtime -DexcludeArtifactIds=stil

TOPCAT needs Java 17 or later: `java -version`, or set `JAVA17_HOME`.

## Running it

    examples\topcat\open-in-topcat.bat        (Windows)
    examples/topcat/open-in-topcat.sh         (Linux, macOS)

TOPCAT opens with the catalogue loaded: 15 rows, each column with its units
and description (Views → Column Info). Try Graphics → Sky Plot for the stars'
positions, or Graphics → Plane Plot of `vmag` against `parallax`.

Either script also takes another manifest, as a file or a URL -- for example a
Data Object's manifest from the archive,
`http://localhost:9090/api/data-objects/<id>/repinfo.ttl` -- with `#name` after
it to choose one of several Data Objects.

## Putting it in an archive

`publish-to-archive.sh` puts the catalogue into an archive-manager archive --
yours, or any other -- as a Data Object with its Representation Information,
so it can be opened from the archive rather than from these files:

    ./publish-to-archive.sh https://your-archive.example.org
    ./publish-to-archive.sh https://your-archive.example.org https://your-archive.example.org/data/bright-stars.bin

It builds the same description in the archive's RepInfo Tools, saves it with
its DFDL and DRB SDF descriptions as a new Data Object, labels it, and gives it
the data's storage location: by default the copy of `bright-stars.bin` in this
project's GitHub repository, or any public http(s) address you give. (The
archive fetches the data itself, and won't fetch from its own or a private
network's addresses.) It reads the edit password from `ARCHIVE_EDIT_PASSWORD`
or asks for it, checks that the archive can decode the data, and prints the Data
Object's page, manifest and VOTable addresses. It runs anywhere with `sh` and
`curl`: Linux, macOS, or Git Bash on Windows.

The archive needs the version of archive-manager with TOPCAT and SPLAT support.
Then, with TOPCAT running on your computer, the Data Object's page -- or its
right-click menu in the graph -- has "View with TOPCAT"; or load its VOTable
address in TOPCAT (File → Load), with no plugin at all.

Without the GUI, STILTS (inside the same jar) reads it the same way:

    java -Dstartable.readers=info.oais.infomodel.structure.topcat.OaisStructureTableBuilder \
         -cp "topcat-full.jar:oais-structure-topcat/target/oais-structure-topcat-0.0.1-SNAPSHOT.jar:oais-structure-topcat/target/dependency/*" \
         uk.ac.starlink.ttools.Stilts tpipe ifmt=OAIS-RepInfo in=examples/topcat/bright-star-catalogue.ttl omode=meta

(on Windows, `;` between the classpath entries).
