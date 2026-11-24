#!/usr/bin/env python3
"""Build the Windows desktop app on a Windows runner. Never submits a print job."""
from __future__ import annotations
import hashlib, json, os, platform, secrets, shutil, subprocess, sys, zipfile
from datetime import datetime, timezone
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'ci-output'
STATION = ROOT / 'clients' / 'print-station'
def run(args: list[str], logfile: str, **kwargs) -> subprocess.CompletedProcess:
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1200, **kwargs)
    (OUT / logfile).write_text(result.stdout, encoding='utf-8')
    if result.returncode:
        print(result.stdout)
        raise RuntimeError(f'{args[0]} failed; see {logfile}')
    return result

def main() -> None:
    OUT.mkdir(exist_ok=True)
    if sys.platform != 'win32':
        raise RuntimeError('This build must run on Windows. No Windows binary was produced.')
    staging = OUT / 'windows-app'
    if staging.exists(): shutil.rmtree(staging)
    staging.mkdir()
    run(['dotnet','publish',str(STATION/'native'/'LensDropStation.csproj'),'-c','Release','-r','win-x64','--self-contained','true','-p:PublishSingleFile=false','-o',str(staging)], 'windows-build.log')
    exe = staging/'LensDropStation.exe'
    if not exe.is_file(): raise RuntimeError('Native build produced no LensDropStation.exe')
    # Pure native geometry plus crypto only. No printer/driver capture or UI launch.
    run([str(exe),'--layout-self-test'],'native-layout-tests.log')
    sample = secrets.token_hex(32)
    protected = run([str(exe),'--protect'],'native-protect.log',input=sample).stdout.strip()
    restored = run([str(exe),'--unprotect'],'native-unprotect.log',input=protected).stdout.strip()
    if restored != sample: raise RuntimeError('DPAPI current-user round trip failed')
    for name in ['native-protect.log','native-unprotect.log']:
        (OUT/name).write_text('PASS: ephemeral CI test data redacted; not a production credential.\n',encoding='utf-8')
    for name in ['src','public','tools','licenses']:
        shutil.copytree(STATION/name,staging/name)
    for name in ['README.md','THIRD_PARTY_NOTICES.md']:
        shutil.copy2(STATION/name,staging/name)
    for name in ['package.json']:
        shutil.copy2(STATION/name,staging/name)
    node = Path(shutil.which('node') or '')
    if not node.is_file() or node.suffix.lower() != '.exe': raise RuntimeError('Windows Node executable is missing')
    runtime = staging/'runtime'; runtime.mkdir()
    shutil.copy2(node,runtime/'node.exe')
    license_file = node.parent/'LICENSE'
    if not license_file.is_file(): raise RuntimeError('Node redistribution LICENSE missing from runtime distribution')
    shutil.copy2(license_file,runtime/'NODE_LICENSE.txt')
    shutil.copy2(ROOT/'docs'/'h5_2'/'CP9550DW_SETUP.html',staging/'START_HERE.html')
    for name in ['CP9550DW_SETUP.html','RELEASE_REPORT.html','CP9550_Profile_Panel.png']:
        shutil.copy2(ROOT/'docs'/'h5_2'/name,staging/name)
    shutil.copytree(ROOT/'docs'/'h5_2'/'calibration',staging/'calibration')
    run([str(runtime/'node.exe'),'--check',str(staging/'src'/'main.cjs')],'node-entry-check.log')
    node_version = subprocess.check_output([str(node),'--version'],text=True).strip()
    dotnet_version = subprocess.check_output(['dotnet','--version'],text=True).strip()
    info = {'product':'LensDrop Print Station H5.2 CP9550DW','version':'0.1.1-alpha.h5.2','geometry_smoke_passed':True,'architecture':'win-x64','native_target':'net10.0-windows','dotnet_sdk':dotnet_version,'node':node_version,'created_utc':datetime.now(timezone.utc).isoformat(),'commit':os.environ.get('GITHUB_SHA'),'run_id':os.environ.get('GITHUB_RUN_ID'),'native_compiled':True,'dpapi_roundtrip_passed':True,'hardware_print_tested':False,'signed_windows_binary':False,'production_approved':False}
    (staging/'BUILD_INFO.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
    archive = OUT/'LensDrop-PrintStation-H5-win-x64.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for file in sorted(staging.rglob('*')):
            if file.is_file(): z.write(file,Path('LensDrop-PrintStation-H5')/file.relative_to(staging))
    sha = hashlib.sha256(archive.read_bytes()).hexdigest()
    (OUT/'WINDOWS_BUILD_INFO.json').write_text(json.dumps({**info,'archive_sha256':sha},indent=2)+'\n',encoding='utf-8')
    (OUT/'WINDOWS_SHA256SUMS.txt').write_text(sha+'  '+archive.name+'\n',encoding='utf-8')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'],'a',encoding='utf-8') as f:
            f.write('## LensDrop H5.2 CP9550DW Windows build\nNative compilation, geometry assertions and DPAPI smoke test passed. No paper was printed. Download the Windows artifact, extract its inner ZIP fully, then run LensDropStation.exe. It starts paused in simulation. Physical printer and LAN validation remain required.\n')
    print('Compiled Windows package ready; no printer tests performed.')
if __name__ == '__main__':
    try: main()
    except Exception as e:
        OUT.mkdir(exist_ok=True)
        (OUT/'windows-package-error.log').write_text(str(e)+'\n',encoding='utf-8')
        print(str(e),file=sys.stderr);sys.exit(1)
