# Reports fixes and verification ledger

This ledger records fixes separately from the immutable 2026-10-07 audit. The first batch is on `fix/reports-reference-batch1`, based on native runtime commit `07a6f0a`. It changes editable module overrides and runs through the existing native overlay; no image was built or published.

| Audit ID | Root cause and change | Focused proof | Native headless proof | Limits |
| --- | --- | --- | --- | --- |
| A-01 | SQL context setup uses `SET`; treating each statement as a result query swallowed the SQL error and produced a null iterator. Execute setup statements correctly, propagate errors, and transfer connection ownership to the iterator. | JDBC proxy assertions cover setup, rows, empty results, failure cleanup, exhaustion, repeated `next`, explicit close and early truncation. | Iterable SQL 7/9 preview and CSV; empty and malformed SQL; separate immutable 60-row definition displays 50 preview rows and exports all 60. | Full export intentionally streams all requested rows. Editing a dataset after an earlier report run has a separate stale-value observation below. |
| A-03 | Missing Initializer sticker flags hid every field. Missing/blank settings now enable identifier, name, gender, date of birth, age and address plus barcode; explicit false remains false. | Six absent-setting defaults, opt-in secondary identifier, explicit false/true. | Aarav fixture emits all six populated fields and barcode; unknown UUID completes with empty fields. | Defaults are an owner-authorized repair. Stock page dimensions/header behavior remain; complete layout parity is not claimed. |
| B-06 | Preview loaded and modified a Hibernate-managed design, which could flush without an explicit Save. GET and POST preview now use detached transient designs and render directly from them. | Draft and resource isolation, renderer does not resolve persisted design. | Valid unsaved Velocity renders; reopening preserves saved script; malformed preview preserves saved script; explicit Submit survives reload. | Existing preview JSP emits an unrendered `ps:exception` tag and a CodeMirror console error on the malformed path; controlled failure and persistence are verified, polished error presentation remains follow-up work. |
| C-1 | Persisted completed requests retain rendered output but no transient ReportData. Logging dereferenced the absent data. | Persisted, failed and live-data request cases. | On-demand Logging on the completed CSV request creates a processing log without the previous failure. | Other processors and eligibility validation belong to subsequent batches. |

## Reproduce

Use the existing native dependency/runtime preparation documented in `NATIVE.md`, then:

```sh
python3 scripts/prepare-backend.py --native
bash tests/run-reference-batch.sh
python3 scripts/validate.py
.venv/bin/python tests/setup-batch.py
node tests/headless-batch-one.mjs
.venv/bin/python tests/cleanup-batch.py
```

The native runtime must have these compiled modules overlaid before the browser suite runs. Tests launch Chromium in headless mode, authenticate as the existing QA administrator, and assert the frozen UTC clock. Outputs are ignored under `test-results/batch-one`: `browser-results.json`, XML/CSV output, screenshots, accessibility snapshots and iframe HTML. The source compilation and 32 Java assertions pass. The final browser run passes 9/9 checks. `cleanup-results.json` records exact owned UUID and prefix residual counts of zero for definitions, designs, processors and requests. Clinical data and historical QA records are unchanged.

Fixtures use only `QA Reports Fix R1 20261008`. Cleanup does not touch baseline report definitions; even sticker requests created against the baseline report are deleted by their exact captured request UUID. The cleanup script uses the already-running MariaDB dependency for read-only residual checks and does not build or start containers.

## Follow-up observation: edited dataset and existing report

Status: **reproduced, root cause not yet established**. After an initial 7/9 run, saving a 60-row SQL query and reloading its editor and standalone preview showed the new query and 50-row preview. Remapping that same dataset in the existing report and creating a new report request still exported 7/9. A fresh immutable dataset/report exported all 60 rows. Request UUIDs were different, so this was not merely reading the same report request. Investigate definition serialization/cache invalidation versus intentionally embedded definitions; frozen timestamps are a hypothesis, not an established cause. The immutable fixture isolates the iterator/export proof without calling this separate update behavior fixed.

## Source provenance

Reporting overrides start from the pinned Reporting 2.1.0 source in `versions.lock.json`. The sticker override starts from pinned Patient Documents 1.1.0. Original license headers are retained. Native compilation uses the just-compiled API classes before controller compilation so the preview renderer API and controller remain consistent.
