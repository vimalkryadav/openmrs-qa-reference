# Calculation registration and local evaluation flows

The same workflows run on real OpenMRS at `:8090` and the clone at `:8093`.
Route: `/openmrs/module/calculation/calculationRegistrations.list`.
Use an administrator session on the reference. Keep the frozen clock and seed unchanged.
The test console accepts at most 1,000 existing active patients. It evaluates installed
classpath and Reporting definitions; it does not simulate missing Groovy implementations.

| Flow | Action | Expected result | Real | Clone |
| --- | --- | --- | --- | --- |
| CALC-01 Seeded registrations and provider choices are available | Open Calculation Registrations and the new-registration form. | The four seed tokens and installed local provider choices are available. | passed | passed |
| CALC-02 Required registration validation retains the editable form | Save an empty registration. | Required-field errors retain the editable form. | passed | passed |
| CALC-03 Unavailable implementation cannot be registered | Try registering a missing local Groovy calculation. | The provider is rejected with an honest unavailable message; no registration is saved. | passed | passed |
| CALC-04 Create registration persists after reload | Create an owned PatientIdCalculation registration, then reload. | The saved token and provider persist. | passed | passed |
| CALC-05 Duplicate token is rejected | Create another registration using the same token. | Duplicate validation preserves the original registration. | passed | passed |
| CALC-06 Edit registration persists | Rename the owned token, save, then reload. | The edited token persists and the previous token is replaced. | passed | passed |
| CALC-07 Explicit patient selection is ordered and deduplicated | Enter two active patient IDs out of order, with one repeated. | Actual patients are evaluated once each, in ascending order. | passed | passed |
| CALC-08 First-N selection is deterministic and bounded | Request the first two active patients; then submit negative, oversized and nonnumeric counts. | Selection is deterministic; counts outside the whole-number range 0–1000 show errors. | passed | passed |
| CALC-09 Unknown and malformed patient inputs are visible errors | Submit an unknown patient and malformed patient text. | No invented result is returned; the invalid text stays in the form. | passed | passed |
| CALC-10 Empty cohort has an explicit result state | Evaluate a zero-patient cohort. | An explicit no-results state appears. | passed | passed |
| CALC-11 Date parameter changes real age without retaining old defaults | Evaluate age at the frozen date, then six years earlier; reload and open a fresh form. | Real age changes by six years; the saved definition retains its original behavior. | passed | passed |
| CALC-12 Typed number parameter evaluates submitted values | Register the owned Integer parameter definition; evaluate 42 and then 7. | Each submitted numeric value appears in the result. | passed | passed |
| CALC-13 Typed boolean parameter evaluates submitted values | Evaluate the owned Boolean parameter with True and False. | Both choices produce the corresponding real result. | passed | passed |
| CALC-14 Typed text parameter evaluates submitted values | Evaluate plain text containing angle brackets, then change it. | Actual text is escaped and updates; it is never interpreted as markup. | passed | passed |
| CALC-15 Metadata collection returns real identifiers rather than object hashes | Select the local identifier type for an owned identifier calculation. | The result contains real patient identifiers in a collection, without an object hash. | passed | passed |
| CALC-16 Delete cancellation and confirmation work | Cancel deletion, then confirm it and revisit the deleted registration. | Cancel preserves it; confirmation deletes it; the resource read returns 404. | passed | passed |
| CALC-17 Remove remaining owned registrations and preserve seed registrations | Delete every remaining owned registration and revisit the list. | All four seed registrations remain; no owned registration remains. | passed | passed |
| CALC-18 No external requests or page errors | Intercept nonlocal HTTP(S) throughout the headless run. | Zero external requests and zero browser page errors. | passed | passed |
| CALC-19 Invalid typed values retain inputs and explicit patients take precedence | Submit bad and missing numeric values, impossible/trailing-junk dates, and valid explicit patients with an invalid first-N count. | Errors retain their inputs; explicit patients take precedence and evaluate. | passed | passed |
| CALC-20 Numeric collections preserve typed members | Add two Integer collection entries, 1 and 2, and evaluate. | The real typed collection renders [1, 2]. | passed | passed |
| CALC-21 Static metadata collections display local names | Select a database location in a static metadata collection and evaluate. | The actual local location name appears instead of a UUID or identity hash. | passed | passed |

Reproduce from the reference fixes repository using `tests/setup-calculation.py` for each
origin, `tests/calculation-state.py before`, and `node tests/headless-calculation.mjs
reference` / `clone`. `tests/calculation-state.py after` verifies saved-definition hashes.
Then run `tests/cleanup-calculation.py <target> --definitions` and
`tests/calculation-state.py clean` to prove owned rows are gone and the four seed tokens
remain. Python helpers use the existing environment with `httpx`; no image is built.

Focused Java assertions cover pre-query bounds, strict dates, required/default metadata,
real collections and copied evaluation parameters. Focused Python tests additionally cover
required/default conversion, enums, unavailable providers and definition immutability.
A final native Boolean supplement verifies checked state after reload, invalid-markup recovery and keyboard Space selection against the recorded template hash. Reproduce with `tests/setup-calculation.py reference --boolean-only`, `node tests/headless-calculation.mjs reference --boolean-only`, then `tests/cleanup-calculation.py reference --definitions --boolean-only`.
Browser interception proves local-only browser traffic; Calculation evaluation uses local
DB/service providers and does not introduce a remote client. OCL/OWA backend sentinels
are covered in the separate offline-app ledger.

## Automatic Registration supplement

Five additional paired groups use the actual live browser UI, bringing the catalogue to 26 groups per application. They verify preview/Cancel, Use existing, creation of a missing suggestion, and meaningful Overwrite after changing an existing target. No isolated provider or mocked save path is used.

| Flow | Action | Expected result | Real | Clone |
| --- | --- | --- | --- | --- |
| CALC-A01 | Open Automatic Registration, inspect defaults, then Cancel. | The three live suggestions appear; Cancel preserves every registration field. | passed | passed |
| CALC-A02 | Select Reporting Data Items with Use existing and submit. | All three suggested seed registrations are skipped; every row is unchanged. | passed | passed |
| CALC-A03 | With an exact four-row recovery snapshot, remove age through the UI and apply suggestions. | One missing age registration is created with a new identity and correct provider target; the other rows remain unchanged after reload. | passed | passed |
| CALC-A04 | Temporarily change gender to a different valid provider target, then apply Overwrite. | The suggested gender target replaces the changed target while preserving its original UUID; three replacements are reported. | passed | passed |
| CALC-A05 | Delete the newly created age row through the UI, restore the exact snapshot, and reload. | All original registration columns/identities and all saved-definition hashes match; no external browser attempts or page errors occur. | passed | passed |

Run `node tests/headless-calculation-auto.mjs reference` and the same command with `clone` only when no other registration proof is active. The helper requires exactly the four baseline tokens, snapshots all stored columns and the six definition hashes, and refuses to overwrite an earlier recovery snapshot. The UI temporarily removes/recreates `age` and changes then overwrites `gender`; cleanup deletes the newly created registration through the service (clearing its cache) before a guarded transaction restores the original rows. Both applications ended with every original column, integer key and UUID restored and all definition hashes unchanged. Auto-increment counters are not rewound. Clinical tables are never written. The ignored `test-results/calculation-auto/<target>/` directory contains recovery, creation, overwrite, accessibility and result ledgers.
