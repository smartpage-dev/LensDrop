import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import time
import unittest

CLIENT=Path(__file__).resolve().parents[1]/'desktop'/'lensdrop_agent.py'
spec=importlib.util.spec_from_file_location('h2_agent',CLIENT)
agent=importlib.util.module_from_spec(spec);spec.loader.exec_module(agent)

class OperationsQueueTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.q=agent.Queue(self.root/'state');self.folder=self.root/'source';self.folder.mkdir()
    def tearDown(self):
        self.q.close();self.tmp.cleanup()
    def jpeg(self,name='test.jpg'):
        p=self.folder/name;p.write_bytes(b'\xff\xd8local-test\xff\xd9');return p
    def test_preflight_stays_separate_from_production_counts_and_scanning(self):
        p=self.jpeg();jid=self.q.add_preflight(p);self.q.scan(self.folder,0);self.q.scan(self.folder,0)
        s=self.q.snapshot();self.assertEqual(s['production']['received'],0);self.assertEqual(s['preflight']['received'],1);self.assertEqual(self.q.add_preflight(p),jid)
    def test_cannot_relabel_existing_production_as_test(self):
        p=self.jpeg();self.q.scan(self.folder,0);self.q.scan(self.folder,0)
        with self.assertRaises(ValueError):self.q.add_preflight(p)
    def test_source_folder_binding_cannot_be_repointed(self):
        self.q.bind_source(self.folder);self.q.bind_source(self.folder)
        with self.assertRaises(ValueError):self.q.bind_source(self.root/'another-event')
    def test_empty_queue_is_idle_not_stalled(self):
        s=self.q.snapshot();self.assertFalse(s['production']['attention_no_progress']);self.assertFalse(s['camera_card_complete']);self.assertFalse(s['source_originals_may_be_deleted'])
    def test_error_exports_do_not_include_sensitive_raw_errors_or_source_paths(self):
        self.jpeg('PRIVATE_PERSON.JPG');self.q.scan(self.folder,0);self.q.scan(self.folder,0);jid=self.q.due()['id']
        self.q.set(jid,state='blocked',error='Bearer SECRET_TOKEN /private/PRIVATE_PERSON.JPG')
        report=self.q.diagnostics();raw=json.dumps(report)
        for s in ['PRIVATE_PERSON','SECRET_TOKEN',str(self.folder)]:self.assertNotIn(s,raw)
        self.assertEqual(report['error_counts'],{'other_error':1})
    def test_old_server_cannot_silently_publish_preflight(self):
        self.q.add_preflight(self.jpeg())
        class OldServer:
            calls=[]
            def call(self,path,*args):
                self.calls.append(path)
                if path=='/v1/device':return {'event':{'id':'old-server'}}
                raise AssertionError('Should not create an upload on H1')
        api=OldServer();self.assertFalse(agent.Agent(self.q,api,0).one(self.q.due()))
        self.assertEqual(api.calls,['/v1/device']);self.assertEqual(self.q.counts(),{'blocked':1})
    def test_retry_backlog_preserves_production_source_and_counts(self):
        p=self.jpeg();self.q.scan(self.folder,0);self.q.scan(self.folder,0);self.q.set(self.q.due()['id'],state='retry',error='network_unavailable')
        self.q.close();self.q=agent.Queue(self.root/'state');s=self.q.snapshot();self.assertEqual(s['production']['queued'],1);self.assertEqual(s['production']['blocked'],0);self.assertTrue(p.exists())
    def test_console_export_is_read_only_during_active_transfer(self):
        import importlib.util
        try:
            import tkinter
        except ImportError:
            self.skipTest('Tk unavailable; console export test requires Python with Tk')
        sys.path.insert(0,str(CLIENT.parent))
        spec=importlib.util.spec_from_file_location('h2_console',CLIENT.with_name('lensdrop_console.py'))
        console=importlib.util.module_from_spec(spec);spec.loader.exec_module(console)
        self.jpeg();self.q.scan(self.folder,0);self.q.scan(self.folder,0);job=self.q.due()
        self.q.set(job['id'],state='uploading')
        report=console.read_diagnostics(self.root/'state')
        self.assertEqual(report['production']['queued'],1)
        self.assertEqual(self.q.db.execute('SELECT state FROM jobs WHERE id=?',(job['id'],)).fetchone()[0],'uploading')

    def test_legacy_queue_migration_is_additive(self):
        p=self.jpeg();self.q.scan(self.folder,0);self.q.scan(self.folder,0)
        self.q.db.executescript('ALTER TABLE jobs DROP COLUMN is_test; ALTER TABLE jobs DROP COLUMN last_progress; PRAGMA user_version=0;');self.q.close();self.q=agent.Queue(self.root/'state');self.assertEqual(self.q.snapshot()['production']['received'],1);self.assertTrue(p.exists())

CLOUD=os.environ.get('LENSDROP_CLOUD_DIR')
@unittest.skipUnless(CLOUD,'Set LENSDROP_CLOUD_DIR for the real cloud integration tests.')
class OperationsIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.folder=self.root/'camera';self.folder.mkdir();self.key='operations-client-private-admin-key-not-for-real-use'
        script="const a=require(process.argv[1]).createApp({dataDir:process.argv[2],adminKey:process.argv[3],freeReserve:0});a.server.listen(0,'127.0.0.1',()=>console.log(a.server.address().port));process.on('SIGTERM',()=>a.close().then(()=>process.exit(0)));"
        self.server=subprocess.Popen(['node','-e',script,str(Path(CLOUD)/'src/server.cjs'),str(self.root/'cloud'),self.key],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
        self.base='http://127.0.0.1:'+self.server.stdout.readline().strip();self.admin=agent.API(self.base,self.key,True)
        self.e=self.admin.call('/v1/events',{'name':'H2 integration','policy':'live'});self.d=self.admin.call('/v1/events/'+self.e['id']+'/devices',{'label':'Client A'});self.api=agent.API(self.base,self.d['token'],True)
    def tearDown(self):
        self.server.terminate();self.server.wait(timeout=10);self.server.stdout.close();self.tmp.cleanup()
    def photo(self,name,colour):
        p=self.folder/name
        subprocess.run(['node','-e',"require('sharp')({create:{width:120,height:80,channels:3,background:process.argv[2]}}).jpeg().toFile(process.argv[1])",str(p),colour],check=True)
        return p
    def run_agent(self,*extra):
        env={**os.environ,'LENSDROP_DEVICE_TOKEN':self.d['token']}
        return subprocess.run([sys.executable,str(CLIENT),'--server',self.base,'--allow-http-local','--folder',str(self.folder),'--state',str(self.root/'state'),'--min-free-mb','0',*map(str,extra)],env=env,text=True,capture_output=True,timeout=30)
    def test_cli_preflight_is_verified_but_never_public(self):
        p=self.photo('preflight.jpg','#345678');report=self.root/'diagnostics.json';r=self.run_agent('--preflight-photo',p,'--diagnostics',report)
        self.assertEqual(r.returncode,0,r.stderr+r.stdout);self.assertIn('isolated-test-never-public',r.stdout);self.assertEqual(len(self.api.call('/v1/g/'+self.e['slug'])['photos']),0)
        self.assertEqual(json.loads(report.read_text())['preflight']['uploaded'],1);self.assertTrue(p.exists())
    def test_drain_processes_only_existing_queue_and_cloud_reconciles(self):
        first=self.photo('first.jpg','#112233');q=agent.Queue(self.root/'state');q.scan(self.folder,0);q.scan(self.folder,0);q.close();second=self.photo('new-not-observed.jpg','#887755')
        report=self.root/'wrapup.json';r=self.run_agent('--drain','--reconcile',report)
        self.assertEqual(r.returncode,0,r.stderr+r.stdout);data=json.loads(report.read_text());self.assertEqual(data['production']['received'],1);self.assertEqual(data['cloud']['verified_photos'],1);self.assertTrue(data['cloud']['reconciled']);self.assertTrue(first.exists());self.assertTrue(second.exists());self.assertEqual(len(self.api.call('/v1/g/'+self.e['slug'])['photos']),1)
    def test_other_event_cannot_reuse_queue_and_does_not_replace_cached_identity(self):
        p=self.photo('first.jpg','#123456');r=self.run_agent('--preflight-photo',p);self.assertEqual(r.returncode,0)
        before=sqlite3.connect(self.root/'state/queue.sqlite').execute("SELECT value FROM meta WHERE key='identity'").fetchone()[0]
        other=self.admin.call('/v1/events',{'name':'Other','policy':'review'});self.d=self.admin.call('/v1/events/'+other['id']+'/devices',{'label':'Other phone'})
        r=self.run_agent('--drain');self.assertNotEqual(r.returncode,0)
        after=sqlite3.connect(self.root/'state/queue.sqlite').execute("SELECT value FROM meta WHERE key='identity'").fetchone()[0];self.assertEqual(before,after)

if __name__=='__main__':unittest.main(verbosity=2)
