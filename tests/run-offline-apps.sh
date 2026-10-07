#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
java_home=${JAVA_HOME:-$(brew --prefix openjdk@21)}
classes="$root/.build/offline-test-classes"
mkdir -p "$classes" "$root/test-results"
python3 "$root/tests/setup-offline-apps.py"
classpath="$root/.build/owa-classes:$root/.build/ocl-classes:$root/.build/ocl-web-classes:$root/.build/deps/*"
for module in "$root"/.build/backend-inputs/*.omod; do classpath="$classpath:$module"; done
"$java_home/bin/javac" -proc:none -cp "$classpath" -d "$classes" "$root/tests/OclOfflineRegression.java" "$root/tests/OwaOfflineRegression.java"
"$java_home/bin/java" -cp "$classes:$classpath" OclOfflineRegression | tee "$root/test-results/ocl-offline-guards.log"
"$java_home/bin/java" -cp "$classes:$classpath" OwaOfflineRegression "$root/test-results/owa-offline" | tee "$root/test-results/owa-offline-guards.log"
