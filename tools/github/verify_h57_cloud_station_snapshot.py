#!/usr/bin/env python3
"""Fail before native builds when the H5.11 cloud/station source checkout is incomplete.
No network access, no runtime secrets, no application writes.
"""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "CLOUD_STATION_SOURCE_SNAPSHOT_H5_11.json"


def verify(root=ROOT, manifest=MANIFEST):
    doc = json.loads(Path(manifest).read_text(encoding="utf-8"))
    failures = []
    for relative, expected in sorted(doc["files"].items()):
        file = Path(root) / relative
        if not file.is_file():
            failures.append("MISSING " + relative)
        else:
            actual = hashlib.sha256(file.read_bytes()).hexdigest()
            if actual != expected:
                failures.append("STALE/MODIFIED " + relative + " expected=" + expected[:12] + " actual=" + actual[:12])
    return len(doc["files"]), failures


if __name__ == "__main__":
    count, failures = verify()
    if failures:
        print("H5.11 CLOUD/STATION SOURCE SNAPSHOT FAILED; source checkout is incomplete or mixed.", file=sys.stderr)
        for issue in failures:
            print("  " + issue, file=sys.stderr)
        print("Restore the exact H5.11 source files; do not disable this gate.", file=sys.stderr)
        sys.exit(1)
    print(f"H5.11 cloud/station source snapshot OK: {count} exact files")
