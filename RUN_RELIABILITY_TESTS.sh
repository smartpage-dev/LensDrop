#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
SRC=app/src/main/java/app/lensdrop/transfer
javac --release 17 -d "$OUT" $(find ledger-tests -name '*.java') $(find reliability-tests -name '*.java') "$SRC/QueueDb.java" "$SRC/InstantQr.java" "$SRC/WatchLedger.java" "$SRC/WatchRules.java" "$SRC/SessionRules.java" "$SRC/RecoveryLedger.java" "$SRC/PrintQueue.java" "$SRC/UploadRetryPolicy.java" "$SRC/JpegStructure.java"
java -cp "$OUT" app.lensdrop.transfer.JpegReliabilityTest
java -Dledger.bridge="$(pwd)/ledger-tests/bridge.py" -cp "$OUT" app.lensdrop.transfer.RecoveryReliabilityTest
