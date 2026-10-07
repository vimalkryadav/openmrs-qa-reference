#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
source_dir="$root/.build/esm-admin-tools"
[ -d "$source_dir/node_modules" ] || { echo 'Run scripts/prepare-frontend.sh --native first' >&2; exit 1; }
cd "$source_dir/packages/esm-reports-app"
export NODE_OPTIONS=--max-old-space-size=6144
node ../../node_modules/typescript/bin/tsc --noEmit
node ../../node_modules/@rspack/cli/bin/rspack.js --mode production
cp -R dist/. "$root/.build/frontend/reports-dist/"
# Persist editable upstream changes in the tracked patch, not only the generated checkout.
git -C "$source_dir" diff > "$root/patches/esm-admin-tools-v4.4.0.patch"
python3 - "$root" <<'PY'
import hashlib,json,pathlib,re,shutil,sys
root=pathlib.Path(sys.argv[1]);build=root/'.build/frontend';native=root/'.native/frontend'
digest=hashlib.sha256((build/'reports-dist/openmrs-esm-reports-app.js').read_bytes()).hexdigest()[:12]
for name in ['Dockerfile','importmap.json']:
 p=build/name;p.write_text(re.sub(r'openmrs-esm-reports-app-4\.4\.0-reports-flows(?:-[0-9a-f]{12})?', 'openmrs-esm-reports-app-4.4.0-reports-flows-'+digest,p.read_text()))
imports=json.loads((build/'importmap.json').read_text());url=imports['imports']['@openmrs/esm-reports-app'];target=native/url.split('/openmrs/spa/')[-1].lstrip('/')
shutil.copytree(build/'reports-dist',target.parent,dirs_exist_ok=True);shutil.copy2(build/'importmap.json',native/'importmap.json')
print('Reports source compiled and served. Hard reload the browser to load the new content-addressed bundle.')
PY
