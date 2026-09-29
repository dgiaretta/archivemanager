#!/bin/sh
# Puts the bright star catalogue into an archive-manager archive as a Data
# Object with its Representation Information, so it can be opened from the
# archive: "View with TOPCAT" on its page or its right-click menu in the
# graph, its VOTable, or its Representation Information manifest by URL.
#
# It does what you'd do by hand in the archive's RepInfo Tools -- a blank
# byte layout, a "star" record repeated to the end of the file with its five
# fields, their types, units and meanings -- then saves it with its DFDL and
# DRB SDF descriptions, names the new Data Object, and gives it the storage
# location of the data.
#
# Usage:
#   ./publish-to-archive.sh ARCHIVE_URL [DATA_URL]
#
#   ARCHIVE_URL  the archive, e.g. https://archive.example.org or http://localhost:9090
#   DATA_URL     where bright-stars.bin can be fetched from over http(s) by the
#                archive (a public address); by default the copy in this project's
#                GitHub repository.
#
# The edit password is read from ARCHIVE_EDIT_PASSWORD, or asked for. Needs curl.
# Running it again adds another Data Object; delete one from its page if needed.

set -eu

ARCHIVE="${1:?usage: $0 ARCHIVE_URL [DATA_URL]}"
ARCHIVE="${ARCHIVE%/}"
DATA_URL="${2:-https://raw.githubusercontent.com/dgiaretta/archivemanager/main/examples/topcat/bright-stars.bin}"

if [ -z "${ARCHIVE_EDIT_PASSWORD:-}" ]; then
    printf 'Edit password for %s: ' "$ARCHIVE" >&2
    stty -echo 2>/dev/null || true
    read -r ARCHIVE_EDIT_PASSWORD
    stty echo 2>/dev/null || true
    printf '\n' >&2
fi

JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT
c() { curl -sS -b "$JAR" -c "$JAR" "$@"; }

# ---- log in ----
location=$(c -o /dev/null -w '%{redirect_url}' --data-urlencode "password=$ARCHIVE_EDIT_PASSWORD" \
    --data-urlencode "redirect=/" "$ARCHIVE/login")
case "$location" in
    *error=*) echo "The archive didn't accept the edit password." >&2; exit 1 ;;
    "") echo "No answer from $ARCHIVE/login -- is that the archive's address?" >&2; exit 1 ;;
esac
echo "Logged in to $ARCHIVE"

# ---- the description, in RepInfo Tools ----
c -o /dev/null -d "template=blank" "$ARCHIVE/repinfo-tools/start"
c -o /dev/null --data-urlencode "name=Bright star catalogue" -d "defaultByteOrder=BIG_ENDIAN" \
    -d "fileExtensions=bin" \
    --data-urlencode "notes=A binary catalogue of bright stars: one fixed-length record per star, big-endian, to the end of the file." \
    "$ARCHIVE/repinfo-tools/details"

add() {  # parent kind name -> the new element's id
    c -o /dev/null -w '%{redirect_url}' -d "parentId=$1" -d "kind=$2" -d "name=$3" \
        "$ARCHIVE/repinfo-tools/elements/add" | sed 's/.*element=//'
}
star=$(add root record star)
[ -n "$star" ] || { echo "RepInfo Tools didn't add the star record -- is this archive up to date?" >&2; exit 1; }
c -o /dev/null -d "name=star" -d "occurs=until_end" --data-urlencode "semanticName=Star" \
    --data-urlencode "definition=One star of the catalogue." "$ARCHIVE/repinfo-tools/elements/$star/update"

field() {  # name, then the update form's fields
    id=$(add "$star" field "$1")
    shift
    c -o /dev/null "$@" "$ARCHIVE/repinfo-tools/elements/$id/update"
}
field name -d name=name -d type=STRING -d length=12 -d occurs=once \
    --data-urlencode "semanticName=Star name" --data-urlencode "definition=Common name, padded with spaces."
field ra -d name=ra -d type=FLOAT64 -d occurs=once -d units=deg -d validMin=0 -d validMax=360 \
    --data-urlencode "semanticName=Right ascension" --data-urlencode "definition=Right ascension, ICRS, epoch J2000."
field dec -d name=dec -d type=FLOAT64 -d occurs=once -d units=deg -d validMin=-90 -d validMax=90 \
    --data-urlencode "semanticName=Declination" --data-urlencode "definition=Declination, ICRS, epoch J2000."
field vmag -d name=vmag -d type=FLOAT32 -d occurs=once -d units=mag \
    --data-urlencode "semanticName=Visual magnitude" --data-urlencode "definition=Apparent magnitude in the Johnson V band."
field parallax -d name=parallax -d type=FLOAT32 -d occurs=once -d units=mas -d validMin=0 \
    --data-urlencode "semanticName=Parallax" --data-urlencode "definition=Trigonometric parallax."
echo "Described the catalogue in RepInfo Tools"

# ---- save: a new Data Object, with the DFDL and DRB SDF descriptions ----
resource=$(c -o /dev/null -w '%{redirect_url}' -d "dataObjectId=" -d "formats=dfdl" -d "formats=drb-java" \
    "$ARCHIVE/repinfo-tools/save")
id="${resource##*/resource/}"
[ -n "$id" ] && [ "$id" != "$resource" ] || { echo "Saving to the archive failed." >&2; exit 1; }

json() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
c -o /dev/null -H "Content-Type: application/json" \
    -d "{\"customProperty\":\"http://www.w3.org/2000/01/rdf-schema#label\",\"value\":\"Bright stars (TOPCAT example)\"}" \
    "$ARCHIVE/api/entities/$id/properties"
c -o /dev/null -H "Content-Type: application/json" \
    -d "{\"customProperty\":\"https://oais.info/bridge#hasStorageLocation\",\"customTarget\":\"$(json "$DATA_URL")\"}" \
    "$ARCHIVE/api/entities/$id/relationships"
echo "Saved as a Data Object, its bits at $DATA_URL"

# ---- check ----
status=$(c -o /dev/null -w '%{http_code}' "$ARCHIVE/api/data-objects/$id/repinfo.ttl")
if [ "$status" != 200 ]; then
    echo "The archive has no Representation Information manifest for it (HTTP $status):" >&2
    echo "it needs the version of archive-manager with TOPCAT and SPLAT support." >&2
    exit 1
fi
votable=$(c -w '\n%{http_code}' "$ARCHIVE/api/data-objects/$id/votable")
if [ "$(printf '%s' "$votable" | tail -n 1)" = 200 ]; then
    rows=$(printf '%s' "$votable" | grep -c '<TR>' || true)
    echo "The archive decodes it: a VOTable of $rows stars"
else
    echo "The archive couldn't make a VOTable of it yet:" >&2
    printf '%s\n' "$votable" | sed '$d' >&2
fi

cat <<EOF

Done. The Data Object's page:
  $ARCHIVE/resource/$id
Its Representation Information manifest (for TOPCAT's reader and SPLAT, by URL):
  $ARCHIVE/api/data-objects/$id/repinfo.ttl
Its data as VOTable (TOPCAT: File > Load, no plugin needed):
  $ARCHIVE/api/data-objects/$id/votable
EOF
