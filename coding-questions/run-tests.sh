#!/usr/bin/env bash
# Compile everything into out/ and run all tests. Exit code 0 = all passed.
set -e
cd "$(dirname "$0")"
rm -rf out
javac -d out $(find src test -name '*.java')
java -cp out AllTests
