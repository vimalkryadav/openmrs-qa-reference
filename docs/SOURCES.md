# Source provenance and ownership

`versions.lock.json` pins the public upstream Git commits corresponding to each edited module and the immutable image manifests used as the distribution/dependency boundary. Fetch a source checkout with `git init`, `git fetch URL COMMIT` and `git checkout --detach FETCH_HEAD`. Do not substitute a moving main branch for the recorded SHA.

Tracked `source/` files retain upstream copyright headers and licenses. They are the editable final overrides; `scripts/prepare-backend.py` compiles them and overlays only those classes/JSPs on the pinned distribution. Logic's generated ANTLR Java parser is retained as upstream source together with its grammar. Upstream Logic test sources/resources are included for provenance but are not part of the distribution compilation; the legacy upstream test suite requires its original Maven/OpenMRS test environment.

The Reporting queue patch in `patches/reporting-2.1.0-baseline-queue.patch` documents the earlier owned change already in the pinned baseline. The entire admin-tools difference against official v4.4.0 is in `patches/esm-admin-tools-v4.4.0.patch`, including the existing OCL/admin tools changes. The frontend preparation fetches that exact public commit and applies the patch, not a developer's local branch.

The backend baseline contains other pre-existing distribution customizations (including the clock agent and earlier module fixes). They are preserved by digest, rather than silently rebuilt from different stock sources. The repository does not claim to reconstruct every byte of that historical baseline from stock upstream. Replacing that baseline requires a separate provenance/rebuild project. Current owned overrides and their compilation/package recipe are fully editable here; no unexplained developer-local path is required.

The original OCL OWA is extracted from `openconceptlab-3.1.0.omod` in the pinned input image. Patient Flags OWA removal is retained from the baseline. Seed data is an external pinned DB-image artifact, never Git source. The DB compatibility SQL and entrypoint are reviewable under `docker/db`.

Licenses: OpenMRS module/core files use their retained OpenMRS Public License or MPL notices; O3 admin-tools uses MPL-2.0. License texts are in `licenses/`. The package remains a collection of those components and project-specific build scripts, not a relicensing of upstream work. See `THIRD_PARTY.md`.
