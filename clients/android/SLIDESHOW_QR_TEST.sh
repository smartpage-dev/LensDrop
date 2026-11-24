#!/bin/sh
set -eu
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
javac --release 17 -d "$OUT" app/src/main/java/app/lensdrop/transfer/TicketProtocol.java app/src/main/java/app/lensdrop/transfer/SlideshowTicketProtocol.java slideshow-tests/SlideshowTicketProtocolTest.java
java -cp "$OUT" app.lensdrop.transfer.SlideshowTicketProtocolTest
