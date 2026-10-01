#!/bin/sh
# Writes the galaxy field image as FITS through its Representation
# Information manifest, then opens it in an image viewer if you name one --
# see README.md in this folder.
#
#   ./open-as-fits.sh                                  (this example, to galaxy-field.fits)
#   ./open-as-fits.sh other-manifest.ttl out.fits      (any manifest file or URL, #name to pick one)
#
# VIEWER, if set, is run with the FITS file as its argument, e.g.
#   VIEWER=ds9 ./open-as-fits.sh
#   VIEWER="$HOME/Fiji.app/ImageJ-linux64" ./open-as-fits.sh
#   VIEWER="java -jar $HOME/Aladin/Aladin.jar" ./open-as-fits.sh
here=$(cd "$(dirname "$0")" && pwd)
repo="$here/../.."
image="$repo/oais-structure-image/target/oais-structure-image-0.0.1-SNAPSHOT.jar"
reader="$repo/oais-structure-topcat/target"
manifest="${1:-$here/galaxy-field.ttl}"
fits="${2:-$here/galaxy-field.fits}"
[ -f "$image" ] || { echo "oais-structure-image isn't built yet: see README.md, \"Installing\"."; exit 1; }
[ -d "$reader/dependency" ] || { echo "The manifest reader's dependencies aren't collected yet: see README.md, \"Installing\"."; exit 1; }

# A Windows java (Git Bash) wants Windows paths, with ; between classpath entries.
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) sep=";"; image=$(cygpath -m "$image"); reader=$(cygpath -m "$reader") ;;
    *) sep=":" ;;
esac
"${JAVA17_HOME:+$JAVA17_HOME/bin/}java" \
    -cp "$image$sep$reader/oais-structure-topcat-0.0.1-SNAPSHOT.jar$sep$reader/dependency/*" \
    info.oais.infomodel.structure.image.ManifestToFits "$manifest" "$fits" || exit 1
if [ -n "${VIEWER:-}" ]; then
    # VIEWER may be a command with arguments of its own, so it's split on spaces.
    exec $VIEWER "$fits"
fi
echo "Open it in Fiji/ImageJ, SAOImage DS9 or Aladin, or set VIEWER to have this script do it."
