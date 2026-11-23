#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" app/src/main/java/app/lensdrop/transfer/PrintProfilePolicy.java policy-tests/PrintProfilePolicyTest.java
java -cp "$OUT" app.lensdrop.transfer.PrintProfilePolicyTest
