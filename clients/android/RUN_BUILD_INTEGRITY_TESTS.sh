#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
python3 tools/github/test_android_snapshot.py
