# Source provenance and ownership

`versions.lock.json` pins the public upstream Git commits corresponding to each edited module and the immutable image manifests used as the distribution/dependency boundary. Fetch a source checkout with `git init`, `git fetch URL COMMIT` and `git checkout --detach FETCH_HEAD`. Do not substitute a moving main branch for the recorded SHA.

Tracked `source/` files retain upstream copyright headers and licenses. They are the editable final overrides; `scripts/prepare-backend.py` compiles them and overlays only those classes/JSPs on the pinned distribution. Logic's generated ANTLR Java parser is retained as upstream source together with its grammar. Upstream Logic test sources/resources are included for provenance but are not part of the distribution compilation; the legacy upstream test suite requires its original Maven/OpenMRS test environment.

The Reporting queue patch in `patches/reporting-2.1.0-baseline-queue.patch` documents the earlier owned change already in the pinned baseline. The entire admin-tools difference against official v4.4.0 is in `patches/esm-admin-tools-v4.4.0.patch`, including the existing OCL/admin tools changes. The frontend preparation fetches that exact public commit and applies the patch, not a developer's local branch.

The backend baseline contains other pre-existing distribution customizations (including the clock agent and earlier module fixes). They are preserved by digest, rather than silently rebuilt from different stock sources. The repository does not claim to reconstruct every byte of that historical baseline from stock upstream. Replacing that baseline requires a separate provenance/rebuild project. Current owned overrides and their compilation/package recipe are fully editable here; no unexplained developer-local path is required.

Reporting REST 2.0.0 overrides (`MappedConverter` and `ReportRequestResource`) start from pinned commit `16bf6260c30c3ec18c6e4a3b6eb02c1b9b87cca5`. Their namespace is compiled with the controllers and packaged into the separate patched reportingrest module.

The OCL 3.1.0 importer/scheduler/REST overrides and OWA 1.15.0 manager/controller/servlet overrides start from their exact tagged commits in `versions.lock.json`. Their local-only policy and paired validation are documented in `docs/OFFLINE_APPS.md`. OWA inherited University of Oslo BSD-3-Clause notices and the FileServlet GPL-3.0-or-later notice are retained in those files.

The original OCL OWA is extracted from `openconceptlab-3.1.0.omod` in the pinned input image. Patient Flags OWA removal is retained from the baseline. Seed data is an external pinned DB-image artifact, never Git source. The DB compatibility SQL and entrypoint are reviewable under `docker/db`.

Licenses: OpenMRS module/core files use their retained OpenMRS Public License or MPL notices; O3 admin-tools uses MPL-2.0. License texts are in `licenses/`. The package remains a collection of those components and project-specific build scripts, not a relicensing of upstream work. See `THIRD_PARTY.md`.

Calculation 2.0.0 overrides start from pinned upstream commit
`8cf6cdd7cd4b9c490b67cccebf57b2e71c3c882a`. The controller, validator, utility and
registration service retain their OpenMRS Public License notices. Reporting's
Calculation adapter overrides retain MPL-2.0 notices from the pinned Reporting
2.1.0 source. This legacy Calculation OMOD duplicates API classes at its root and
inside its API JAR; assembly patches both copies so the owned code is the code
actually loaded. Calculation remains independent of Reporting at class-load time;
optional saved defaults are read from the provider without adding a reverse module
dependency.


The October Reporting QA fixes extend stock Reporting 2.1.0 JSPs and tag files
captured from the pinned module under `source/web-overrides/`. Assembly includes
both `.jsp` and `.tag` overrides. `LogicReportController` preserves the upstream
row-report creation workflow with validation before persistence. The scoped
metadata reader recovers raw form text before the legacy request wrapper's HTML
encoding; its rendering templates escape values at their HTML/JavaScript sinks.
The global legacy XSS filter is unchanged.

`source/legacyui-web/template/headerFull.jsp` starts from pinned legacyui 2.1.0
and adds responsive account-header layout and locale-hint rendering. Selected
Spanish/Italian message overrides live in `source/legacyui-resources/`; assembly
merges them into the module's bundled properties and registers Italian without
replacing its other translations. These are source overlays, not generated
runtime files or a change to the reference clock/authentication settings.
