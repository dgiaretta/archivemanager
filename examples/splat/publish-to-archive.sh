#!/bin/sh
# Puts the A0 V star spectrum into an archive-manager archive as a Data
# Object with its Representation Information, so it can be opened from the
# archive: "View with SPLAT" on its page or its right-click menu in the
# graph, its VOTable, or its Representation Information manifest by URL.
#
# It does what you'd do by hand in the archive's RepInfo Tools -- a blank
# byte layout, little-endian, with the object's name and the number of
# points, then a "point" record repeated that many times with its three
# fields, their types, units and meanings -- then saves it with its DFDL and
# DRB SDF descriptions, names the new Data Object, and gives it the storage
# location of the data.
#
# Usage:
#   ./publish-to-archive.sh ARCHIVE_URL [DATA_URL]
#
#   ARCHIVE_URL  the archive, e.g. https://archive.example.org or http://localhost:9090
#   DATA_URL     where a0v-spectrum.bin can be fetched from over http(s) by the
#                archive (a public address); by default the copy in this project's
#                GitHub repository.
#
# The edit password is read from ARCHIVE_EDIT_PASSWORD, or asked for. Needs curl.
# Running it again adds another Data Object; delete one from its page if needed.

set -eu

ARCHIVE="${1:?usage: $0 ARCHIVE_URL [DATA_URL]}"
ARCHIVE="${ARCHIVE%/}"
DATA_URL="${2:-https://raw.githubusercontent.com/dgiaretta/archivemanager/main/examples/splat/a0v-spectrum.bin}"

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
c -o /dev/null --data-urlencode "name=A0 V star spectrum" -d "defaultByteOrder=LITTLE_ENDIAN" \
    -d "fileExtensions=bin" \
    --data-urlencode "notes=A spectrum in binary: the object's name, the number of points, then one little-endian record per point, in order of wavelength." \
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
    --data-urlencode "semanticName=Object" --data-urlencode "definition=Name of the object observed, padded with spaces."
field root n_points -d name=n_points -d type=UINT32 -d occurs=once \
    --data-urlencode "semanticName=Number of points" --data-urlencode "definition=How many points the spectrum has."
point=$(add root record point)
[ -n "$point" ] || { echo "RepInfo Tools didn't add the point record -- is this archive up to date?" >&2; exit 1; }
c -o /dev/null -d "name=point" -d "occurs=repeated" -d "occursExpr=n_points" --data-urlencode "semanticName=Point" \
    --data-urlencode "definition=One point of the spectrum." "$ARCHIVE/repinfo-tools/elements/$point/update"
field "$point" wavelength -d name=wavelength -d type=FLOAT64 -d occurs=once -d units=Angstrom -d validMin=0 \
    --data-urlencode "semanticName=Wavelength" --data-urlencode "definition=Wavelength in air."
field "$point" flux -d name=flux -d type=FLOAT32 -d occurs=once -d units=erg/cm2/s/Angstrom \
    --data-urlencode "semanticName=Flux density" --data-urlencode "definition=Flux density per unit wavelength."
field "$point" error -d name=error -d type=FLOAT32 -d occurs=once -d units=erg/cm2/s/Angstrom -d validMin=0 \
    --data-urlencode "semanticName=Flux density error" --data-urlencode "definition=One-sigma uncertainty of the flux density."
echo "Described the spectrum in RepInfo Tools"

# ---- save: a new Data Object, with the DFDL and DRB SDF descriptions ----
resource=$(c -o /dev/null -w '%{redirect_url}' -d "dataObjectId=" -d "formats=dfdl" -d "formats=drb-java" \
    "$ARCHIVE/repinfo-tools/save")
id="${resource##*/resource/}"
[ -n "$id" ] && [ "$id" != "$resource" ] || { echo "Saving to the archive failed." >&2; exit 1; }

json() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
c -o /dev/null -H "Content-Type: application/json" \
    -d "{\"customProperty\":\"http://www.w3.org/2000/01/rdf-schema#label\",\"value\":\"A0 V star spectrum (SPLAT example)\"}" \
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
if c "$ARCHIVE/resource/$id" | grep -q "View with SPLAT"; then
    echo "The archive offers it to SPLAT"
else
    echo "The Data Object's page doesn't offer SPLAT -- check its Representation Information there." >&2
fi
votable=$(c -w '\n%{http_code}' "$ARCHIVE/api/data-objects/$id/votable")
if [ "$(printf '%s' "$votable" | tail -n 1)" = 200 ]; then
    rows=$(printf '%s' "$votable" | grep -c '<TR>' || true)
    echo "The archive decodes it: a VOTable of $rows points"
else
    echo "The archive couldn't make a VOTable of it yet:" >&2
    printf '%s\n' "$votable" | sed '$d' >&2
fi

cat <<EOF

Done. The Data Object's page:
  $ARCHIVE/resource/$id
Its Representation Information manifest (for SPLAT's launcher and TOPCAT's reader, by URL):
  $ARCHIVE/api/data-objects/$id/repinfo.ttl
Its data as VOTable (SPLAT: File > Location; TOPCAT: File > Load; no plugin needed):
  $ARCHIVE/api/data-objects/$id/votable
EOF
