#!/usr/bin/env bash
# Fresh evidence paths preserve all earlier before/after captures and UUID ledgers.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
run_name=${1:?Usage: replay-reports.sh unique-run-name}
case "$run_name" in *[!a-zA-Z0-9_-]*) echo 'Use an alphanumeric run name' >&2; exit 2;; esac
out="test-results/replays/$run_name"
if [ -e "$out" ]; then echo 'Evidence directory already exists; choose a new run name' >&2; exit 2; fi
mkdir -p "$out"
for suite in batch-one editors objects form-errors live-update; do
    export REPORTS_EVIDENCE="$out/$suite"
    mkdir -p "$REPORTS_EVIDENCE"
    case "$suite" in
        batch-one) setup=batch; browser=batch-one; cleanup=batch;;
        editors) setup=editors; browser=editors; cleanup=editors;;
        objects) setup=objects; browser=objects; cleanup=objects;;
        form-errors) setup=form-errors; browser=form-errors; cleanup=form-errors;;
        live-update) setup=live-update; browser=live-update; cleanup=live-update;;
    esac
    printf 'Starting %s\n' "$suite"
    .venv/bin/python "tests/setup-$setup.py" > "$REPORTS_EVIDENCE/setup.log" 2>&1
    status=0
    node "tests/headless-$browser.mjs" > "$REPORTS_EVIDENCE/browser.log" 2>&1 || status=$?
    if [ "$suite" = objects ] && [ "$status" = 0 ]; then
        node tests/headless-object-extras.mjs > "$REPORTS_EVIDENCE/extras.log" 2>&1 || status=$?
        .venv/bin/python tests/decode-object-extras.py > "$REPORTS_EVIDENCE/decode.log" 2>&1 || status=$?
    fi
    .venv/bin/python "tests/cleanup-$cleanup.py" > "$REPORTS_EVIDENCE/cleanup.log" 2>&1
    if [ "$status" != 0 ]; then echo "Failed $suite; owned fixtures cleaned, inspect $REPORTS_EVIDENCE" >&2; exit "$status"; fi
    printf 'Passed and cleaned %s\n' "$suite"
done
