#!/bin/sh
# Build DSPFlow (requires JDK 11+)
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -d out $(find src -name '*.java')
# bundle non-Java resources (the matplotlib renderer) into the classpath/jar
mkdir -p out/dspflow/resources
cp src/dspflow/resources/*.py out/dspflow/resources/
jar --create --file dspflow.jar --main-class dspflow.Main -C out .
echo "Build OK. Run with: ./run.sh   (or: java -jar dspflow.jar)"
