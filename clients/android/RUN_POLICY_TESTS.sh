#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
javac -d "$OUT" app/src/main/java/app/lensdrop/transfer/SessionRules.java policy-tests/SessionRulesTest.java
java -cp "$OUT" app.lensdrop.transfer.SessionRulesTest
