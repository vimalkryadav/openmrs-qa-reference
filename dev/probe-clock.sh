#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
java_home=$(brew --prefix openjdk@21)
clock_library=$(brew --prefix libfaketime)/lib/faketime/libfaketime.1.dylib
mkdir -p "$root/.native/clock"
cc -Wall -Wextra -Werror -dynamiclib "$root/dev/macos-timed-wait.c" -o "$root/.native/clock/timed-wait.dylib"
"$java_home/bin/javac" -d "$root/.native/clock" "$root/dev/ClockProbe.java"
export FAKETIME=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["clock"])' "$root/dev/config.json")
expected=$(python3 -c 'import datetime,os; print(int(datetime.datetime.fromisoformat(os.environ["FAKETIME"]).replace(tzinfo=datetime.timezone.utc).timestamp()*1000))')
export DYLD_INSERT_LIBRARIES="$root/.native/clock/timed-wait.dylib:$clock_library"
export FAKETIME_DONT_FAKE_MONOTONIC=1 FAKETIME_FORCE_MONOTONIC_FIX=0 FAKETIME_DONT_FAKE_STAT=1 TZ=UTC
"$java_home/bin/java" -cp "$root/.native/clock" ClockProbe "$expected"
