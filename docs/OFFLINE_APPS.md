# Local Open Concept Lab and Open Web Apps

Verified 2026-10-08 against native reference :8090 and clone :8093. No image build or release was performed. The paired [result ledger](offline-app-results.json) records 25 flows per application; the clone repository contains the human-readable `docs/qa/offline-app-flows.html` and `docs/qa/flows/09-offline-ocl-owa.md` catalogue.

## Supported local workflows

OCL imports a local ZIP containing `export.json` through its real 3.1.0 importer. Concept/name persistence, concept-set mappings, repeated uploads, changed names, history, item errors and error acknowledgement were validated through O3 and the admin OWA. Both interfaces allow local upload/history without a subscription. Subscription settings remain editable, but remote execution is disabled. The REST import entry point returns 400 before creating a row; direct importer and scheduled triggers are guarded as well. External item links are plain text with an explanation, and unfinished imports show Pending rather than the epoch.

OWA module management installs local HTML/CSS/JavaScript packages with a root `manifest.webapp`. Launch files must exist; uploads are limited to 32 MiB compressed, 128 MiB expanded and 4096 entries. Validation rejects traversal, symlinks, encrypted/duplicate entries and malformed manifests before replacing an existing app. Extraction uses a staging directory, then publishes the complete package; replacement removes obsolete files. Canonical `/openmrs/owa/` paths remain usable alongside the configured local base. File serving reads the current configured directory on each request and checks canonical containment. Remote app-store installation returns 400 without reading or downloading the requested URL.

Uploaded applications are executable web content; the module does not rewrite arbitrary third-party applications into offline applications. The verified fixture contains only local assets and uses the local OpenMRS REST API. The shipped OCL app's remote controls are explicitly disabled. Full Bed Management business workflows are outside this audit.

## Source ownership

- OCL 3.1.0: `source/ocl-src/` importer and scheduler; `source/ocl-web-src/` REST resources, including pre-mutation subscription URL/token validation. `prepare-backend.py` patches and repacks the embedded OCL OWA as well as extracting its served files, so activation cannot reinstall the original remote-enabled copy.
- OWA 1.15.0: `source/owa-src/` manager, upload controller, remote controller, servlet and filter; `source/owa-web/manage.jsp` upload validation, offline notice and accessible action labels.
- O3 4.4.0: tracked `patches/esm-admin-tools-v4.4.0.patch`. Both Reports and OCL are compiled into content-addressed local distributions and the import map is published after both directories exist. Backend preparation preserves a previously compiled frontend import map.
- Exact upstream commits are pinned in `versions.lock.json`; copyright and license notices are retained. The editable source and packaging recipe are committed, while compiled outputs remain ignored.

## Repeat the proof

Start the two existing native servers as described in `NATIVE.md`. Use a clean owned-fixture run, with no in-progress import. The reference test package uses Playwright in headless mode and blocks/counts every external browser HTTP(S) attempt.

```sh
./tests/run-offline-apps.sh
node tests/headless-ocl-offline.mjs reference
python3 tests/cleanup-ocl.py reference
node tests/headless-ocl-offline.mjs clone
python3 tests/cleanup-ocl.py clone
node tests/headless-owa-offline.mjs reference
node tests/headless-owa-offline.mjs clone
```

The Java script directly tests OCL REST/client/scheduler guards and OWA manager install/replacement/deletion plus seven malformed archives; it does not rely solely on browser interception. Clone focused coverage is `backend/tests/test_ocl_subscription_import.py`, `backend/tests/test_legacy_owa_admin.py` (28 passes) and `scripts/test_owa_middleware.mjs` (16 passes). The latter verifies legacy-route isolation when the OWA backend fails, mutable DB-driven prefixes, deleted-app 404s and resolver-failure 502s.

Raw results and cleanup ledgers are under ignored `test-results/{ocl,owa}-offline/{reference,clone}/`. Screenshots are temporary inspection artifacts and are removed after review. The tracked compact ledger has no clinical records or developer-local filesystem paths. If a browser run fails, inspect its owned UUIDs/settings ledger before retrying; do not unsubscribe to clean fixtures, because stock OCL erases all import history. Cleanup only removes captured UUIDs absent from the source seed. OWA deletion uses its own package identity and preserves shipped apps.

## Final data state

Both applications retain 24 original OCL imports and 35,440 import items. Owned concepts, mappings, import rows and installed packages were removed; both shipped OWAs remain. OWA settings were restored, including the original NULL base URL after stock settings submission normalized it to `/owa`. The Downloads seed was untouched. No additional containers were created.

The shared seed has 1,377 concepts. The reference retains two required attachment compatibility concepts, `42ed45fd-f3f6-44b6-bfc2-8bde1bb41e00` and `7cac8397-53cd-4f00-a6fe-028e8d743f8e`, for 1,379 concepts total. The earlier QA312 concept was removed separately after explicit authorization and a dependency audit: exactly its concept, name and mapping rows were absent from the seed. The guarded transaction preserved both compatibility concepts and all other key/UUID sets; `test-results/concept-reconciliation/{rollback,apply}.json` contains the recovery and validation ledger. None of these three concepts was created by the OCL flows. Local-flow proof compares the same imported UUIDs and verifies its cleanup independently.

## Configuration round-trip supplement

The [configuration ledger](ocl-configuration-results.json) adds five paired headless groups: required URL/token and malformed URL recovery; Cancel without writes; edited URL/token/snapshot/validation flags surviving hard reload; offline import rejection with unchanged history; and exact settings restoration with local upload/history still available. Both applications passed all five groups with no page errors or external browser request attempts. The original 24 imports and 35,440 items were unchanged.

```sh
node tests/headless-ocl-configuration.mjs reference
node tests/headless-ocl-configuration.mjs clone
```

The helper snapshots all OCL property identities, values and descriptions before mutation, then restores only the seven subscription settings. Native restoration first uses the normal global-property service so its cache observes the restored values; direct SQL alone leaves stale cached configuration. Guarded row restoration removes only newly created fixture settings and verifies the complete snapshot. It never invokes Unsubscribe. A rerun deliberately refuses to overwrite an existing snapshot: verify restoration, archive the previous ignored result directory, then start a new run.

The separate Angular OWA configuration form is exercised with `node tests/headless-ocl-configuration.mjs reference owa` and the same command with `clone`. This is distinct from the O3 form: Cancel navigates home, advanced settings are initially collapsed, and malformed URL errors come from REST. The reference resource now validates with the stock Java URL parser before the scheduler writes settings, returning a controlled 400 for malformed input instead of the former 500. The existing clone already returned 400. The Java regression asserts five invalid URL/token cases reject before any database/service access. No network lookup is performed by this validation.

The final separate OWA supplement passed six groups per application, including the visible invalid-URL error, retained input and successful correction. Together the O3 and OWA configuration supplements contain 11 paired groups in `ocl-configuration-results.json`; they supplement, rather than replace, the original 25 offline application flows per app. Both exact settings snapshots and the original import history were restored, with no page errors or external browser request attempts.
