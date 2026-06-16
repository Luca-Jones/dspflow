#!/bin/sh
cd "$(dirname "$0")"
[ -f dspflow.jar ] || ./build.sh
java -jar dspflow.jar "$@"
