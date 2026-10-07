#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
source_dir="$root/.build/esm-admin-tools"
[ -d "$source_dir/node_modules" ] || { echo 'Run scripts/prepare-frontend.sh --native first' >&2; exit 1; }
for package in esm-reports-app esm-admin-openconceptlab-app; do
cd "$source_dir/packages/$package"
export NODE_OPTIONS=--max-old-space-size=6144
node ../../node_modules/typescript/bin/tsc --noEmit
node ../../node_modules/@rspack/cli/bin/rspack.js --mode production
output=reports-dist; [ "$package" = esm-admin-openconceptlab-app ] && output=ocl-dist
mkdir -p "$root/.build/frontend/$output"
cp -R dist/. "$root/.build/frontend/$output/"
done
# Persist editable upstream changes in the tracked patch, not only the generated checkout.
git -C "$source_dir" diff | sed 's/[[:blank:]]*$//' > "$root/patches/esm-admin-tools-v4.4.0.patch"
python3 - "$root" <<'PY'
import hashlib,json,pathlib,re,shutil,sys
root=pathlib.Path(sys.argv[1]);build=root/'.build/frontend';native=root/'.native/frontend'
digest=hashlib.sha256((build/'reports-dist/openmrs-esm-reports-app.js').read_bytes()).hexdigest()[:12]
ocl_digest=hashlib.sha256((build/'ocl-dist/openmrs-esm-openconceptlab-app.js').read_bytes()).hexdigest()[:12]
# Both manifests are tracked templates, with generated content hashes only in .build.
for name in ['Dockerfile','importmap.json']:
 shutil.copy2(root/'docker/frontend'/name,build/name)
for name in ['Dockerfile','importmap.json']:
 p=build/name;p.write_text(re.sub(r'openmrs-esm-reports-app-4\.4\.0-reports-flows(?:-[0-9a-f]{12})?', 'openmrs-esm-reports-app-4.4.0-reports-flows-'+digest,p.read_text()))
 p.write_text(re.sub(r'openmrs-esm-openconceptlab-app-4\.4\.0-offline(?:-[0-9a-f]{12})?', 'openmrs-esm-openconceptlab-app-4.4.0-offline-'+ocl_digest,p.read_text()))
imports=json.loads((build/'importmap.json').read_text());url=imports['imports']['@openmrs/esm-reports-app'];target=native/url.split('/openmrs/spa/')[-1].lstrip('/')
shutil.copytree(build/'reports-dist',target.parent,dirs_exist_ok=True)
url=imports['imports']['@openmrs/esm-openconceptlab-app'];target=native/url.split('/openmrs/spa/')[-1].lstrip('/')
shutil.copytree(build/'ocl-dist',target.parent,dirs_exist_ok=True)
shutil.copy2(build/'importmap.json',native/'importmap.json')
print('Reports source compiled and served. Hard reload the browser to load the new content-addressed bundle.')
PY
