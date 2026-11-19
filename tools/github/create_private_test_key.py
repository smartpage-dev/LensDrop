#!/usr/bin/env python3
"""Generate an owner-held private TEST debug signing key outside the repository.

Needs Python 3 and the JDK keytool executable. No Android SDK or network required.
The Android debug alias and passwords are the conventional test defaults; keep
this key private and do not treat it as a production release-signing setup.
"""
from __future__ import annotations
import argparse
import base64
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path.home() / 'LensDrop-private-signing')
    args = parser.parse_args()
    out = args.directory.expanduser().resolve()
    if out == ROOT or ROOT in out.parents:
        parser.error('Choose a private directory OUTSIDE this source repository.')
    keytool = shutil.which('keytool')
    if not keytool:
        print('JDK keytool is missing. Install JDK 17 (for example Eclipse Temurin), reopen Terminal, and try again.', file=sys.stderr)
        return 1
    key = out / 'lensdrop-test-debug.keystore'
    encoded = out / 'lensdrop-test-debug.keystore.base64.txt'
    if key.exists() or encoded.exists():
        print('Refusing to overwrite an existing identity. Keep your existing key for future updates.', file=sys.stderr)
        return 1
    out.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        subprocess.run([keytool, '-genkeypair', '-noprompt', '-storetype', 'JKS',
                        '-keystore', str(key), '-storepass', 'android', '-keypass', 'android',
                        '-alias', 'androiddebugkey', '-keyalg', 'RSA', '-keysize', '2048',
                        '-validity', '10000', '-dname', 'CN=LensDrop Private Test, O=LensDrop Test'],
                       check=True, capture_output=True, text=True, timeout=60)
        key.chmod(0o600)
        encoded.write_text(base64.b64encode(key.read_bytes()).decode('ascii'), encoding='ascii')
        encoded.chmod(0o600)
    except Exception as exc:
        print('Key generation failed: ' + type(exc).__name__ + '. Inspect your local JDK installation. Do not replace any existing installed-app identity.', file=sys.stderr)
        return 1
    print('Private test key created outside the repository:')
    print(key)
    print('Copy the contents of this PRIVATE file into the GitHub Actions repository secret:')
    print(encoded)
    print('Secret name: LENSDROP_TEST_KEYSTORE_BASE64')
    print('Choose persistent-test for every build from your FIRST phone installation.')
    print('Keep both files securely backed up. Do not commit, send to chat, or attach to Releases.')
    print('This is a testing identity, not a production release-signing setup.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
