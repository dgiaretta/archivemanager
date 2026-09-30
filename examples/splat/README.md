# Example: a binary spectrum in SPLAT

`a0v-spectrum.bin` is a small binary spectrum of an A0 V star, of the kind
Vega is: a 16-character object name, the number of points (uint32), then that
many 16-byte little-endian records -- wavelength (float64, Angstrom), flux
density and its one-sigma error (float32, erg/cm2/s/Angstrom). There are 601
points, 3800 to 6800 Angstrom in steps of 5. SPLAT can't read that format
itself. Here it reads it through the data's OAIS Representation Information.

The spectrum is synthetic, not an observation: a 9600 K blackbody scaled to
Vega's flux near 5556 Angstrom, with the Balmer lines (H-alpha to H8), Ca II K
and Mg II 4481 as absorption lines, and 1% noise.

| File | What it is |
|---|---|
| `a0-v-star-spectrum.ttl` | The **Representation Information manifest**: a Turtle excerpt naming the data file, its structure descriptions (DFDL or DRB SDF -- equivalent alternatives, either will do), the table view, and what each element means. Every file is named explicitly; rename or move them by editing it. |
| `a0-v-star-spectrum.dfdl.xsd` | The structure as a DFDL schema. |
| `a0-v-star-spectrum.drb.xsd` | The same structure as a DRB SDF schema. |
| `a0-v-star-spectrum-table-view.xml` | How to show the decoded data as a table: one row per `point` record, its fields as columns, with units and descriptions. |
| `a0v-spectrum.bin` | The data. |

The manifest, the two schemas and the table view were made with archive-manager's
RepInfo Tools, not by hand, and are used unchanged: a blank little-endian byte
layout with two fields, `object` and `n_points`, then a `point` record repeated
`n_points` times, its three fields given types, units and definitions, then
**Preview → Open in TOPCAT or SPLAT** with the data file name
`a0v-spectrum.bin`.

Unlike the [TOPCAT example](../topcat/README.md), whose records simply run to
the end of the file, the number of records here is read from the data itself.
The header fields aren't part of the table: the table view's rows are the
`point` records.

SPLAT needs every column of a spectrum to be numeric, and works out which is
which from the column names: `wavelength` becomes the spectral axis, `flux` the
data and `error` its error bars, each with its units.

## Installing

Once:

1. Install **SPLAT-VO**, the current SPLAT release, from
   <https://github.com/mmpcn/splat-build/releases> (see
   <https://www.g-vo.org/pmwiki/About/SPLAT>): download `splat-vo-<version>.jar`
   and run it with `java -jar splat-vo-<version>.jar`. Note the folder you
   install it in -- the one holding `bin`, `etc` and `lib` -- and set
   `SPLAT_HOME` to it:

       set SPLAT_HOME=C:\path\to\splat-vo            (Windows)
       export SPLAT_HOME=/path/to/splat-vo           (Linux, macOS)

   Without `SPLAT_HOME` the scripts look for a `splat-vo` folder in your home
   folder and a few usual places (Program Files; `/usr/local`, `/opt`,
   `/Applications`).

2. Build the manifest reader, as for the [TOPCAT example](../topcat/README.md),
   and collect its dependencies next to it. SPLAT brings its own STIL, so
   that's left out:

       mvn -pl oais-structure-topcat -am install -DskipTests
       mvn -pl oais-structure-topcat dependency:copy-dependencies -DincludeScope=runtime -DexcludeArtifactIds=stil

3. Build the SPLAT launcher, `oais-structure-splat`. SPLAT isn't published as a
   library, so first copy SPLAT-VO's jars into your local Maven repository
   (from Git Bash on Windows), then build it:

       oais-structure-splat/scripts/install-splat-deps.sh
       mvn -Psplat -pl oais-structure-splat install

   Its tests open a spectrum with SPLAT's own classes, using jniast's native
   library from `SPLAT_HOME`.

It needs Java 17 or later: `java -version`, or set `JAVA17_HOME`. This was
tried with SPLAT-VO 4.1 and with the older 3.11-3 from
<http://star-www.dur.ac.uk/~pdraper/splat/splat-vo/>.

SPLAT built from the starjava source works too (see the root README's "SPLAT
example description"). Set `SPLAT_HOME` to a folder with its `lib` and `etc`
(the starjava install folder), give `install-splat-deps.sh` that `lib` as
`STARJAVA_LIB`, and set `JNIAST_NATIVE_DIR` to the source tree's
`jniast/lib/amd64`, since the starjava install doesn't include it.

**Why a launcher?** TOPCAT takes the manifest reader as a plugin
(`-Dstartable.readers=...`). SPLAT reads tables through the same library
(STIL), and SPLAT-VO 4.1 does take the reader that way internally, but its
window's file loading never reaches it for a `.ttl` file: `-t table` only
tries VOTable, and `-t guess` reads it as text. So `OaisStructureSpectrumLauncher`
builds the spectrum from the manifest itself and hands it to a SPLAT window.

## Running it

    examples\splat\open-in-splat.bat        (Windows)
    examples/splat/open-in-splat.sh         (Linux, macOS)

SPLAT-VO opens with the spectrum plotted, flux against wavelength with its error
bars. Zoom in on H-beta at 4861 Angstrom or H-alpha at 6563 to see the broad
Balmer lines of an A star.

Either script also takes another manifest, as a file or a URL -- for example a
Data Object's manifest from the archive,
`http://localhost:9090/api/data-objects/<id>/repinfo.ttl` -- with `#name` after
it to choose one of several Data Objects.

TOPCAT opens the same manifest too, as a table of 601 rows: see the TOPCAT
example's scripts, which take a manifest as their argument.

## Putting it in an archive

`publish-to-archive.sh` puts the spectrum into an archive-manager archive --
yours, or any other -- as a Data Object with its Representation Information,
so it can be opened from the archive rather than from these files:

    ./publish-to-archive.sh https://your-archive.example.org
    ./publish-to-archive.sh https://your-archive.example.org https://your-archive.example.org/data/a0v-spectrum.bin

It builds the same description in the archive's RepInfo Tools, saves it with
its DFDL and DRB SDF descriptions as a new Data Object, labels it, and gives it
the data's storage location: by default the copy of `a0v-spectrum.bin` in this
project's GitHub repository, or any public http(s) address you give. (The
archive fetches the data itself, and won't fetch from its own or a private
network's addresses.) It reads the edit password from `ARCHIVE_EDIT_PASSWORD`
or asks for it, checks that the archive offers the data to SPLAT and can decode
it, and prints the Data Object's page, manifest and VOTable addresses. It runs
anywhere with `sh` and `curl`: Linux, macOS, or Git Bash on Windows.

The archive needs the version of archive-manager with TOPCAT and SPLAT support.
Then, with SPLAT running on your computer, the Data Object's page -- or its
right-click menu in the graph -- has "View with SPLAT", which sends the
spectrum to SPLAT over SAMP. That needs a SAMP hub, which SPLAT doesn't start
by itself: start its internal hub from SPLAT's Interop menu first (or have
TOPCAT running, which starts one). The first time, the hub asks whether to
allow the archive's page. For that, SPLAT-VO as installed will do, with none
of this project's code: step 1 of "Installing" is all it needs. Or open its
VOTable address in SPLAT (File → Location), or give its manifest address to
`open-in-splat`.
