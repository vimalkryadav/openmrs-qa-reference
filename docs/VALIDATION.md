# Validation evidence

## Reports QA corrections — 2026-10-08

The native reference at `http://localhost:8090/openmrs` was compiled from the
`fix/reports-qa-findings` worktree and replayed with headless Chromium. No Docker
image was built. The existing seeded database, `admin` credentials, port 8090 and
frozen `2026-09-29 09:00 UTC` clock were retained.

Validation passed:

- `python3 scripts/validate.py` and `git diff --check`.
- `python3 scripts/prepare-backend.py --native`: all source groups compile and
  patched Reporting/Legacy UI modules assemble.
- `./tests/run-reference-batch.sh`: 64 existing assertions plus 16 new metadata,
  date and cron validation assertions.
- `node tests/headless-reporting-qa.mjs`: 26/26 grouped UI checks. This covers the
  create-page title (002), numeric creation-date sorting (003), unsaved-preview
  guidance (004), deletion feedback (005/018), visible dataset totals (006),
  preserved mapping keys (007), report-design and history name sorting (008/019),
  period-column validation and labels (009/010), unavailable-error fallback (012),
  failed-request timestamps (015), processing status (016), history copy/filter
  controls (021/022/023), saved description persistence (024), scheduling help,
  invalid input, date round-trip, search and cron validation (025/026/027/028/038),
  missing-record guidance (030/039/040/041), name bounds (031), lossless metadata
  and trimming through two saves (033/036), 255-character report layout (034),
  invalid run-date handling (037), and retained form values after network loss
  (042).
- Separate headless header checks passed for a browser-only long-name fixture at
  683px and Spanish/Italian locale links (043/044). The server profile was not
  changed. The Legacy UI JSP override uses a distinct JSTL function prefix to
  avoid colliding with Legacy UI's own `fn` tag library.

The suite creates only `QA_REPORTS_REGRESSION_*` definitions and an owned report
request. It removes them in `finally`, including embedded Period Indicator
datasets. A read-only database check after the final run found zero definitions
with that prefix and the original 33 report requests. Screenshots, results and
UUID/cleanup ledgers are generated under ignored `test-results/reporting-qa/`.
Use `REFERENCE_ORIGIN` and `REPORTS_EVIDENCE` to change the target and output folder.
The default target must have the matching QA seed for the read-only seeded
failed/processing-history checks.

The request wrapper change is restricted to metadata name/description fields;
the global XSS filter stays enabled. JSP output and report logs escape text. The
browser round-trip checks literal ampersands, apostrophes, quotes, angle-bracket
text and the literal entity `&amp;`. Reporting ships DataTables 1.5 beta, so its
sort overrides deliberately use supported `aoColumns`, not newer `aoColumnDefs`.

This release does not claim to retest arbitrary custom evaluators, unbounded
patient reports, external processors or all unrelated OpenMRS modules. The
original audit did not reproduce every submitted defect on the reference;
previous successful renderer and bounded-report evidence below remains relevant.

## Earlier renderer and native-runtime validation — 2026-10-07

Before the image-build pause, the local reference backend and frontend were rebuilt and tested at port 8090. Nothing from this Reports release was published. A subsequent native-runtime migration compiled and deployed the Logic/Patient dataset preview fix. The final native worktree headless suite passed10/10, including an exact50-row preview assertion and frozen browser UTC time; see `native-validation.json` and `NATIVE.md`. No Docker image was built for that migration.

Headless Chromium Playwright executed nine passing workflows against the existing local reference: fixed-admin login; converter save/reload; malformed converter-map validation; converted data preview; row-per-patient column save/reload; period column label edit preserving its embedded indicator; report run with history reload and an actual CSV browser download; one-time schedule save/reload; SPA Reports dashboard. There were zero page errors, console errors or failed network requests in the completed run. `results.json` includes the explicit blocked dataset-preview case. Owned fixtures and scheduled requests were removed afterward.

Six renderers ran to COMPLETED and returned real content: CSV, TSV, XLSX, simple HTML, XML and text template. Download bytes were decoded, the workbook was opened and the scalar result was checked. Default Excel now uses `.xlsx` with the OpenXML MIME type; text templates default to `.html`. See `renderer-content-verification.json` and `renderer-live-verification.json`.

Native Logic gender, AGE and birthdate were verified on an isolated copy and the final local runtime. An invalid single-quoted expression returned a readable HTTP400 in0.21s; previously its null parse result could wait indefinitely against the frozen wall clock. Reference English birthdates display MM/dd/yyyy. Modern module startup registers stock AGE if absent and preserves an existing custom registration.

Remaining limitations: full report evaluation can still be expensive unless its base cohort is bounded; scalar SQL must keep its own SQL row bound. Every nested converter property combination, arbitrary custom Java/Groovy rules, and email/disk delivery processors have not been verified. No outbound email was sent. OCL had no configured subscription/token/schedule; no import or sync was performed. These facts prevent an honest “everything in Reports is green” claim.

Cleanup: two isolated Logic verification containers, their scratch network and owned anonymous DB volume were removed. Compiler/extraction containers use automatic removal. Final reference services and persistent DB/application volumes remain. No global Docker prune was run.

## Repeat the headless suite

Use a dedicated QA reference, not an unrelated shared environment: the setup creates temporary named definitions/reports. Install Python `httpx` in a local virtual environment and run `npm install`, then `npx playwright install chromium` once. No image build is involved.

```sh
python3 tests/setup.py
npm run test:headless
python3 tests/cleanup.py
```

Always run cleanup, including after a failed assertion. It deletes only captured UUIDs. The dataset-preview case is deliberately blocked by default while the source fix is undeployed. After an explicitly authorized image release containing that fix, use `VERIFY_DATASET_PREVIEW=1 npm run test:headless` to exercise it. `test-results/` contains screenshots and errors; `.build/test-fixtures/` contains owned UUIDs and setup responses. Do not commit them.

The source-only packaging check passed using `python3 scripts/prepare-backend.py`: seven source groups compiled and packaged using dependencies extracted from the pinned image. Official admin-tools v4.4.0 was fetched by SHA and `git apply --check` passed. Compose configuration and Bash/Python syntax validated. No Docker image build was performed for repository validation.
