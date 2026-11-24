#!/usr/bin/env python3
"""GitHub-hosted Android build support. Never contacts the LensDrop cloud.

H5 application files are not modified. A persistent key, when selected, is restored
only to the runner's default Android DEBUG keystore; it is never an artifact.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'ci-output'
ANDROID = ROOT / 'clients' / 'android'


def mode() -> str:
    value = os.environ.get('SIGNING_MODE', 'temporary-test')
    if value not in ('temporary-test', 'persistent-test'):
        raise ValueError('Unknown signing mode.')
    return value


def consent() -> None:
    if os.environ.get('ACCEPT_ANDROID_SDK_LICENSES', '').lower() != 'true':
        raise ValueError('Run workflow again and check the Android SDK license acceptance box after reading the SDK terms.')


def sdk_root() -> Path:
    value = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not value:
        raise ValueError('Android SDK not found. Use the GitHub-hosted ubuntu-24.04 runner configured by this workflow.')
    return Path(value)


def summary(message: str) -> None:
    destination = os.environ.get('GITHUB_STEP_SUMMARY')
    if destination:
        with open(destination, 'a', encoding='utf-8') as handle:
            handle.write(message + '\n')


def prepare() -> None:
    OUT.mkdir(exist_ok=True)
    # Prevent a stale APK or report committed by mistake from becoming a new artifact.
    for item in OUT.iterdir():
        if item.is_file():
            item.unlink()
    (OUT / 'setup.log').write_text('LensDrop H5 manual SDK build. No production deployment or credentials required.\n', encoding='utf-8')
    selected = mode()
    consent()
    if not (ANDROID / 'app' / 'build.gradle').is_file():
        raise ValueError('Project must be extracted: clients/android/app/build.gradle is missing.')
    summary('## LensDrop H5 Android build\nTesting only. A successful compile does not validate camera transfer or phone recovery.')
    if selected == 'temporary-test':
        summary('**Temporary signing key:** this APK is for a disposable test installation. Subsequent temporary builds may not update it. Never uninstall an app with an unfinished queue to fix signing.')
    print('Build configuration accepted. Signing mode:', selected)


def prepare_sdk() -> None:
    consent()
    sdk = sdk_root()
    executable = sdk / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager'
    if not executable.is_file():
        discovered = shutil.which('sdkmanager')
        if not discovered:
            raise ValueError('sdkmanager missing on the runner. Review the runner image, not your Hostinger settings.')
        executable = Path(discovered)
    subprocess.run([str(executable), '--sdk_root=' + str(sdk), '--licenses'],
                   input='y\n' * 200, text=True, check=True, timeout=600)
    subprocess.run([str(executable), '--sdk_root=' + str(sdk), 'platform-tools',
                    'platforms;android-35', 'build-tools;35.0.0'], check=True, timeout=900)
    if not (sdk / 'platforms' / 'android-35' / 'android.jar').is_file():
        raise ValueError('Android 35 platform installation incomplete.')
    print('Android 35 platform and build tools 35.0.0 are ready.')


def restore_signing() -> None:
    selected = mode()
    if selected == 'temporary-test':
        print('Temporary debug signing selected. No repository secret will be read.')
        return
    value = os.environ.get('LENSDROP_TEST_KEYSTORE_BASE64', '').strip()
    if not value:
        raise ValueError('Persistent mode requires the repository Actions secret LENSDROP_TEST_KEYSTORE_BASE64. See docs/github/README.md.')
    if len(value) > 64000:
        raise ValueError('Unexpected test-keystore secret size.')
    try:
        data = base64.b64decode(value, validate=True)
    except Exception as exc:
        raise ValueError('Test-keystore secret is not valid single-line Base64.') from exc
    if not data:
        raise ValueError('Test keystore is empty.')
    key = Path.home() / '.android' / 'debug.keystore'
    key.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    key.write_bytes(data)
    key.chmod(0o600)
    result = subprocess.run(['keytool', '-list', '-keystore', str(key), '-storepass', 'android',
                             '-alias', 'androiddebugkey'], capture_output=True, text=True, timeout=30)
    if result.returncode != 0 or 'PrivateKeyEntry' not in result.stdout:
        key.unlink(missing_ok=True)
        raise ValueError('Keystore is not compatible with the documented private-test alias/password. Do not put a production signing key here.')
    print('Private test keystore validated and restored. Its bytes will not be logged or uploaded.')


def collect() -> None:
    # Recheck after the tests/build, before packaging an APK. This guards an
    # accidental partial upload; it is not a signature or hardware certification.
    subprocess.run([sys.executable, str(ROOT / 'tools/github/verify_android_snapshot.py'),
                    '--report', str(OUT / 'android-source-integrity.json')],
                   check=True, timeout=30)
    selected = mode()
    source = ANDROID / 'app' / 'build' / 'outputs' / 'apk' / 'debug' / 'app-debug.apk'
    if not source.is_file() or source.stat().st_size == 0:
        raise ValueError('No debug APK produced. Review android-build.log; no APK will be fabricated.')
    verifier = sdk_root() / 'build-tools' / '35.0.0' / 'apksigner'
    result = subprocess.run([str(verifier), 'verify', '--verbose', '--print-certs', str(source)],
                            capture_output=True, text=True, timeout=120)
    (OUT / 'apk-verification.txt').write_text(result.stdout + result.stderr, encoding='utf-8')
    if result.returncode:
        raise ValueError('APK signature verification failed. See the build log.')
    destination = OUT / 'LensDrop-H5-debug.apk'
    shutil.copy2(source, destination)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    gradle = (ANDROID / 'app' / 'build.gradle').read_text(encoding='utf-8')
    name = re.search(r"versionName\s+'([^']+)'", gradle)
    code = re.search(r'versionCode\s+(\d+)', gradle)
    info = {
        'product': 'LensDrop Android H5', 'build_type': 'debug',
        'version_name': name.group(1) if name else 'unknown',
        'version_code': int(code.group(1)) if code else None,
        'application_id': 'app.lensdrop.transfer', 'minimum_android_api': 29,
        'jdk_major': 17, 'gradle': '8.11.1', 'agp': '8.9.1', 'compile_sdk': 35,
        'sdk_build_tools': '35.0.0', 'signing_mode': selected,
        'source_snapshot': 'lensdrop-android-h5.10-code16',
        'source_snapshot_manifest_sha256': hashlib.sha256((ROOT / 'ANDROID_SOURCE_SNAPSHOT_H5_10.json').read_bytes()).hexdigest(),
        'source_snapshot_verified': True,
        'git_commit': os.environ.get('GITHUB_SHA', 'local'),
        'github_run_id': os.environ.get('GITHUB_RUN_ID'),
        'github_run_attempt': os.environ.get('GITHUB_RUN_ATTEMPT'),
        'created_utc': datetime.now(timezone.utc).isoformat(),
        'apk_sha256': digest, 'physical_phone_validation': False,
        'production_approved': False,
    }
    (OUT / 'BUILD_INFO.json').write_text(json.dumps(info, indent=2) + '\n', encoding='utf-8')
    (OUT / 'SHA256SUMS.txt').write_text(digest + '  LensDrop-H5-debug.apk\n', encoding='utf-8')
    (OUT / 'README_INSTALL.txt').write_text(
        'LensDrop H5 DEBUG APK - controlled testing only.\n'
        'This file was compiled and signature-verified, not field validated.\n'
        'Use Android 10+ and the H5.5 cloud (schema 7) / H5.5 station for this workflow.\n'
        'Do not use an administrator key as the phone device token.\n'
        'Signing mode: ' + selected + '\n'
        'Temporary signing may change each run. Do NOT uninstall/clear app data to resolve a\n'
        'signature mismatch while photos or queues matter. Use persistent signing for\n'
        'repeat tests from the FIRST installation and keep that key backed up privately.\n'
        'Do not distribute a debuggable APK as a production photographer app.\n'
        'GitHub compilation does not deploy or change your Hostinger server.\n', encoding='utf-8')
    summary('APK assembled and signature-verified. Download the **LensDrop-H5-APK** artifact below.\n\n'
            'Physical camera, MediaStore, QR, lifecycle, heat and recovery tests are still required.')
    print('APK collected with SHA-256:', digest)


def cleanup() -> None:
    # Only operate automatically on disposable GitHub Actions runners.
    if os.environ.get('GITHUB_ACTIONS') == 'true':
        (Path.home() / '.android' / 'debug.keystore').unlink(missing_ok=True)
    print('Signing cleanup completed; local keys are not deleted by this command.')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'sdk', 'signing', 'collect', 'cleanup'))
    command = parser.parse_args().command
    try:
        {'prepare': prepare, 'sdk': prepare_sdk, 'signing': restore_signing,
         'collect': collect, 'cleanup': cleanup}[command]()
        return 0
    except Exception as exc:
        message = f'{type(exc).__name__}: {exc}'
        OUT.mkdir(exist_ok=True)
        with (OUT / 'setup.log').open('a', encoding='utf-8') as handle:
            handle.write(message + '\n')
        print(message, file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
