#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
SRC=app/src/main/java/app/lensdrop/transfer
javac --release 17 -d "$OUT" $(find ledger-tests workflow-tests -name '*.java') "$SRC/GalleryLedger.java" "$SRC/QueueDb.java" "$SRC/InstantQr.java" "$SRC/WatchLedger.java" "$SRC/WatchRules.java" "$SRC/SessionRules.java" "$SRC/PrintQueue.java" "$SRC/RecoveryLedger.java" "$SRC/ArchiveStaging.java"
java -Dledger.bridge="$(pwd)/ledger-tests/bridge.py" -cp "$OUT" app.lensdrop.transfer.GalleryLedgerTest
