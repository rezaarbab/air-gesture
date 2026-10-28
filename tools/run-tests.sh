#!/usr/bin/env bash
# Runs the pure decoders on the JVM with the Kotlin compiler already sitting in the Gradle cache.
# No Android, no Gradle, no device: ./tools/run-tests.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin"

CC=$(find "$CACHE/kotlin-compiler-embeddable" -name "kotlin-compiler-embeddable-*.jar" | sort | tail -1)
STD=$(find "$CACHE/kotlin-stdlib" -name "kotlin-stdlib-[0-9]*.jar" | sort | tail -1)
REF=$(find "$CACHE/kotlin-reflect" -name "kotlin-reflect-*.jar" | sort | tail -1)
TROVE=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps/trove4j" -name "trove4j-*.jar" | sort | tail -1)
CORO=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx" -name "kotlinx-coroutines-core-jvm-*.jar" | sort | tail -1)
ANN=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations" -name "annotations-*.jar" | sort | tail -1)

if [ -z "$CC" ] || [ -z "$STD" ]; then
  echo "kotlin compiler or stdlib not in the Gradle cache; build the app once first" >&2
  exit 1
fi
echo "compiler: $(basename "$CC")"

OUT=$(mktemp -d)
java -cp "$CC:$STD:$REF:$TROVE:$CORO:$ANN" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  "$ROOT"/app/src/main/java/ir/airgesture/decode/*.kt \
  "$ROOT"/app/src/main/java/ir/airgesture/model/*.kt \
  "$ROOT"/tools/decodertest/Main.kt \
  -classpath "$STD" -d "$OUT" -nowarn

java -cp "$OUT:$STD" MainKt
