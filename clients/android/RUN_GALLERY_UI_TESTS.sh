#!/bin/sh
set -eu
cd "$(dirname "$0")"
python3 gallery-tests/check_gallery_ui.py
