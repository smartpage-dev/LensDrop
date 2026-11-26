#!/usr/bin/env python3
"""Fail early on an incomplete/mixed LensDrop Android source upload.

This is an accidental-mix guard, not a signature or a substitute for Gradle/lint.
It never modifies source, credentials, device data, or a live cloud deployment.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = 'ANDROID_SOURCE_SNAPSHOT_H5_12_2.json'
RELEASE = 'lensdrop-android-h5.12.2-code19'
VERSION = '0.2.0-alpha.8-h5.12.2'
APP_ID = 'app.lensdrop.transfer'
REQUIRED = {
    '.github/workflows/android-debug.yml', 'tools/github/android_ci.py',
    'tools/github/verify_android_snapshot.py', 'clients/android/app/build.gradle',
    'clients/android/build.gradle', 'clients/android/settings.gradle',
    'clients/android/app/src/main/AndroidManifest.xml',
}
for name in ('MainActivity', 'SingleDeliveryActivity', 'SingleSync', 'DeliveryActivity', 'StaffSetupActivity',
             'StaffQrScannerActivity', 'RecoveryActivity', 'ArchiveStaging',
             'PrintProfilePolicy', 'JpegStructure', 'QueueDb', 'PrintQueue', 'CloudApi', 'GalleryActivity', 'GalleryLedger', 'SlideshowActivity', 'SlideshowTicketProtocol', 'InstantQr', 'EventGalleryActivity'):
    REQUIRED.add('clients/android/app/src/main/java/app/lensdrop/transfer/' + name + '.java')
MAX_BYTES = 32 * 1024 * 1024
HEX = re.compile(r'^[0-9a-f]{64}$')


def canonical_bytes(raw: bytes, encoding: str) -> bytes:
    if encoding == 'raw':
        return raw
    if encoding != 'utf8-lf':
        raise ValueError('unsupported file encoding')
    # Do not normalize BOM, spacing, or lone carriage returns. CRLF/LF alone
    # may legitimately change when Git normalizes a Windows checkout.
    return raw.decode('utf-8').replace('\r\n', '\n').encode('utf-8')


def safe_path(root: Path, relative: str) -> Path:
    if not isinstance(relative, str) or not relative or '\\' in relative:
        raise ValueError('invalid relative path')
    p = PurePosixPath(relative)
    if p.is_absolute() or '..' in p.parts or str(p) != relative:
        raise ValueError('unsafe relative path')
    candidate = root.joinpath(*p.parts)
    for part in (candidate, *candidate.parents):
        if part == root:
            break
        if part.is_symlink():
            raise ValueError('symlinks are not allowed in the source snapshot')
    candidate.resolve().relative_to(root.resolve())
    return candidate


def verify(root: Path, manifest_path: Path) -> dict:
    result = {
        'schema': 'luce.android.source-check/1', 'release': RELEASE,
        'expected_version_name': VERSION, 'expected_version_code': 19,
        'git_commit': os.environ.get('GITHUB_SHA', 'local'),
        'checked_at_utc': datetime.now(timezone.utc).isoformat(),
        'ok': False, 'checked_files': 0, 'problems': [],
        'scope': 'Source snapshot only. Not Android compilation, lint or runtime validation.',
    }
    errors = result['problems']
    try:
        raw = manifest_path.read_bytes()
        if len(raw) > 2 * 1024 * 1024:
            raise ValueError('manifest too large')
        manifest = json.loads(raw)
        result['manifest_sha256'] = hashlib.sha256(raw).hexdigest()
        if manifest.get('schema') != 'luce.android.source-snapshot/1' or manifest.get('release') != RELEASE:
            raise ValueError('wrong source manifest/release')
        entries = manifest.get('files')
        if not isinstance(entries, dict) or not REQUIRED.issubset(entries):
            raise ValueError('manifest omits required production/workflow files')
        if len(entries) > 2000:
            raise ValueError('too many snapshot entries')
        for relative, entry in sorted(entries.items()):
            try:
                p = safe_path(root, relative)
                if not p.is_file():
                    errors.append({'path': relative, 'reason': 'missing'})
                    continue
                if p.stat().st_size > MAX_BYTES:
                    raise ValueError('source file too large')
                if not isinstance(entry, dict) or not HEX.fullmatch(str(entry.get('sha256', ''))):
                    raise ValueError('invalid digest record')
                content = canonical_bytes(p.read_bytes(), entry.get('encoding'))
                if hashlib.sha256(content).hexdigest() != entry['sha256']:
                    errors.append({'path': relative, 'reason': 'different revision or edited content'})
                result['checked_files'] += 1
            except (OSError, ValueError, UnicodeError, TypeError):
                errors.append({'path': relative, 'reason': 'unsafe or unreadable snapshot entry'})
        # Extra compile-affecting files can hide an old implementation. Runtime
        # build output/local.properties/private signing files are never inspected.
        for relative_dir in ('clients/android/app/src', 'clients/android/app/libs'):
            directory = root / relative_dir
            if directory.is_symlink():
                errors.append({'path': relative_dir, 'reason': 'unexpected symlink'})
                continue
            if not directory.exists():
                continue
            for p in directory.rglob('*'):
                if not (p.is_file() or p.is_symlink()):
                    continue
                rel = p.relative_to(root).as_posix()
                if p.name in ('.DS_Store', 'Thumbs.db'):
                    continue
                if rel not in entries:
                    errors.append({'path': rel, 'reason': 'unexpected source/resource/library file'})
        for rel in ('clients/android/build.gradle.kts', 'clients/android/settings.gradle.kts',
                    'clients/android/app/build.gradle.kts'):
            if (root / rel).exists() and rel not in entries:
                errors.append({'path': rel, 'reason': 'unexpected alternate Gradle configuration'})
        gradle = (root / 'clients/android/app/build.gradle').read_text(encoding='utf-8')
        if not re.search(r'\bversionCode\s+19\b', gradle) or f"versionName '{VERSION}'" not in gradle or f"applicationId '{APP_ID}'" not in gradle:
            errors.append({'path': 'clients/android/app/build.gradle', 'reason': 'unexpected app identity/version'})
    except (OSError, ValueError, KeyError, TypeError, UnicodeError):
        errors.append({'path': manifest_path.name, 'reason': 'missing, unsafe, or invalid manifest/configuration'})
    result['ok'] = not errors
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--manifest', type=Path, default=Path(DEFAULT_MANIFEST))
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = args.manifest if args.manifest.is_absolute() else root / args.manifest
    result = verify(root, manifest)
    if args.report:
        report = args.report if args.report.is_absolute() else root / args.report
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    if result['ok']:
        print(f"PASS source snapshot: {RELEASE}; {result['checked_files']} files; native build/lint still required.")
        return 0
    print('ERROR: incomplete or mixed LensDrop Android source. No SDK/signing/printing workaround is appropriate.', file=sys.stderr)
    for problem in result['problems'][:80]:
        print(f"  {problem['reason']}: {problem['path']}", file=sys.stderr)
    print('Restore the complete H5.12.2 Android source bundle at repository root, including hidden .github; commit and start a NEW workflow. Do not disable this check or clear app data.', file=sys.stderr)
    return 2


if __name__ == '__main__':
    raise SystemExit(main())
