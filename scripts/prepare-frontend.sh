#!/usr/bin/env bash
# Compile frontend assets only. Does not build or deploy an image.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
source_dir="$root/.build/esm-admin-tools"
readarray_compat=$(python3 - "$root/versions.lock.json" <<'PY'
import json,sys
s=json.load(open(sys.argv[1]))['sources']['esm-admin-tools']
print(s['url']);print(s['ref'])
PY
)
url=$(printf '%s\n' "$readarray_compat" | head -1)
ref=$(printf '%s\n' "$readarray_compat" | tail -1)
if [ -e "$source_dir" ]; then
  echo "Refusing to overwrite $source_dir. Keep source edits in patches/; remove only this generated checkout to prepare again." >&2
  exit 1
fi
git init "$source_dir"
git -C "$source_dir" fetch --depth=1 "$url" "$ref"
git -C "$source_dir" checkout --detach FETCH_HEAD
git -C "$source_dir" apply "$root/patches/esm-admin-tools-v4.4.0.patch"
mkdir -p "$root/.build/frontend"
cp "$root/docker/frontend/"* "$root/.build/frontend/"
node_image=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["images"]["node"])' "$root/versions.lock.json")
docker run --rm -v "$source_dir:/src" -v "$root/.build/frontend:/out" -w /src -e COREPACK_ENABLE_DOWNLOAD_PROMPT=0 -e NODE_OPTIONS=--max-old-space-size=6144 "$node_image" bash -euc '
corepack enable
yarn install --immutable
cd packages/esm-reports-app
node ../../node_modules/typescript/bin/tsc --noEmit
node ../../node_modules/@rspack/cli/bin/rspack.js --mode production
mkdir -p /out/reports-dist
cp -R dist/. /out/reports-dist/
'
python3 - "$root/.build/frontend" <<'PY'
import hashlib,pathlib,re,sys
root=pathlib.Path(sys.argv[1]);digest=hashlib.sha256((root/'reports-dist/openmrs-esm-reports-app.js').read_bytes()).hexdigest()[:12]
for name in ['Dockerfile','importmap.json']:
 p=root/name;p.write_text(re.sub(r'openmrs-esm-reports-app-4\.4\.0-reports-flows(?:-[0-9a-f]{12})?', 'openmrs-esm-reports-app-4.4.0-reports-flows-'+digest,p.read_text()))
PY
