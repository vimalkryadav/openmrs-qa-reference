#!/usr/bin/env bash
# Explicit manual action only. Never invoked by push/PR workflows or QA updates.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
tag=${1:?Usage: scripts/build-images.sh RELEASE_TAG [--push]}
[[ "$tag" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$ ]] || { echo 'Invalid release tag' >&2; exit 2; }
mode=${2:---load}
[[ "$mode" == '--push' || "$mode" == '--load' ]] || { echo 'Use --push or omit for local build' >&2; exit 2; }
registry=${REFERENCE_REGISTRY:-public.ecr.aws/x9d4j8f0}
platforms=${REFERENCE_PLATFORMS:-linux/amd64,linux/arm64}
if [ "$mode" = '--load' ]; then platforms=${REFERENCE_LOCAL_PLATFORM:-linux/arm64}; fi
python3 "$root/scripts/prepare-backend.py"
"$root/scripts/prepare-frontend.sh"
for service in backend frontend; do
  docker buildx build --platform "$platforms" --provenance=false --sbom=false --build-arg "RELEASE_TAG=$tag" -t "$registry/openmrs-qa-$service:$tag" "$mode" "$root/.build/$service"
done
if [ "$mode" = '--push' ]; then
  # DB and gateway bytes remain pinned; never replace seed data during an app-only release.
  for service in db gateway; do
    image=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["images"][sys.argv[2]])' "$root/versions.lock.json" "$service")
    docker buildx imagetools create -t "$registry/openmrs-qa-$service:$tag" "$image"
  done
fi
