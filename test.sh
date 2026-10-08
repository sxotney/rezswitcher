#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

LIB=tools/lib
mkdir -p "$LIB"
[ -f "$LIB/junit-4.13.2.jar" ] || curl -fsSL -o "$LIB/junit-4.13.2.jar" \
  https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar
[ -f "$LIB/hamcrest-core-1.3.jar" ] || curl -fsSL -o "$LIB/hamcrest-core-1.3.jar" \
  https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
CP="$LIB/junit-4.13.2.jar:$LIB/hamcrest-core-1.3.jar"

rm -rf build/test
mkdir -p build/test
javac -d build/test -cp "$CP" $(find app/src -path '*/core/*.java') $(find test -name '*.java')
CLASSES=$(cd test && find . -name '*Test.java' | sed 's|^\./||; s|\.java$||; s|/|.|g')
java -cp "build/test:$CP" org.junit.runner.JUnitCore $CLASSES
