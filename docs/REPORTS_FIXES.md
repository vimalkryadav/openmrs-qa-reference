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

## Editor and renderer follow-up batch

The first batch source commit is `9b5de34`. The next local commit extends the same branch; no source has been pushed. Runtime proof uses the existing native 8090 server.

| Audit ID | Repair | Evidence and remaining limits |
| --- | --- | --- |
| B-02 | Creation accepts the blank optional specification index already emitted by Add Indicator. | Headless create, reload and one-patient count pass. |
| B-03 | Add Iteration accepts its unused blank optional index. | Headless two iterations persist and evaluate 7 and 8. |
| A-05 / C-3 | MappedEditor decodes the legacy XSS wrapper's HTML transport encoding before XML deserialization; raw XML entities are preserved. | Two focused transport tests; real chooser adds and persists patient row filter, and legacy Run with selected mapped cohort completes with one-row Gender CSV. The successful paired manifestations establish the common transport cause. |
| B-05 | New Static reporting queries own their metadata and use serialized-definition storage while referring to an existing clinical cohort. Legacy cohort-backed definitions remain readable. Editing a legacy alias in the annotated editor creates independent reporting metadata; clinical cohort writes/purges are rejected through this reporting persister. | Three focused identity-isolation assertions. Headless create/reopen/rename/delete passes. Complete selected cohort metadata and member-ID snapshots are byte-identical before/after. No clinical rows were changed. |
| A-10 | Generic resource upload uses the multipart wrapper's delegated multi-file map instead of its uninitialized inherited single-file map. | Source compiled; paired actual upload/reopen/run proof is coordinated with the clone renderer suite. A redirect alone is not the proof. |
| A-12 | Text renderer rejects missing design/template with a readable RenderingException. | Focused missing-template assertion passes; run-error UI follow-up remains separate. |
| B-06 follow-up | Preview displays its error as escaped text with an alert role, renders empty successful output, and uses a plain read-only result textarea instead of invoking unloaded CodeMirror. | Updated 9/9 headless suite proves empty/valid/malformed preview and saved-state behavior. No page errors; only the expected malformed-SQL HTTP400 appears in the event log. |
| New metadata correction | CohortDetail's workbook bytes are XLSX; filename and MIME now match, while the existing `:xls` mode argument stays usable. HTML metadata is unchanged. | Focused workbook/HTML MIME assertions pass; paired workbook decoding is coordinated with the clone suite. |

`tests/headless-editors.mjs` passes all five editor paths on fresh fixtures. Its output is `test-results/editors/browser-results.json`; `cohort-before.tsv` and `cohort-after.tsv` prove the existing clinical cohort was preserved. `tests/cleanup-editors.py` confirms zero owned definition/design/request UUID and prefix residuals. The updated focused Java suite passes 40 assertions. The rerun first-batch fixtures are also cleaned with zero recorded residual counts. Earlier exploratory selector/timing failures remain under ignored evidence and are not counted as application failures.

The direct patient-dataset preview intentionally samples the first 50 active patients before applying filters. A valid filter selecting a patient outside that sample therefore gives an empty preview; the verification fixture uses the first active patient and separately runs a full one-patient report. This is a preview scope limit, not evidence that later patient records are absent from the database.

## Object dataset editor batch

B-04 now has fixed-value controls for named, ordered columns, typed saved data definitions and parameter mappings, converter chains, ordered ascending/descending sort rules, and typed row filters. Column rename retargets its sort rule; removal removes dependent sorts. Both apps use the same inline source/mapping control contract. Dynamic whole-column/sort collections retain the existing generic Parameter mode; this change does not add a runtime widget for those structured collections.

The implementation reuses core `addColumn` adapters, mapped definitions, converters and serialized-object storage. Observation data, visit data and visit queries needed their missing typed persisters, DAO registrations and UUID serializers. No schema change or clinical-data write is involved. The annotated editor now binds to a copied definition, handles parameterized generic collection elements, validates source/type/name/sort inputs, and retains stored values on invalid submissions. Metadata mappings resolve real objects even when htmlwidgets lacks a handler (for example VisitType). Dates use the current session's accepted date format; collection controls retain their element types. Invalid JSON prevents submission and receives focus.

`tests/headless-objects.mjs` proves the three dataset variants with actual controls, save/reopen/save, Date mappings, StringConverter/AgeConverter, ordering and filters, bounded CSV results, rename/removal, Cancel and keyboard activation. Duplicate names and invalid dates return HTTP400 without changing the saved columns. The populated fixtures run through a report whose base cohort is one existing patient; SQL observation and encounter filters also limit their object IDs to two. Standalone object previews use the same first-50-active-patient sample as patient/Logic previews, and the empty result is a valid outcome with no invented headers or values.

Additional checks in `tests/headless-object-extras.mjs` cover a VisitType collection mapping across saves, malformed JSON preservation and actual query evaluation. `test-results/objects` contains the result JSONs, screenshots, accessibility trees, saved definition XML and downloaded workbooks/CSVs. Exploratory failures are kept separately and are not counted as passing evidence.

ExcelTemplateRenderer's resource-free fallback produces an XLSX workbook; its filename/MIME now match. Uploaded XLSX templates also use XLSX metadata, while OLE/XLS templates retain XLS metadata. Three focused assertions cover these paths. The headless fallback download has the correct extension/MIME, ZIP bytes and decoded cell value7.

A-09 is **currently passing, original cause unresolved**. The same `XlsReportRenderer` design is enabled, run, disabled, saved, reopened and run again. Captured database configuration shows the same design UUID with the property present then absent; decoded workbooks respectively include and omit the dataset title. This does not establish the cause of the earlier stale output or a link to the separate edited-dataset observation. See `header-*-configuration.tsv`, `metadata-cells.json` and the exact request UUIDs in `extras-results.json`.

Reproduce the new bounded batch on an overlaid native runtime:

```sh
.venv/bin/python tests/setup-objects.py
node tests/headless-objects.mjs
node tests/headless-object-extras.mjs
python3 tests/decode-object-extras.py
.venv/bin/python tests/cleanup-objects.py
```

The paired renderer evidence for commit `434c0a8` is now final: both apps passed9/9 cases in `openmrs-native-data/reports-fixes-20261008/clone-batch2/{reference,clone}-renderer-results-final.json`. Actual generic upload/reopen and CohortDetail HTML/XLSX populated/empty output are proven, with semantic comparisons in `decoded-renderer-comparison-final.json`. Malformed/unsupported cases intentionally finish FAILED with controlled errors. Earlier overwritten result files are retained but are not authoritative. The six sticker fields and barcode also match in `sticker-comparison.json`.

The final B-04 suite passes10/10 browser cases and the extra suite passes4/4, with no browser page errors. The workbook decoder confirms the fallback value and header-toggle rows. The focused Java suite passes43 assertions. Source/package validation and whitespace checks pass. `cleanup-objects.py` records zero exact owned definition/design/request UUIDs and zero R3 prefix residuals; clinical rows and pre-audit history remain untouched.
