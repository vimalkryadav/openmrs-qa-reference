#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
# Compile first; an error leaves the running application untouched.
python3 "$root/scripts/prepare-backend.py" --native
python3 "$root/dev/runtime.py" stop
python3 "$root/dev/runtime.py" overlay
python3 "$root/dev/runtime.py" start
