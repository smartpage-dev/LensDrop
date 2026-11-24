#!/usr/bin/env python3
"""No-hardware contract tests for Windows packaging and source completeness."""
from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


pkg = load_module('windows_ci_contract', ROOT/'tools/github/windows_ci.py')
source = load_module('h57_source_contract', ROOT/'tools/github/verify_h57_cloud_station_snapshot.py')


class WindowsPackagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.station = self.root/'clients/print-station'
        self.out = self.root/'ci-output'
        self.docs = self.root/'docs/h5_2'
        self.node = self.root/'node-distribution/node.exe'
        self.node.parent.mkdir(parents=True)
        self.node.write_bytes(b'fake-node')
        (self.node.parent/'LICENSE').write_text('fake license for test fixture')
        for name in ['src','public','tools','licenses','native']:
            (self.station/name).mkdir(parents=True)
        for name in ['src/main.cjs','public/index.html','tools/test.ps1',
                     'licenses/QRCode-LICENSE.txt','native/LensDropStation.csproj',
                     'README.md','THIRD_PARTY_NOTICES.md']:
            (self.station/name).write_text('fixture')
        (self.station/'package.json').write_text(json.dumps({'version':'0.1.3-alpha.h5.5'}))
        (self.docs/'calibration').mkdir(parents=True)
        for name in ['CP9550DW_SETUP.html','RELEASE_REPORT.html','CP9550_Profile_Panel.png',
                     'calibration/PHOTO_4x6_1200x1800.jpg',
                     'calibration/STRIP_2x6_SINGLE_600x1800.jpg']:
            (self.docs/name).write_bytes(b'fixture')
        pkg.ROOT=self.root
        pkg.OUT=self.out
        pkg.STATION=self.station
        pkg.PHASE='not_started'

    def test_preflight_accepts_all_inputs(self):
        pkg.preflight()
        self.assertIn('preflight', (self.out/'windows-package-steps.log').read_text())

    def test_preflight_identifies_missing_document(self):
        (self.docs/'calibration/STRIP_2x6_SINGLE_600x1800.jpg').unlink()
        with self.assertRaisesRegex(FileNotFoundError,'STRIP_2x6_SINGLE'):
            pkg.preflight()

    def test_preflight_identifies_missing_license_directory(self):
        shutil.rmtree(self.station/'licenses')
        with self.assertRaisesRegex(FileNotFoundError,'licenses'):
            pkg.preflight()

    def test_copy_dir_identifies_missing_source_after_preflight(self):
        pkg.preflight()
        shutil.rmtree(self.station/'public')
        with self.assertRaisesRegex(FileNotFoundError,'public'):
            pkg.copy_dir(self.station/'public', self.out/'copy')
        self.assertIn('copy directory: clients/print-station/public',
                      (self.out/'windows-package-steps.log').read_text())

    def test_missing_node_license_fails_closed(self):
        (self.node.parent/'LICENSE').unlink()
        with self.assertRaisesRegex(FileNotFoundError,'license'):
            pkg.node_license(self.node)

    def test_runtime_launch_error_is_logged_with_phase(self):
        self.out.mkdir()
        pkg.PHASE='validate bundled Node entrypoint'
        with patch.object(pkg.subprocess,'run',side_effect=FileNotFoundError(3,'missing path')):
            with self.assertRaisesRegex(RuntimeError,'validate bundled Node'):
                pkg.run(['not-a-program'],'node-entry-check.log')
        self.assertIn('validate bundled Node', (self.out/'node-entry-check.log').read_text())

    def test_full_package_with_simulated_tools(self):
        def fake_run(args, logfile, **kwargs):
            if 'windows-build.log' == logfile:
                staging=self.out/'windows-app'
                staging.mkdir(exist_ok=True)
                (staging/'LensDropStation.exe').write_bytes(b'fake-native')
            value = ''
            if logfile=='native-protect.log': value='protected-token'
            if logfile=='native-unprotect.log': value='original-token'
            if logfile=='native-unprotect.log':
                # The test's fake crypto returns exactly the supplied sample.
                value='SAMPLE_TOKEN'
            (self.out/logfile).write_text(value)
            return subprocess.CompletedProcess(args,0,value)
        with patch.object(pkg.sys,'platform','win32'), \
             patch.object(pkg,'run',side_effect=fake_run), \
             patch.object(pkg.shutil,'which',return_value=str(self.node)), \
             patch.object(pkg.subprocess,'check_output',side_effect=['v22.23.5\n','10.0.100\n']), \
             patch.object(pkg.secrets,'token_hex',return_value='SAMPLE_TOKEN'):
            pkg.main()
        archive=self.out/'LensDrop-PrintStation-H5-win-x64.zip'
        self.assertTrue(archive.is_file())
        import zipfile
        with zipfile.ZipFile(archive) as z:
            names=z.namelist()
            self.assertIn('LensDrop-PrintStation-H5/runtime/NODE_LICENSE.txt',names)
            self.assertIn('LensDrop-PrintStation-H5/calibration/STRIP_2x6_SINGLE_600x1800.jpg',names)
            self.assertIn('LensDrop-PrintStation-H5/public/index.html',names)
            info=json.loads(z.read('LensDrop-PrintStation-H5/BUILD_INFO.json'))
            self.assertEqual(info['version'],'0.1.3-alpha.h5.5')
            self.assertFalse(info['hardware_print_tested'])
        self.assertIn('complete: portable Windows ZIP', (self.out/'windows-package-steps.log').read_text())

    def test_source_manifest_detects_missing_and_stale(self):
        manifest = source.MANIFEST  # use the same current-release snapshot as the build gate
        count, issues=source.verify(ROOT,manifest)
        self.assertGreaterEqual(count,100)
        self.assertFalse(issues,issues)
        with tempfile.TemporaryDirectory() as td:
            r=Path(td)
            (r/'tools/github').mkdir(parents=True)
            (r/'tools/github/windows_ci.py').write_bytes(b'changed')
            mini=r/'manifest.json'
            original=json.loads(manifest.read_text())
            original['files']={
                'tools/github/windows_ci.py':original['files']['tools/github/windows_ci.py'],
                'docs/h5_2/CP9550DW_SETUP.html':original['files']['docs/h5_2/CP9550DW_SETUP.html']}
            mini.write_text(json.dumps(original))
            count, issues=source.verify(r,mini)
            self.assertEqual(count,2)
            self.assertTrue(any('STALE/MODIFIED' in s for s in issues))
            self.assertTrue(any('MISSING' in s for s in issues))


if __name__=='__main__':
    unittest.main(verbosity=2)
