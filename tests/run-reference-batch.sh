#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
java_home=${JAVA_HOME:-$(brew --prefix openjdk@21)}
classes="$root/.build/batch-test-classes"
mkdir -p "$classes"
classpath="$root/.build/logic-classes:$root/.build/backend-classes:$root/.build/controller-classes:$root/.build/patientdocuments-classes:$root/.build/deps/*"
"$java_home/bin/javac" -proc:none -cp "$classpath" -d "$classes" "$root/tests/ReferenceBatchRegression.java" "$root/tests/ReportingQaRegression.java" "$root/tests/LogicAdminRegression.java"
"$java_home/bin/java" -cp "$classes:$classpath" ReferenceBatchRegression

"$java_home/bin/java" -cp "$classes:$classpath" ReportingQaRegression

"$java_home/bin/java" -cp "$classes:$classpath" LogicAdminRegression
