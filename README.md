# OpenMRS QA reference

Editable source patches and release tooling for the **real OpenMRS reference at http://localhost:8090/openmrs/spa**. The clone lives in a separate repository and is not built here. This is a private QA system with de-identified demonstration data, not a clinical deployment.

## Native development (current local mode)

The application now runs directly from a dedicated repository worktree: native Java21/Tomcat and nginx, with the owned Java patches and Reports frontend compiled locally. Only MariaDB remains in Docker to preserve the large existing database. No application image is needed after the one-time extraction. See [native setup, commands and limitations](docs/NATIVE.md). The older published-image instructions below remain available for explicitly requested image deployments.

```sh
python3 dev/runtime.py start
python3 dev/runtime.py status
python3 dev/runtime.py stop
dev/rebuild-backend.sh
dev/rebuild-frontend.sh
```

These commands create no Docker images and push nothing. Do not run `start` if the native services are already running.

## Published image status

- Current QA release: `2026-10-08-module-parity-r1`, including the merged Reports, offline OCL/OWA and Calculation fixes. Immutable amd64/arm64 digests, source commit and validation details are in `releases/2026-10-08-module-parity-r1.json`.
- The backend Dockerfile explicitly preserves executable startup permissions. This was verified through actual container startup, which native source checks alone did not cover.
- The database and gateway reuse the pinned large-data baseline. Fresh volumes have the verified seed counts and 33 report requests; existing QA volumes are preserved rather than reset.
- Previous portable release: `2026-10-07-legacy-qa`. Its manifest remains available for rollback.
- Image builds, publication and deployment happen only when explicitly requested. A Git push does not trigger them. The workflow has only `workflow_dispatch` and defaults to validation without publication.

## QA: update to an already-published release

Install Docker Desktop with at least 16 GB assigned memory. The backend has an 8 GB heap and 10 GB container limit; MariaDB has a 3 GB limit. Keep these settings for the large dataset.

```sh
git clone https://github.com/vimalkryadav/openmrs-qa-reference.git
cd openmrs-qa-reference
git pull --ff-only
scripts/qa-update.sh 2026-10-08-module-parity-r1
```

The update script runs `docker compose pull`, then `up -d --no-build`. It uses project `openmrs-qa`, preserving the existing `openmrs-qa_db-data` and `openmrs-qa_openmrs-data` volumes. It never runs `down -v`. First initialization creates those volumes from the pinned images. On an existing laptop, check the project and volume names before adopting a different stack. A manual local Reports override is not automatically used by this script: select a published release consciously.

On first O3 login, select a location and click **Confirm** if prompted. Login remains `admin` / `Admin123`; port remains **8090**; the frozen clock remains **2026-09-29 09:00 UTC**. Fixed demonstration DB credentials are embedded in the pinned baseline, not private service credentials. Do not add real credentials to Git.

`git pull` updates source and scripts only. It cannot replace compiled modules or a running frontend. Developers must explicitly prepare and release images before QA can pull the new code. Roll back application images using the same update command with a previously published tag. This preserves data; SQL/data upgrades are not undone by image rollback. Take a local volume backup before an authorized DB upgrade and keep that backup outside this repository.

## Developer workflow

1. Create a branch, make a focused source change, and record the user-visible behavior and tests in the PR.
2. Run `python3 scripts/validate.py` (syntax, manifest, manual-only workflow and prohibited artifact checks).
3. For Java changes run `python3 scripts/prepare-backend.py`. It extracts dependencies from digest-pinned baseline images, compiles tracked Java and packages `.omod` files under ignored `.build/`. It does **not** build or deploy a Docker image. Temporary extraction/compiler containers are removed automatically.
4. For frontend changes edit the patch against the pinned upstream checkout; run `scripts/prepare-frontend.sh` to fetch the exact commit, apply the patch, run TypeScript and compile Reports assets. This also does not build an image. Keep source edits in `patches/`, not generated `.build/`.
5. Review the PR and merge source. When the owner requests a release, follow [release instructions](docs/RELEASING.md). No automatic deployment occurs.

The build recipe is an overlay on the exact `2026-10-07-legacy-qa` baseline. Those images supply the OpenMRS distribution, bundled modules, original OCL OWA and data snapshot. This repository contains editable sources for the current owned Reports/Logic/cohort/core/legacyui/patientdocuments changes and the DB compatibility patch. It is **not** a from-scratch build of every upstream module or a copy of the database. [Source ownership and dependencies](docs/SOURCES.md) explains this boundary and the pinned fetch recipes.

## Source map

| Location | Purpose |
|---|---|
| `source/controller-src`, `source/backend-src` | Reporting controllers, evaluation helpers, cohort evaluators and renderers |
| `source/web-overrides`, `source/reporting-web` | Legacy JSP forms, converter controls, preview UI |
| `source/logic-source` | Logic 0.5.5 source with current OpenMRS lifecycle and demographic compatibility fixes |
| `source/cohort-src`, `source/cohort-web` | Legacy cohort dashboard pagination, search and editing |
| `source/core-src`, `source/legacyui-src`, `source/patientdocuments-src` | Small upstream compatibility overrides |
| `patches/esm-admin-tools-v4.4.0.patch` | Reports SPA and existing OCL/admin-tools source changes against pinned upstream |
| `docker/db` | Idempotent settings/concept mapping patch and entrypoint; large seed stays external |
| `versions.lock.json` | Immutable baseline image digests and source commits |
| `scripts` | Source preparation, explicit image release, QA pull-only update |

See [validation coverage and remaining limits](docs/VALIDATION.md). Passing listed workflows is not a claim that every custom Groovy rule, converter class or outbound delivery processor has been exercised.
