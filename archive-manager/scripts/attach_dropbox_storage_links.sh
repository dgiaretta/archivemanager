#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ $# -eq 0 ]]; then
  echo "Usage: scripts/attach_dropbox_storage_links.sh --dir <local-folder> --base-url <dropbox-folder-url> [--tdb data/tdb2] [--dry-run]"
  echo
  echo "Example:"
  echo "  scripts/attach_dropbox_storage_links.sh --dir \"C:/Users/me/Dropbox/records\" --base-url \"https://www.dropbox.com/scl/fi/abc123/records\" --tdb data/tdb2 --dry-run"
  exit 1
fi

mvn -q -DskipTests compile >/dev/null

CP_FILE=".attach_dropbox_storage_links.classpath"
rm -f "$CP_FILE"
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >/dev/null
CP="target/classes:$(cat "$CP_FILE")"

java -cp "$CP" info.oais.archive.manager.tools.AttachDropboxStorageLinks "$@"

rm -f "$CP_FILE"
