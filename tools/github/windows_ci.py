#!/usr/bin/env python3
"""Build a Windows x64 portable station package. Never submits a physical print.

A staged, auditable packager: missing source paths are reported by name instead
of an unhelpful WinError 3. It does not alter station runtime or print behavior.
"""
from __future__ import annotations

import hashlib
import json
import os
import secrets
import shutil
import subprocess
import sys
import traceback
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'ci-output'
STATION = ROOT / 'clients' / 'print-station'
PHASE = 'not_started'


def phase(label: str) -> None:
    global PHASE
    PHASE = label
    OUT.mkdir(exist_ok=True)
    with (OUT / 'windows-package-steps.log').open('a', encoding='utf-8') as stream:
        stream.write(label + '\n')
    print('[windows-package] ' + label, flush=True)


def required_file(path: Path) -> Path:
    if not path.is_file():
        raise FileNotFoundError(f'Missing required file during {PHASE}: {path}')
    return path


def required_dir(path: Path) -> Path:
    if not path.is_dir():
        raise FileNotFoundError(f'Missing required directory during {PHASE}: {path}')
    return path


def run(args: list[str], logfile: str, **kwargs) -> subprocess.CompletedProcess:
    try:
        result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=1200, **kwargs)
    except OSError as exc:
        (OUT / logfile).write_text(
            f'Failed to start {args[0]!r} during {PHASE}: {exc}\n', encoding='utf-8')
        raise RuntimeError(f'Cannot launch {args[0]!r} during {PHASE}; see {logfile}') from exc
    (OUT / logfile).write_text(result.stdout, encoding='utf-8')
    if result.returncode:
        print(result.stdout)
        raise RuntimeError(f'{args[0]} failed during {PHASE}; see {logfile}')
    return result


def packaging_inputs() -> tuple[list[str], list[str]]:
    directories = ['src', 'public', 'tools', 'licenses']
    files = ['README.md', 'THIRD_PARTY_NOTICES.md', 'package.json']
    return directories, files


def preflight() -> None:
    phase('preflight: verify all station and documentation packaging inputs')
    directories, files = packaging_inputs()
    for name in directories:
        required_dir(STATION / name)
        if not any((STATION / name).rglob('*')):
            raise RuntimeError(f'Empty packaging directory: {STATION / name}')
    for name in files:
        required_file(STATION / name)
    docs = ROOT / 'docs' / 'h5_2'
    for name in ['CP9550DW_SETUP.html', 'RELEASE_REPORT.html', 'CP9550_Profile_Panel.png']:
        required_file(docs / name)
    calibration = required_dir(docs / 'calibration')
    for name in ['PHOTO_4x6_1200x1800.jpg', 'STRIP_2x6_SINGLE_600x1800.jpg']:
        required_file(calibration / name)
    required_file(STATION / 'native' / 'LensDropStation.csproj')


def copy_dir(source: Path, target: Path) -> None:
    phase(f'copy directory: {source.relative_to(ROOT)}')
    shutil.copytree(required_dir(source), target)


def copy_file(source: Path, target: Path) -> None:
    phase(f'copy file: {source.relative_to(ROOT)}')
    shutil.copy2(required_file(source), target)


def node_license(node: Path) -> Path:
    # The official setup-node distribution normally ships LICENSE alongside node.exe.
    # Different packaging may name it LICENSE.md. Do not omit the license silently.
    for name in ['LICENSE', 'LICENSE.md']:
        candidate = node.parent / name
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        f'Node redistribution license not found next to {node}. '
        'The portable package must include the official license.')


def main() -> None:
    OUT.mkdir(exist_ok=True)
    phase('start: Windows x64 portable package')
    if sys.platform != 'win32':
        raise RuntimeError('This build must run on Windows. No Windows binary was produced.')
    preflight()
    staging = OUT / 'windows-app'
    phase('prepare clean staging directory')
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir()

    phase('dotnet publish native Windows app')
    run(['dotnet', 'publish', str(STATION / 'native' / 'LensDropStation.csproj'),
         '-c', 'Release', '-r', 'win-x64', '--self-contained', 'true',
         '-p:PublishSingleFile=false', '-o', str(staging)], 'windows-build.log')
    exe = required_file(staging / 'LensDropStation.exe')

    phase('native layout smoke test')
    run([str(exe), '--layout-self-test'], 'native-layout-tests.log')
    phase('DPAPI round trip smoke test')
    sample = secrets.token_hex(32)
    protected = run([str(exe), '--protect'], 'native-protect.log', input=sample).stdout.strip()
    restored = run([str(exe), '--unprotect'], 'native-unprotect.log', input=protected).stdout.strip()
    if restored != sample:
        raise RuntimeError('DPAPI current-user round trip failed')
    for name in ['native-protect.log', 'native-unprotect.log']:
        (OUT / name).write_text(
            'PASS: ephemeral CI test data redacted; not a production credential.\n',
            encoding='utf-8')

    directories, files = packaging_inputs()
    for name in directories:
        copy_dir(STATION / name, staging / name)
    for name in files:
        copy_file(STATION / name, staging / name)

    phase('bundle Windows Node runtime and license')
    node_path = shutil.which('node')
    if not node_path:
        raise FileNotFoundError('Node executable not on PATH in Windows packaging job')
    node = required_file(Path(node_path))
    if node.suffix.lower() != '.exe':
        raise RuntimeError(f'Expected Windows node.exe, found {node}')
    runtime = staging / 'runtime'
    runtime.mkdir()
    shutil.copy2(node, runtime / 'node.exe')
    shutil.copy2(node_license(node), runtime / 'NODE_LICENSE.txt')

    docs = ROOT / 'docs' / 'h5_2'
    copy_file(docs / 'CP9550DW_SETUP.html', staging / 'START_HERE.html')
    for name in ['CP9550DW_SETUP.html', 'RELEASE_REPORT.html', 'CP9550_Profile_Panel.png']:
        copy_file(docs / name, staging / name)
    copy_dir(docs / 'calibration', staging / 'calibration')

    phase('validate bundled Node entrypoint')
    run([str(runtime / 'node.exe'), '--check', str(staging / 'src' / 'main.cjs')],
        'node-entry-check.log')
    phase('collect build provenance')
    node_version = subprocess.check_output([str(node), '--version'], text=True).strip()
    dotnet_version = subprocess.check_output(['dotnet', '--version'], text=True).strip()
    station_version = json.loads(required_file(STATION / 'package.json').read_text(encoding='utf-8'))['version']
    info = {
        'product': 'LensDrop Windows Print Station',
        'version': station_version,
        'source_package': 'H5.7.1 Windows packaging recovery',
        'geometry_smoke_passed': True,
        'architecture': 'win-x64',
        'native_target': 'net10.0-windows',
        'dotnet_sdk': dotnet_version,
        'node': node_version,
        'created_utc': datetime.now(timezone.utc).isoformat(),
        'commit': os.environ.get('GITHUB_SHA'),
        'run_id': os.environ.get('GITHUB_RUN_ID'),
        'native_compiled': True,
        'dpapi_roundtrip_passed': True,
        'hardware_print_tested': False,
        'signed_windows_binary': False,
        'production_approved': False,
    }
    (staging / 'BUILD_INFO.json').write_text(json.dumps(info, indent=2) + '\n', encoding='utf-8')
    phase('create portable Windows archive')
    archive = OUT / 'LensDrop-PrintStation-H5-win-x64.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in sorted(staging.rglob('*')):
            if file.is_file():
                z.write(file, Path('LensDrop-PrintStation-H5') / file.relative_to(staging))
    sha = hashlib.sha256(archive.read_bytes()).hexdigest()
    (OUT / 'WINDOWS_BUILD_INFO.json').write_text(
        json.dumps({**info, 'archive_sha256': sha}, indent=2) + '\n', encoding='utf-8')
    (OUT / 'WINDOWS_SHA256SUMS.txt').write_text(sha + '  ' + archive.name + '\n', encoding='utf-8')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as stream:
            stream.write('## LensDrop Windows portable build\n'
                         'Native compile, geometry assertions and DPAPI smoke passed. '
                         'No printer was accessed. Extract the inner ZIP completely; '
                         'the station starts paused in Simulation.\n')
    phase('complete: portable Windows ZIP and checksums ready')
    print('Compiled Windows package ready; no printer tests performed.')


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        OUT.mkdir(exist_ok=True)
        diagnostic = f'Packaging failed at stage: {PHASE}\n{type(exc).__name__}: {exc}\n'
        (OUT / 'windows-package-error.log').write_text(
            diagnostic + '\n' + traceback.format_exc(), encoding='utf-8')
        print(diagnostic, file=sys.stderr)
        sys.exit(1)
