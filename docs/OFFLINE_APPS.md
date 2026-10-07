# Local Open Concept Lab and Open Web Apps

Verified 2026-10-08 against native reference :8090 and clone :8093. No image build or release was performed. The paired [result ledger](offline-app-results.json) records 25 flows per application; the clone repository contains the human-readable `docs/qa/offline-app-flows.html` and `docs/qa/flows/09-offline-ocl-owa.md` catalogue.

## Supported local workflows

OCL imports a local ZIP containing `export.json` through its real 3.1.0 importer. Concept/name persistence, concept-set mappings, repeated uploads, changed names, history, item errors and error acknowledgement were validated through O3 and the admin OWA. Both interfaces allow local upload/history without a subscription. Subscription settings remain editable, but remote execution is disabled. The REST import entry point returns 400 before creating a row; direct importer and scheduled triggers are guarded as well. External item links are plain text with an explanation, and unfinished imports show Pending rather than the epoch.

OWA module management installs local HTML/CSS/JavaScript packages with a root `manifest.webapp`. Launch files must exist; uploads are limited to 32 MiB compressed, 128 MiB expanded and 4096 entries. Validation rejects traversal, symlinks, encrypted/duplicate entries and malformed manifests before replacing an existing app. Extraction uses a staging directory, then publishes the complete package; replacement removes obsolete files. Canonical `/openmrs/owa/` paths remain usable alongside the configured local base. File serving reads the current configured directory on each request and checks canonical containment. Remote app-store installation returns 400 without reading or downloading the requested URL.

Uploaded applications are executable web content; the module does not rewrite arbitrary third-party applications into offline applications. The verified fixture contains only local assets and uses the local OpenMRS REST API. The shipped OCL app's remote controls are explicitly disabled. Full Bed Management business workflows are outside this audit.

## Source ownership

- OCL 3.1.0: `source/ocl-src/` importer and scheduler; `source/ocl-web-src/` REST resource. `prepare-backend.py` patches and repacks the embedded OCL OWA as well as extracting its served files, so activation cannot reinstall the original remote-enabled copy.
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

Full screenshots, raw results and cleanup ledgers are under ignored `test-results/{ocl,owa}-offline/{reference,clone}/`. The tracked compact ledger has no clinical records or developer-local filesystem paths. If a browser run fails, inspect its owned UUIDs/settings ledger before retrying; do not unsubscribe to clean fixtures, because stock OCL erases all import history. Cleanup only removes captured UUIDs absent from the source seed. OWA deletion uses its own package identity and preserves shipped apps.

## Final data state

Both applications retain 24 original OCL imports and 35,440 import items. Owned concepts, mappings, import rows and installed packages were removed; both shipped OWAs remain. OWA settings were restored, including the original NULL base URL after stock settings submission normalized it to `/owa`. The Downloads seed was untouched. No additional containers were created.

The shared seed has 1,377 concepts. The reference has three extra concepts: attachment module concepts `42ed45fd-f3f6-44b6-bfc2-8bde1bb41e00` and `7cac8397-53cd-4f00-a6fe-028e8d743f8e`, plus the earlier `QA312 duplicate mapping verification` concept `6883f553-e8e8-41a1-8c63-a9fde934c20a`. None was created by these flows. The older QA312 row was reported for separate dependency/ownership review; this cleanup did not remove it. Local-flow proof compares the same newly imported UUIDs and verifies cleanup independently of those compatibility rows.
