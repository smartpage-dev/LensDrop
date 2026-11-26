#!/usr/bin/env python3
"""Targeted upload-coherence and Intent name-resolution regressions.

Does not use the Android SDK. The Intent test compiles exact navigation
expressions/imports extracted from DeliveryActivity against tiny test doubles.
The mandatory Gradle assembly and lint steps remain the actual Android gate.
"""
from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location('snapshot_guard', ROOT / 'tools/github/verify_android_snapshot.py')
guard = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(guard)
MANIFEST = ROOT / guard.DEFAULT_MANIFEST
JAVA = 'clients/android/app/src/main/java/app/lensdrop/transfer/'


class SourceSnapshotTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='lensdrop-snapshot-')
        self.root = Path(self.tmp.name)
        manifest = json.loads(MANIFEST.read_text())
        for rel in manifest['files']:
            destination = self.root / rel
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / rel, destination)
        shutil.copyfile(MANIFEST, self.root / guard.DEFAULT_MANIFEST)

    def tearDown(self):
        self.tmp.cleanup()

    def check(self):
        return guard.verify(self.root, self.root / guard.DEFAULT_MANIFEST)

    def reject(self, rel, reason=None):
        result = self.check()
        self.assertFalse(result['ok'])
        self.assertTrue(any(p['path'] == rel and (reason is None or reason in p['reason']) for p in result['problems']), result)

    def test_complete_reference_accepts(self):
        result = self.check()
        self.assertTrue(result['ok'], result)
        self.assertEqual(result['checked_files'], len(json.loads(MANIFEST.read_text())['files']))

    def test_missing_recovery_activity_rejected(self):
        rel = JAVA + 'RecoveryActivity.java'; (self.root / rel).unlink(); self.reject(rel, 'missing')

    def test_missing_profile_policy_rejected(self):
        rel = JAVA + 'PrintProfilePolicy.java'; (self.root / rel).unlink(); self.reject(rel, 'missing')

    def test_missing_archive_staging_rejected(self):
        rel = JAVA + 'ArchiveStaging.java'; (self.root / rel).unlink(); self.reject(rel, 'missing')

    def test_missing_jpeg_validator_rejected(self):
        rel = JAVA + 'JpegStructure.java'; (self.root / rel).unlink(); self.reject(rel, 'missing')

    def test_stale_queue_db_rejected(self):
        rel = JAVA + 'QueueDb.java'; p = self.root / rel
        p.write_text(p.read_text().replace('void archivePolicy(', 'void oldArchivePolicy(')); self.reject(rel, 'revision')

    def test_stale_print_queue_rejected(self):
        rel = JAVA + 'PrintQueue.java'; p = self.root / rel
        p.write_text(p.read_text().replace('String mode,String archiveId', 'String mode')); self.reject(rel, 'revision')

    def test_stale_cloud_api_rejected(self):
        rel = JAVA + 'CloudApi.java'; p = self.root / rel
        p.write_text(p.read_text().replace('byte[] thumbnail(', 'byte[] oldThumbnail(')); self.reject(rel, 'revision')

    def test_missing_import_rejected(self):
        rel = JAVA + 'DeliveryActivity.java'; p = self.root / rel
        p.write_text(p.read_text().replace('import android.content.Intent;\n', '')); self.reject(rel, 'revision')

    def test_old_workflow_rejected(self):
        rel = '.github/workflows/android-debug.yml'; p = self.root / rel
        p.write_text(p.read_text().replace(' :app:lintDebug', '')); self.reject(rel, 'revision')

    def test_wrong_version_rejected(self):
        rel = 'clients/android/app/build.gradle'; p = self.root / rel
        p.write_text(p.read_text().replace('versionCode 20', 'versionCode 13')); self.reject(rel)

    def test_untracked_runtime_class_rejected(self):
        rel = JAVA + 'LeftoverOldActivity.java'; (self.root / rel).write_text('package app.lensdrop.transfer; class LeftoverOldActivity {}')
        self.reject(rel, 'unexpected')

    def test_untracked_library_rejected(self):
        rel = 'clients/android/app/libs/old.jar'; p = self.root / rel; p.parent.mkdir(parents=True); p.write_bytes(b'not a jar')
        self.reject(rel, 'unexpected')

    def test_build_outputs_and_private_local_files_ignored(self):
        for rel in ('clients/android/app/build/test.txt', 'clients/android/local.properties',
                    'clients/android/private-signing/demo.keystore', 'notes/my-notes.md'):
            p = self.root / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_text('private test placeholder')
        self.assertTrue(self.check()['ok'])

    def test_crlf_checkout_accepted(self):
        p = self.root / (JAVA + 'QueueDb.java')
        p.write_bytes(p.read_bytes().replace(b'\n', b'\r\n'))
        self.assertTrue(self.check()['ok'])

    def test_manifest_cannot_omit_critical_file(self):
        p = self.root / guard.DEFAULT_MANIFEST; value = json.loads(p.read_text())
        del value['files'][JAVA + 'RecoveryActivity.java']; p.write_text(json.dumps(value))
        self.assertFalse(self.check()['ok'])

    def test_manifest_traversal_rejected(self):
        p = self.root / guard.DEFAULT_MANIFEST; value = json.loads(p.read_text())
        value['files']['../outside.txt'] = {'sha256': '0' * 64, 'encoding': 'utf8-lf'}
        p.write_text(json.dumps(value)); self.reject('../outside.txt', 'unsafe')

    def test_source_symlink_rejected(self):
        rel = JAVA + 'QueueDb.java'; p = self.root / rel
        q = self.root / 'copy.java'; q.write_bytes(p.read_bytes()); p.unlink(); p.symlink_to(q)
        self.reject(rel, 'unsafe')

    def test_missing_manifest_rejected(self):
        (self.root / guard.DEFAULT_MANIFEST).unlink(); self.assertFalse(self.check()['ok'])

    def test_report_does_not_dump_source(self):
        rel = JAVA + 'CloudApi.java'; (self.root / rel).write_text('DO_NOT_EXPORT_THIS_TEST_SECRET')
        result = self.check(); self.assertFalse(result['ok'])
        self.assertNotIn('DO_NOT_EXPORT_THIS_TEST_SECRET', json.dumps(result))

    def test_cli_nonzero_and_json_on_failure(self):
        (self.root / (JAVA + 'ArchiveStaging.java')).unlink()
        result = subprocess.run(['python3', str(ROOT / 'tools/github/verify_android_snapshot.py'),
                                 '--root', str(self.root), '--report', 'ci-output/check.json'],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 2)
        self.assertIn('incomplete or mixed', result.stderr)
        self.assertFalse(json.loads((self.root / 'ci-output/check.json').read_text())['ok'])


class ExtractedIntentCompilationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = (ROOT / (JAVA + 'DeliveryActivity.java')).read_text()
        cls.targets = re.findall(r'startActivity\(new Intent\(this,([A-Za-z_]\w*)\.class\)\)', cls.source)

    def compile(self, source):
        self.assertEqual(set(self.targets), {'TicketActivity', 'PrintActivity', 'RecoveryActivity', 'SingleDeliveryActivity'})
        # Extract only content imports needed by the exact navigation expressions.
        imports = '\n'.join(re.findall(r'^import android\.content\.(?:Intent|\*);', source, re.M))
        statements = '\n'.join('startActivity(new Intent(this,' + name + '.class));' for name in self.targets)
        with tempfile.TemporaryDirectory(prefix='lensdrop-intent-javac-') as td:
            p = Path(td)
            values = {
                'android/content/Context.java': 'package android.content; public class Context {}',
                'android/content/Intent.java': 'package android.content; public final class Intent { public Intent(Context c,Class<?> k) {} }',
                'android/app/Activity.java': 'package android.app; public class Activity extends android.content.Context { public int launches; public void startActivity(android.content.Intent i) { launches++; } }',
                'app/lensdrop/transfer/DeliveryActivity.java': 'package app.lensdrop.transfer;\n' + imports + '\npublic class DeliveryActivity extends android.app.Activity { void probe(){' + statements + '} public static void main(String[] args){DeliveryActivity d=new DeliveryActivity();d.probe();if(d.launches!=' + str(len(self.targets)) + ')throw new AssertionError();System.out.println("PASS three extracted navigation expressions");}}',
            }
            for target in set(self.targets):
                values['app/lensdrop/transfer/' + target + '.java'] = 'package app.lensdrop.transfer; final class ' + target + ' {}'
            for rel, value in values.items():
                path = p / rel; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(value)
            output = p / 'out'; output.mkdir()
            result = subprocess.run(['javac', '--release', '17', '-d', str(output), *[str(p / rel) for rel in values]],
                                    capture_output=True, text=True, timeout=30)
            if result.returncode:
                return result.returncode, result.stderr, None
            run = subprocess.run(['java', '-cp', str(output), 'app.lensdrop.transfer.DeliveryActivity'],
                                 capture_output=True, text=True, timeout=10)
            return 0, result.stderr, run

    def test_fixed_import_compiles_all_three_extracted_calls(self):
        code, error, run = self.compile(self.source)
        self.assertEqual(code, 0, error)
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertIn('PASS three', run.stdout)

    def test_original_missing_import_reproduces_compiler_failure(self):
        code, error, run = self.compile(self.source.replace('import android.content.Intent;\n', ''))
        self.assertNotEqual(code, 0)
        self.assertIn('cannot find symbol', error)
        self.assertIn('class Intent', error)
        self.assertIsNone(run)


if __name__ == '__main__':
    unittest.main(verbosity=2)
