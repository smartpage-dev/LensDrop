#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
SRC=app/src/main/java/app/lensdrop/transfer
javac --release 17 -d "$OUT" \
 $(find ledger-tests -name '*.java' ! -path '*/org/json/JSONObject.java') \
 reviewfix-tests/stubs/JSONObject.java reviewfix-tests/stubs/CloudApi.java \
 "$SRC/QueueDb.java" "$SRC/InstantQr.java" "$SRC/WatchLedger.java" \
 "$SRC/WatchRules.java" "$SRC/SessionRules.java" "$SRC/SingleSync.java" \
 "$SRC/DeliveryCoordinator.java" "$SRC/TicketProtocol.java" "$SRC/TicketLedger.java" \
 reviewfix-tests/H5122P1RegressionTest.java
java -Dledger.bridge="$(pwd)/ledger-tests/bridge.py" -cp "$OUT" app.lensdrop.transfer.H5122P1RegressionTest
