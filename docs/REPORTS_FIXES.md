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

## Validation and processor eligibility batch

The remaining guarded workflows now pass paired headless probes on the existing native apps. The reference overrides retain their pinned upstream source and validate before changing stored definitions/configurations.

| Audit ID | Repair and proof |
| --- | --- |
| A-07 | Resource download checks both the selected design and its resource before dereference; wrong pairs return404. |
| A-12 | The previously added missing-template exception is proven through a real failed request and readable error details. |
| A-13 | Run refreshes available rendering modes and rejects a deleted selection with a visible field error before creating a request. |
| A-14 | Date input uses non-lenient, complete-string parsing. Rejected input is retained in an accessible text/error control instead of being passed back into a widget that crashes on invalid defaults. Impossible dates and trailing junk create no request; a valid adjacent date runs successfully. |
| B-01 | Unknown, interface and abstract definition types use an explicitly handled400 response. Legacy MVC does not honor the annotation-only exception mapping here. |
| B-07 | Map keys must be nonempty before persistence. The chooser retains the selected definition and blank key, focuses the field and accepts a corrected key. |
| C-5 | Logic column names must be nonempty/unique with matching expressions; validation precedes cloning/mutation. The editor retains duplicate entries, focuses the duplicate and accepts correction. |
| C-6 | Processor names, constructible processor classes, Java properties and design references validate before mutation. The dialog retains required-field and malformed-property input, announces the error and permits correction. Invalid design UUIDs remain controlled404. |
| C-8 | One eligibility predicate gates both displayed actions and direct execution: status flags, on-demand mode, retirement and selected design. Missing UUIDs return404; ineligible processors cannot write processing logs. Success is announced only after execution succeeds. |

The paired guard evidence is `openmrs-native-data/reports-fixes-20261008/final-negatives/{reference,clone}-negative-results-final.json`. It covers14 cases across the nine IDs; initial failures and corrected reruns are retained separately. Two page errors from the earlier broad reference probe are retained in that evidence rather than silently discarded. The subsequent focused editable-form suite has **zero page errors**.

`tests/headless-form-errors.mjs` proves five recoverable form cases, including correcting and saving each rejected value. `test-results/form-errors/results.json` is the final5/5 result, with screenshots and accessibility trees. Its first exploratory dimension attempt had a chooser-navigation race; the final script waits for the selected-definition navigation before editing. The shared JSP submit handler keeps malformed server-validated properties in the existing dialog; field errors use alert roles, `aria-invalid`, descriptions and focus. The cleanup ledger proves all exact R6 definition/processor UUIDs are gone.

`tests/headless-live-update.mjs` separately verifies the older stale-data observation. Both SqlDataSetDefinition and IterableSqlDataSetDefinition now export7/9, then11/13 after editing the same definition, then all60 rows after another save, using new requests and the same containing report. `test-results/live-update/results.json` and downloaded CSVs contain the request identities and outputs. **Currently passing; original cause unresolved.** No cache-related source fix is claimed. All exact R5 definitions/designs/requests are cleaned.

The source gate passes54 focused Java assertions, including strict dates and status/mode/design processor eligibility, plus native compilation, source/package validation and whitespace checks. No image was built or published, and no branch was pushed.

## Approved Reports baseline reconciliation

After proof fixtures were removed, `tests/reconcile-reporting-baseline.py` compared the complete current Reports UUID sets against the immutable seed and the pre-audit snapshot. It refused unknown/missing rows and changed target metadata, checked external dependencies, and then removed only the approved five old QA definitions, one design and two requests through the existing APIs. The original seed was opened with SQLite read-only/immutable mode and never modified. Clinical tables and configuration were untouched.

`test-results/baseline-reconciliation/applied.json` records names/UUIDs, before metadata, dependency checks, each deletion and the final exact seed UUID-set matches:6 serialized definitions,2 designs,33 requests,1 processor. The earlier B4 cleanup snapshot intentionally predates this separately authorized baseline change.

## Approved clinical baseline reconciliation

After explicit authorization to remove reference-only clinical test data, `tests/reconcile-clinical-baseline.py` compared reviewed UUID roots and their complete incoming foreign-key dependency closure against the immutable large `data.db` seed. It rejects any seed primary key or UUID, unexpected key-set drift, non-InnoDB target, trigger, unaccounted conventional foreign-key-less reference, changed target snapshot or disabled FK enforcement. The dry run is read-only; `--rollback` executes and verifies the entire transaction without committing; `--apply` commits only after all checks pass. The test environment requires PyMySQL1.1.2.

The applied transaction removed98 exact extra/dependent rows across15 tables, including8 people/patients,3 encounters,1 new visit and4 additional flags. All15 affected tables now have exactly the seed primary-key/UUID sets. The existing seed patient and visit referenced by an extra encounter retained all native field values. The original seed file was unchanged. This is a key-set and explicitly scoped parent-field proof, not a claim that every field of every table is identical between database engines.

`test-results/clinical-reconciliation/{rollback,apply}.json` retains bounded per-row recovery metadata, dependency edges, action counts and streamed key-set digests. `post-restart.json` confirms core counts and flags remain aligned after the actual canonical native reload. The baseline is person1392354,patient1383925,provider8429,encounter388980,cohort935,visit125047 and patientflags_patient_flag6553. No FK checks were disabled and no clinical row was selected by a numeric high-water cutoff.

Example invocation (reviewed evidence stays local and is not committed):

```sh
.venv/bin/python tests/reconcile-clinical-baseline.py --seed "$SEED_DB" --evidence "$REVIEWED_TARGETS" --rollback
.venv/bin/python tests/reconcile-clinical-baseline.py --seed "$SEED_DB" --evidence "$REVIEWED_TARGETS" --apply
```

## Structured runtime columns and sorting

Obs, Visit and Encounter-and-Obs datasets now accept whole column lists and sort criteria at legacy Preview and report Run. The same named row controls resolve saved definition UUIDs, typed mappings and converters. Runtime evaluation uses a copy, preserving the saved dataset. Report parameters can declare the two supported types through the actual parameter dialog; incompatible shared targets and invalid column/sort combinations fail before a request is queued. Other arbitrary structured Java types remain unsupported.

Request mappings persist native values and expose bounded JSON DTOs through Reporting REST, allowing Copy and hard reload to restore them without Java object recursion. The Encounter-and-Obs evaluator copies its columns before adding its internal observation column, so this helper never contaminates copied requests. Date mappings accept strict ISO dates or the session date format.

`test-results/runtime-parameters-release/results.json` records 13 passing headless cases across all three dataset families, actual parent-parameter creation and mapping, request/CSV/Copy roundtrips, sort-only input and invalid/empty cases. Cleanup verifies every owned definition, design and request UUID is absent. The focused Java regression has 63 assertions. These results establish the legacy workflow; the additional O3 proof is described below.

`tests/replay-reports.sh` runs the earlier focused suites with fresh evidence paths and exact cleanup between suites, preserving all original audit and repair captures. Its first replay caught a missing bounded-preview notice in the new parameter JSP; the existing patched JSP was retained as the sole template source and the notice restored before the final replay.

The separately approved QA312 concept cleanup reused the guarded reconciliation script: three exact nonseed rows removed after a successful rollback rehearsal, with all remaining affected-table key/UUID sets unchanged. Both attachment compatibility concepts are preserved; details are recorded in `docs/OFFLINE_APPS.md`.

## Final O3 consumers and integration proof

O3 Run, scheduled-report editing and Webview now render the two supported structured types as labeled JSON text areas, with examples, array/object shape checks, duplicate-name checks and retained invalid drafts. The legacy editor retains its named row controls. Request summaries serialize structured values as JSON; schedule updates preserve native values through the same REST parser. Webview uses that parser and a bounded context representation rather than recursively serializing Java definition graphs. Unknown fields are rejected by the backend as well as the form.

The O3 headless suite verifies malformed/unknown JSON, Run and decoded two-row CSV, hard-reloaded summaries, schedule creation/reopen/correction/resave, legacy Copy of an O3 request, and Webview. `test-results/runtime-o3-final/o3-results.json` has four passing grouped cases, keyboard traversal and zero page errors; initial harness URL/selector/timing mistakes are retained as separate attempts. `tests/headless-runtime-o3.mjs` captures the editable controls and checks keyboard traversal in its final screenshot replay; all exact final-replay fixture UUIDs are cleaned.

`tests/prove-runtime-scheduler.py` additionally let the existing native scheduler enqueue an owned one-time schedule at the frozen minute. The real queued request completed, retained its typed mappings and exported the expected descending rows. No clock, global service binding or task instrumentation changed. The unrelated seeded schedule was unchanged. `test-results/runtime-o3/scheduler-results.json` retains the raw proof, including a redundant second cleanup DELETE that returned500 after successful removal; `cleanup-results.json` proves all owned schedule/request/design/definition UUIDs absent. That redundant request inherited `Accept: text/html` from the legacy harness; the identical request with the normal REST `Accept: application/json` returns404 (`duplicate-delete-json-response.json`). The helper now requests JSON and checks absence before repeating deletion. No ordinary O3 deletion failure was reproduced.

The fresh replay `test-results/replays/final-20261008-r2/` passes the original first9, editor5, object10 plus4 extra, inline5 and live-update2 cases, with exact cleanup after every suite. The focused Java regression passes64 assertions. `test-results/final-runtime/artifact-proof.json` ties canonical runtime PIDs, WAR and nine loaded module hashes to compiled artifacts, and verifies preserved clinical counts and the two attachment compatibility concepts. The frontend content address now hashes the entire compiled bundle, including lazy chunks, preventing a changed chunk from retaining an unchanged entrypoint-only cache key.

Proof screenshots were inspected and removed after validation as required by the workspace cleanup policy. JSON assertions, HTML/accessibility captures, decoded downloads, cleanup ledgers and original pre-fix audit evidence remain; historical screenshot paths identify the reviewed capture, not a retained deliverable.

## O3 review follow-up: loading guards and blank structured parameters

The schedule editor now derives Save readiness from the loaded definition and, for an existing schedule, the matching loaded request. Both the disabled button and the submit handler enforce this boundary, so pending metadata cannot bypass parameter validation or submit uninitialized values. Structured parameter validation and serialization share blank semantics: optional missing, null, empty and whitespace-only values become `[]`; required blank values remain invalid. Existing typed arrays and unrelated parameter values are preserved.

Nine focused Vitest cases in the frontend patch cover both structured types, blank/required behavior, typed arrays and malformed JSON. The native source type-check and production frontend compilation passed. `node tests/headless-runtime-o3-edges.mjs` passed two live headless cases, independently holding the definition response and existing request response, attempting a forced submit while blocked, then saving the same schedule UUID with its memo preserved. The fixture uses a future schedule and never evaluates a clinical query. Its native parameters are required: required whitespace is rejected and `[]` saves. Optional-whitespace behavior is covered by the helper tests, not misrepresented as a live optional fixture. The stock parameter editor does not expose a required/optional control.

Authoritative local evidence is `test-results/runtime-o3-edges-final/{results,cleanup,definition,definition-request,request-request}.json`, with accessibility captures. Earlier attempts retain fixture/harness diagnostics separately; every owned UUID and the unique fixture prefix are absent after cleanup. Only frontend assets changed in the running reference; backend modules and processes were preserved. The source-owned frontend patch, reproducible unit test and headless script are committed; generated bundles and raw local evidence remain untracked.

```sh
# After preparing the pinned editable frontend checkout:
cd .build/esm-admin-tools/packages/esm-reports-app
node ../../node_modules/vitest/vitest.mjs run src/components/structured-parameter.test.ts
node ../../node_modules/typescript/bin/tsc --noEmit
```
