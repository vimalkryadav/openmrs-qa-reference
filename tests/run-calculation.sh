#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
java_home=${JAVA_HOME:-$(brew --prefix openjdk@21)}
classes="$root/.build/offline-test-classes"
mkdir -p "$classes" "$root/test-results"
classpath="$root/.build/calculation-classes:$root/.build/backend-classes:$root/.build/deps/*"
for module in "$root"/.build/backend-inputs/*.omod; do classpath="$classpath:$module"; done
"$java_home/bin/javac" -proc:none -cp "$classpath" -d "$classes" "$root/tests/CalculationConsoleRegression.java"
"$java_home/bin/java" -cp "$classes:$classpath" CalculationConsoleRegression | tee "$root/test-results/calculation-console-guards.log"
