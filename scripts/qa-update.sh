#!/usr/bin/env bash
# Pull already-published artifacts only; never builds or deletes volumes.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
tag=${1:?Usage: scripts/qa-update.sh PUBLISHED_RELEASE_TAG}
[[ "$tag" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$ ]] || { echo 'Invalid release tag' >&2; exit 2; }
export QA_TAG="$tag"
docker compose --project-name openmrs-qa -f "$root/compose.yaml" pull
docker compose --project-name openmrs-qa -f "$root/compose.yaml" up -d --no-build
docker compose --project-name openmrs-qa -f "$root/compose.yaml" ps
