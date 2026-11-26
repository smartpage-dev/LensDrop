#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
SRC=app/src/main/java/app/lensdrop/transfer
javac --release 17 -d "$OUT" "$SRC/TicketProtocol.java" "$SRC/SingleQuickPrintRules.java" direct-print-tests/SingleDirectPrintRulesTest.java
java -cp "$OUT" app.lensdrop.transfer.SingleDirectPrintRulesTest
python3 direct-print-tests/check_direct_print_ui.py
