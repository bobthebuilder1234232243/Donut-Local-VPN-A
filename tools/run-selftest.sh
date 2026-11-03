#!/usr/bin/env bash
# Runs the engine self-test on a plain JVM (no Android, no phone needed).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
javac -encoding UTF-8 --release 17 -nowarn -d "$OUT" app/src/main/java/com/donutvpn/donutlocalvpn/engine/*.java tools/selftest/EngineSelfTest.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" com.donutvpn.donutlocalvpn.engine.EngineSelfTest
