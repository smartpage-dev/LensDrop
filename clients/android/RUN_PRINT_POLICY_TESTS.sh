#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" app/src/main/java/app/lensdrop/transfer/PrintRules.java app/src/main/java/app/lensdrop/transfer/TransferLane.java policy-tests/PrintRulesTest.java
java -cp "$OUT" app.lensdrop.transfer.PrintRulesTest
