# Validation evidence (2026-10-07)

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
