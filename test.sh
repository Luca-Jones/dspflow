#!/bin/sh
# Run the DSPFlow engine self-tests (pure Java, no JUnit). Requires JDK 11+.
set -e
cd "$(dirname "$0")"
mkdir -p out-test
# Engine + model only; the UI (Swing) is not needed for engine tests.
javac -d out-test $(find src test -name '*.java' ! -path '*/ui/*' ! -name 'Main.java')
java -cp out-test dspflow.engine.EngineTest
