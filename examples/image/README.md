# Example: an image in Fiji/ImageJ, DS9 or Aladin

`galaxy-field.bin` is a small binary image: a 16-character name, the width
and height (uint32), then 128 rows of 128 pixels, each a little-endian
unsigned 16-bit count -- a synthetic galaxy with a few stars, on a sky
background with noise. No image viewer can read that format. Here it is read
through the data's OAIS Representation Information and written as FITS, which
Fiji/ImageJ, SAOImage DS9 and Aladin all open.

| File | What it is |
|---|---|
| `galaxy-field.ttl` | The **Representation Information manifest**: a Turtle excerpt naming the data file, its structure descriptions (DFDL or DRB SDF -- equivalent alternatives, either will do), the image view, and what each element means. Every file is named explicitly; rename or move them by editing it. |
| `galaxy-field.dfdl.xsd` | The structure as a DFDL schema. |
| `galaxy-field.drb.xsd` | The same structure as a DRB SDF schema. |
| `galaxy-field-image-view.xml` | How to show the decoded data as an image: one image row per `row` record, its `pixel` values in order, with their units and meaning. |
| `galaxy-field.bin` | The data. |

The manifest, the two schemas and the image view were made with
archive-manager's RepInfo Tools, not by hand: a blank byte layout with fields
`object`, `width` and `height`, then a `row` record repeated `height` times
holding a `pixel` field (UINT16, units ADU) repeated `width` times, then
**Preview → Open in TOPCAT, SPLAT or an image viewer** with the data file name
`galaxy-field.bin`. RepInfo Tools recognises an image from that shape -- a
repeated record holding one repeated number field -- and generates the image
view; with no other fields in the row, there's no table view. The archive's
help ("Table, spectrum or image") explains the rules.

## Installing

Once, from the repository root, build the image module and the manifest
reader it uses, and collect their dependencies:

    mvn -pl oais-structure-image -am install -DskipTests
    mvn -pl oais-structure-topcat dependency:copy-dependencies -DincludeScope=runtime

It needs Java 17 or later: `java -version`, or set `JAVA17_HOME`. Then any of
these viewers, all free and open source:

- [Fiji](https://fiji.sc/) (ImageJ with plugins), which reads FITS as it comes;
- [SAOImage DS9](https://ds9.si.edu/);
- [Aladin Desktop](https://aladin.cds.unistra.fr/AladinDesktop/).

## Running it

    examples\image\open-as-fits.bat        (Windows)
    examples/image/open-as-fits.sh         (Linux, macOS, Git Bash)

writes `galaxy-field.fits` next to the data and says where. Open it in your
viewer, or name the viewer in `VIEWER` and the script opens it for you:

    set "VIEWER=C:\Fiji.app\ImageJ-win64.exe" && examples\image\open-as-fits.bat
    VIEWER=ds9 examples/image/open-as-fits.sh
    VIEWER="java -jar $HOME/Aladin/Aladin.jar" examples/image/open-as-fits.sh

The FITS header carries what the Representation Information says: `BUNIT =
'ADU'`, the pixels' meaning as a `COMMENT`, and as `HISTORY` where the data
and its Representation Information came from (Fiji: Image → Show Info; DS9:
File → Header). The values are stored exactly, as unsigned 16-bit integers
(`BITPIX = 16`, `BZERO = 32768`). Rows are kept in the order the data holds
them, which here is from the bottom of the field up -- the way these viewers
show a FITS image.

Either script also takes another manifest, as a file or a URL -- for example a
Data Object's manifest from the archive,
`http://localhost:9090/api/data-objects/<id>/repinfo.ttl` -- and an output
file name: `open-as-fits.sh other.ttl#name other.fits`.

## Putting it in an archive

`publish-to-archive.sh` puts the image into an archive-manager archive --
yours, or any other -- as a Data Object with its Representation Information,
so it can be opened from the archive rather than from these files:

    ./publish-to-archive.sh https://your-archive.example.org
    ./publish-to-archive.sh https://your-archive.example.org https://your-archive.example.org/data/galaxy-field.bin

It builds the same description in the archive's RepInfo Tools, saves it with
its DFDL and DRB SDF descriptions as a new Data Object, labels it, and gives it
the data's storage location: by default the copy of `galaxy-field.bin` in this
project's GitHub repository, or any public http(s) address you give. (The
archive fetches the data itself, and won't fetch from its own or a private
network's addresses.) It reads the edit password from `ARCHIVE_EDIT_PASSWORD`
or asks for it, checks that the archive can make a FITS image of the data, and
prints the Data Object's page, FITS and manifest addresses. It runs anywhere
with `sh` and `curl`: Linux, macOS, or Git Bash on Windows.

The archive needs the version of archive-manager with image support. Then the
Data Object's page -- or its right-click menu in the graph -- has "View with
DS9" and "View with Aladin", which send the image to that application running
on your computer over SAMP (Aladin starts its own SAMP hub; if DS9 finds no
hub running, start one first -- TOPCAT or Aladin will do), and a FITS link -- "Download as
FITS" on the graph menu -- for Fiji, which doesn't use SAMP: download it, or
give the link's address to Fiji's File → Import → URL.
