#!/bin/sh
# Opens the bright star catalogue in TOPCAT through its Representation
# Information manifest -- see README.md in this folder.
#
#   ./open-in-topcat.sh                      (this example)
#   ./open-in-topcat.sh other-manifest.ttl   (any manifest file or URL, #name to pick one)
here=$(cd "$(dirname "$0")" && pwd)
repo="$here/../.."
plugin="$repo/oais-structure-topcat/target"
manifest="${1:-$here/bright-star-catalogue.ttl}"
[ -f "$repo/topcat-full.jar" ] || { echo "topcat-full.jar isn't in the repository root: see README.md, \"Installing\"."; exit 1; }
[ -d "$plugin/dependency" ] || { echo "The TOPCAT reader's dependencies aren't collected yet: see README.md, \"Installing\"."; exit 1; }
exec "${JAVA17_HOME:+$JAVA17_HOME/bin/}java" \
    -Dstartable.readers=info.oais.infomodel.structure.topcat.OaisStructureTableBuilder \
    -cp "$repo/topcat-full.jar:$plugin/oais-structure-topcat-0.0.1-SNAPSHOT.jar:$plugin/dependency/*" \
    uk.ac.starlink.topcat.Driver -f OAIS-RepInfo "$manifest"
