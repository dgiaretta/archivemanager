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

Without the GUI, STILTS (inside the same jar) reads it the same way:

    java -Dstartable.readers=info.oais.infomodel.structure.topcat.OaisStructureTableBuilder \
         -cp "topcat-full.jar:oais-structure-topcat/target/oais-structure-topcat-0.0.1-SNAPSHOT.jar:oais-structure-topcat/target/dependency/*" \
         uk.ac.starlink.ttools.Stilts tpipe ifmt=OAIS-RepInfo in=examples/topcat/bright-star-catalogue.ttl omode=meta

(on Windows, `;` between the classpath entries).
