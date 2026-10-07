# Native reference development

The reference application runs from a Git worktree using host Java21/Tomcat and nginx. The current source overrides and Reports frontend are compiled locally; there is no application Docker container or image dependency at runtime. **MariaDB remains a Docker dependency**, retaining `openmrs-qa_db-data`, because migrating its large dataset into a different native database installation adds unnecessary risk.

## Prerequisites and one-time migration

On the tested Apple Silicon macOS host: Homebrew `openjdk@21`, `nginx`, `libfaketime`, Node, Python3 and Xcode command-line tools (`cc`). Native frontend compilation was tested with Node26.3.0 and the repository's vendored Yarn4.10.3. Native backend uses Homebrew JDK21.0.12.1 and the extracted Tomcat9.0.120 distribution.

For a fresh machine, obtain the pinned baseline distribution and data through the existing published images; the seed is not in Git. When migrating an existing QA stack, `python3 dev/export-runtime.py` exports its distribution, frontend and application data into ignored `.native/`, and caches compiler dependencies under ignored `.build/`. It stops the backend before copying application data and refuses to overwrite an existing native directory. It never copies/resets the MariaDB volume. This one-time migration needs the existing application containers; it is not an everyday launch command.

Expose the retained DB only on localhost:

```sh
docker compose -f compose.yaml -f dev/database-port.yaml up -d --no-build db
python3 scripts/prepare-backend.py --native
scripts/prepare-frontend.sh --native
python3 dev/runtime.py overlay
python3 dev/runtime.py start
```

The frontend preparation fetches the pinned official source and applies the tracked patch. It refuses to overwrite an existing checkout. Keep `.build/esm-admin-tools` for ordinary frontend edits/rebuilds.

## Daily commands

Run from the native worktree:

```sh
python3 dev/runtime.py status
python3 dev/runtime.py stop
python3 dev/runtime.py start
dev/rebuild-backend.sh
dev/rebuild-frontend.sh
dev/probe-clock.sh
```

`rebuild-backend.sh` compiles source before stopping the app, overlays current modules/core classes, then starts it. Compilation failure leaves the running app untouched. `rebuild-frontend.sh` type-checks/builds the editable `.build/esm-admin-tools` checkout, saves its Git diff into the tracked patch, deploys assets locally and changes the content-addressed import-map path. Hard reload the browser afterward. These commands do not invoke Docker image builds or Git pushes.

## Addresses, state and logs

- Browser: `http://localhost:8090/openmrs/spa`, login `admin` / `Admin123`.
- Internal host listeners: Tomcat8092, frontend static server8094; both bind127.0.0.1.
- MariaDB:127.0.0.1:3307, existing named Docker volume.
- Native application files/uploads/modules/index: `.native/data/`. The original `openmrs-qa_openmrs-data` Docker volume is retained as a migration backup; subsequent native file writes go to `.native/data/`.
- Logs: `.native/logs/tomcat.log`, `nginx-error.log`, `nginx-access.log`, `compile.log`, `frontend-compile.log`.
- Process identity: `.native/processes.json`. Stop verifies command identity before signalling a PID.
- Configuration: `dev/config.json`; fixed clock2026-09-29 09:00UTC, heap8GB. Native Java has no Docker10GB container limit; the host manages memory outside its heap.

Native startup updates only the two stored filesystem settings `owa.appFolderPath` and `openconceptlab.oclLoadAtStartupPath` to this worktree's native directories. Returning to a container requires restoring `/openmrs/data/owa` and `/openmrs/data/ocl/configuration/loadAtStartup` first. Database rows and credentials are preserved. Runtime properties/encryption material stay in ignored `.native/`, never Git.

## Frozen clock

`dev/config.json` supplies the time to the browser and JVM. macOS libfaketime freezes wall-clock reads, while its native pthread absolute waits otherwise compare frozen deadlines with real time. `dev/macos-timed-wait.c` translates those deadlines; relative/monotonic waits remain real. `dev/probe-clock.sh` verifies that application time stays frozen while Java monitor waits, parks and sleeps consume their expected real duration. No system clock or OS security setting is changed. This implementation is tested on Apple Silicon macOS; it is not a claim of native Windows/Linux support.

## Honest source boundary

The core WAR, Tomcat distribution, other module binaries and unrelated frontend apps are extracted pinned upstream/baseline dependencies, not rebuilt wholesale from source. Current owned overrides and the Reports frontend **are** compiled from this repository/pinned source checkout and drive the running app. Keep `.build/` dependency inputs and `.native/` runtime/data when removing old application images. Do not commit those directories, DB artifacts or credentials. Reconstructing every historical baseline component from upstream source remains a separate project.

No images were built for this migration. The old reference application containers and22 approved backend/frontend/gateway image references were removed only after native login/headless tests passed. MariaDB, its data volume, the original application-data volume, unrelated containers and tool/seed images were retained.
