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
if [ "${1:-}" = '--native' ]; then
  (
    cd "$source_dir"
    export NODE_OPTIONS=--max-old-space-size=6144
    yarn_file=$(find .yarn/releases -name 'yarn-*.cjs' -maxdepth 1 | head -1)
    node "$yarn_file" install --immutable
    for package in esm-reports-app esm-admin-openconceptlab-app; do
    cd "$source_dir/packages/$package"
    node ../../node_modules/typescript/bin/tsc --noEmit
    node ../../node_modules/@rspack/cli/bin/rspack.js --mode production
    output=reports-dist; [ "$package" = esm-admin-openconceptlab-app ] && output=ocl-dist
    mkdir -p "$root/.build/frontend/$output"
    cp -R dist/. "$root/.build/frontend/$output/"
    done
  )
else
docker run --rm -v "$source_dir:/src" -v "$root/.build/frontend:/out" -w /src -e COREPACK_ENABLE_DOWNLOAD_PROMPT=0 -e NODE_OPTIONS=--max-old-space-size=6144 "$node_image" bash -euc '
corepack enable
yarn install --immutable
for package in esm-reports-app esm-admin-openconceptlab-app; do
cd /src/packages/$package
node ../../node_modules/typescript/bin/tsc --noEmit
node ../../node_modules/@rspack/cli/bin/rspack.js --mode production
output=reports-dist; [ "$package" = esm-admin-openconceptlab-app ] && output=ocl-dist
mkdir -p /out/$output
cp -R dist/. /out/$output/
done
'
fi
python3 - "$root/.build/frontend" <<'PY'
import hashlib,pathlib,re,sys
root=pathlib.Path(sys.argv[1]);digest=hashlib.sha256((root/'reports-dist/openmrs-esm-reports-app.js').read_bytes()).hexdigest()[:12]
ocl_digest=hashlib.sha256((root/'ocl-dist/openmrs-esm-openconceptlab-app.js').read_bytes()).hexdigest()[:12]
for name in ['Dockerfile','importmap.json']:
 p=root/name;p.write_text(re.sub(r'openmrs-esm-reports-app-4\.4\.0-reports-flows(?:-[0-9a-f]{12})?', 'openmrs-esm-reports-app-4.4.0-reports-flows-'+digest,p.read_text()))
 p.write_text(re.sub(r'openmrs-esm-openconceptlab-app-4\.4\.0-offline(?:-[0-9a-f]{12})?', 'openmrs-esm-openconceptlab-app-4.4.0-offline-'+ocl_digest,p.read_text()))
PY
