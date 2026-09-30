#!/bin/sh
# Opens the A0 V star spectrum in SPLAT through its Representation
# Information manifest -- see README.md in this folder. SPLAT comes from an
# installed SPLAT-VO (SPLAT_HOME), the manifest reader from this project.
#
#   ./open-in-splat.sh                      (this example)
#   ./open-in-splat.sh other-manifest.ttl   (any manifest file or URL, #name to pick one)
here=$(cd "$(dirname "$0")" && pwd)
repo="$here/../.."
splat="$repo/oais-structure-splat/target"
reader="$repo/oais-structure-topcat/target"
manifest="${1:-$here/a0-v-star-spectrum.ttl}"

if [ -z "${SPLAT_HOME:-}" ]; then
    for d in "$HOME/splat-vo" /usr/local/splat-vo /opt/splat-vo /Applications/splat-vo; do
        [ -f "$d/lib/splat/splat.jar" ] && { SPLAT_HOME="$d"; break; }
    done
fi
[ -n "${SPLAT_HOME:-}" ] && [ -f "$SPLAT_HOME/lib/splat/splat.jar" ] || { echo "SPLAT-VO isn't found: install it and set SPLAT_HOME (see README.md, \"Installing\")."; exit 1; }
case "$(uname -m)" in
    aarch64|arm64) arch=aarch64 ;;
    *) arch=amd64 ;;
esac
native="${JNIAST_NATIVE_DIR:-$SPLAT_HOME/lib/$arch}"
[ -f "$splat/oais-structure-splat-0.0.1-SNAPSHOT.jar" ] || { echo "oais-structure-splat isn't built yet: see README.md, \"Installing\"."; exit 1; }
[ -d "$reader/dependency" ] || { echo "The manifest reader's dependencies aren't collected yet: see README.md, \"Installing\"."; exit 1; }

exec "${JAVA17_HOME:+$JAVA17_HOME/bin/}java" -Djava.library.path="$native" -Dsplat.etc.dir="$SPLAT_HOME/etc/splat" \
    -cp "$SPLAT_HOME/lib/splat/splat.jar:$splat/oais-structure-splat-0.0.1-SNAPSHOT.jar:$reader/oais-structure-topcat-0.0.1-SNAPSHOT.jar:$reader/dependency/*" \
    info.oais.infomodel.structure.splat.OaisStructureSpectrumLauncher "$manifest"
