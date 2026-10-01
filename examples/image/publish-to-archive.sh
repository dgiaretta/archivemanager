#!/bin/sh
# Puts the galaxy field image into an archive-manager archive as a Data Object
# with its Representation Information, so it can be opened from the archive:
# "View with DS9" or "View with Aladin" on its page or its right-click menu in
# the graph, its FITS image for Fiji/ImageJ, or its Representation Information
# manifest by URL.
#
# It does what you'd do by hand in the archive's RepInfo Tools -- a blank
# byte layout, little-endian, with the field's name, width and height, then a
# "row" record repeated height times holding a "pixel" field repeated width
# times, with its type, units and meaning -- then saves it with its DFDL and
# DRB SDF descriptions, names the new Data Object, and gives it the storage
# location of the data.
#
# Usage:
#   ./publish-to-archive.sh ARCHIVE_URL [DATA_URL]
#
#   ARCHIVE_URL  the archive, e.g. https://archive.example.org or http://localhost:9090
#   DATA_URL     where galaxy-field.bin can be fetched from over http(s) by the
#                archive (a public address); by default the copy in this project's
#                GitHub repository.
#
# The edit password is read from ARCHIVE_EDIT_PASSWORD, or asked for. Needs curl.
# Running it again adds another Data Object; delete one from its page if needed.

set -eu

ARCHIVE="${1:?usage: $0 ARCHIVE_URL [DATA_URL]}"
ARCHIVE="${ARCHIVE%/}"
DATA_URL="${2:-https://raw.githubusercontent.com/dgiaretta/archivemanager/main/examples/image/galaxy-field.bin}"

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
c -o /dev/null --data-urlencode "name=Galaxy field" -d "defaultByteOrder=LITTLE_ENDIAN" \
    -d "fileExtensions=bin" \
    --data-urlencode "notes=An image in binary: the field's name, the width and height in pixels, then the rows in order, each its pixels' little-endian counts." \
    "$ARCHIVE/repinfo-tools/details"

add() {  # parent kind name -> the new element's id
    c -o /dev/null -w '%{redirect_url}' -d "parentId=$1" -d "kind=$2" -d "name=$3" \
        "$ARCHIVE/repinfo-tools/elements/add" | sed 's/.*element=//'
}
field() {  # parent name, then the update form's fields
    id=$(add "$1" field "$2")
    [ -n "$id" ] || { echo "RepInfo Tools didn't add the field $2 -- is this archive up to date?" >&2; exit 1; }
    shift 2
    c -o /dev/null "$@" "$ARCHIVE/repinfo-tools/elements/$id/update"
}
field root object -d name=object -d type=STRING -d length=16 -d occurs=once \
    --data-urlencode "semanticName=Object" --data-urlencode "definition=Name of the field observed, padded with spaces."
field root width -d name=width -d type=UINT32 -d occurs=once \
    --data-urlencode "semanticName=Width" --data-urlencode "definition=Number of pixels in each row."
field root height -d name=height -d type=UINT32 -d occurs=once \
    --data-urlencode "semanticName=Height" --data-urlencode "definition=Number of rows."
row=$(add root record row)
[ -n "$row" ] || { echo "RepInfo Tools didn't add the row record -- is this archive up to date?" >&2; exit 1; }
c -o /dev/null -d "name=row" -d "occurs=repeated" -d "occursExpr=height" --data-urlencode "semanticName=Row" \
    --data-urlencode "definition=One row of the image, from the bottom of the field upwards." \
    "$ARCHIVE/repinfo-tools/elements/$row/update"
field "$row" pixel -d name=pixel -d type=UINT16 -d occurs=repeated -d occursExpr=width -d units=ADU \
    --data-urlencode "semanticName=Counts" \
    --data-urlencode "definition=Detector counts in one pixel, sky background included."
echo "Described the image in RepInfo Tools"

# ---- save: a new Data Object, with the DFDL and DRB SDF descriptions ----
resource=$(c -o /dev/null -w '%{redirect_url}' -d "dataObjectId=" -d "formats=dfdl" -d "formats=drb-java" \
    "$ARCHIVE/repinfo-tools/save")
id="${resource##*/resource/}"
[ -n "$id" ] && [ "$id" != "$resource" ] || { echo "Saving to the archive failed." >&2; exit 1; }

json() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
c -o /dev/null -H "Content-Type: application/json" \
    -d "{\"customProperty\":\"http://www.w3.org/2000/01/rdf-schema#label\",\"value\":\"Galaxy field (image example)\"}" \
    "$ARCHIVE/api/entities/$id/properties"
c -o /dev/null -H "Content-Type: application/json" \
    -d "{\"customProperty\":\"http://ontology.oais.info/im/hasStorageLocation\",\"customTarget\":\"$(json "$DATA_URL")\"}" \
    "$ARCHIVE/api/entities/$id/relationships"
echo "Saved as a Data Object, its bits at $DATA_URL"

# ---- check ----
status=$(c -o /dev/null -w '%{http_code}' "$ARCHIVE/api/data-objects/$id/repinfo.ttl")
if [ "$status" != 200 ]; then
    echo "The archive has no Representation Information manifest for it (HTTP $status):" >&2
    echo "it needs the version of archive-manager with image viewer support." >&2
    exit 1
fi
if c "$ARCHIVE/resource/$id" | grep -q "View with DS9"; then
    echo "The archive offers it to DS9 and Aladin"
else
    echo "The Data Object's page doesn't offer DS9 -- is this archive up to date? Check its Representation Information there." >&2
fi
FITS=$(mktemp)
trap 'rm -f "$JAR" "$FITS"' EXIT
status=$(c -o "$FITS" -w '%{http_code}' "$ARCHIVE/api/data-objects/$id/fits")
if [ "$status" = 200 ]; then
    size=$(head -c 2880 "$FITS" | fold -w 80 | sed -n 's/^NAXIS\([12]\) *= *\([0-9]*\).*/\2/p' | paste -sd x -)
    echo "The archive decodes it: a FITS image of $size pixels"
else
    echo "The archive couldn't make a FITS image of it yet:" >&2
    cat "$FITS" >&2
    echo >&2
fi

cat <<EOF

Done. The Data Object's page:
  $ARCHIVE/resource/$id
Its image as FITS (DS9, Aladin, or Fiji/ImageJ: File > Import > URL):
  $ARCHIVE/api/data-objects/$id/fits
Its Representation Information manifest:
  $ARCHIVE/api/data-objects/$id/repinfo.ttl
EOF
