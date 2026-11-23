#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
SRC=app/src/main/java/app/lensdrop/transfer
javac --release 17 -d "$OUT" "$SRC/StaffInvitation.java" "$SRC/SetupFlow.java" ui-tests/SetupFlowTest.java
java -cp "$OUT" app.lensdrop.transfer.SetupFlowTest
python3 ui-tests/check_ui_contracts.py
python3 ui-tests/run_scanner_contracts.py
python3 ui-tests/parse_sources.py
