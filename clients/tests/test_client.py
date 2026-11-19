import hashlib
import importlib.util
import json
import multiprocessing
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import time
import unittest
import uuid

CLIENT=Path(__file__).resolve().parents[1]/'desktop'/'lensdrop_agent.py'
spec=importlib.util.spec_from_file_location('agent',CLIENT)
agent=importlib.util.module_from_spec(spec)
sys.modules['agent']=agent
spec.loader.exec_module(agent)

class QueueTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.q=agent.Queue(self.root/'state')
    def tearDown(self):
        self.q.close();self.tmp.cleanup()
    def test_10000_pending_jobs_survive_reopen(self):
        rows=[(str(uuid.uuid4()),f'/source/IMG_{n}.JPG',f'IMG_{n}.JPG',100,n,time.time()) for n in range(10000)]
        self.q.db.executemany('INSERT INTO jobs(id,source,name,size,mtime,created) VALUES(?,?,?,?,?,?)',rows);self.q.db.commit()
        self.q.close();self.q=agent.Queue(self.root/'state');self.assertEqual(self.q.counts()['queued'],10000)
    def test_uploading_state_recovers_without_deletion(self):
        self.q.db.execute("INSERT INTO jobs(id,source,name,size,mtime,state,created) VALUES('one','/x','x.jpg',10,10,'uploading',0)");self.q.db.commit()
        self.q.close();self.q=agent.Queue(self.root/'state');self.assertEqual(self.q.due()['state'],'retry')
    def test_partial_file_not_queued_and_source_never_removed(self):
        folder=self.root/'camera';folder.mkdir();p=folder/'one.JPG';p.write_bytes(b'\xff\xd8partial')
        self.q.scan(folder,0);self.q.scan(folder,0);self.assertEqual(self.q.counts(),{})
        p.write_bytes(b'\xff\xd8complete\xff\xd9');self.q.scan(folder,0);self.q.scan(folder,0)
        self.assertEqual(self.q.counts()['queued'],1);self.assertTrue(p.exists())
    def test_scope_cannot_be_rebound(self):
        self.q.bind_scope('server|event-A|phone-A')
        with self.assertRaises(ValueError):self.q.bind_scope('server|event-B|phone-A')
    def test_staging_hash_and_source_preservation(self):
        folder=self.root/'camera';folder.mkdir();p=folder/'one.JPG';data=b'\xff\xd8'+os.urandom(1000)+b'\xff\xd9';p.write_bytes(data)
        self.q.scan(folder,0);self.q.scan(folder,0);job=self.q.due();a=agent.Agent(self.q,None,0)
        (self.q.spool/(job['id']+'.part')).write_bytes(b'crash-leftover')
        (self.q.spool/(job['id']+'.jpg')).write_bytes(b'uncommitted-staging-copy')
        staged,sha=a.stage(job)
        self.assertFalse((self.q.spool/(job['id']+'.part')).exists())
        self.assertEqual(sha,hashlib.sha256(data).hexdigest());self.assertEqual(staged.read_bytes(),data);self.assertEqual(p.read_bytes(),data)
    def test_duplicate_observations_do_not_duplicate_queue(self):
        folder=self.root/'camera';folder.mkdir();(folder/'one.JPG').write_bytes(b'\xff\xd8photo\xff\xd9')
        for _ in range(10):self.q.scan(folder,0)
        self.assertEqual(self.q.counts()['queued'],1)
    def test_staging_limit_applies_backpressure_without_deleting_jobs(self):
        folder=self.root/'camera';folder.mkdir();(folder/'one.JPG').write_bytes(b'\xff\xd8photo\xff\xd9')
        self.q.scan(folder,0);self.q.scan(folder,0)
        original=agent.MAX_SPOOL_BYTES
        try:
            agent.MAX_SPOOL_BYTES=8
            with self.assertRaises(agent.TransferError) as caught:agent.Agent(self.q,None,0).stage(self.q.due())
            self.assertEqual(caught.exception.code,'staging_limit_reached');self.assertEqual(self.q.counts()['queued'],1)
        finally:agent.MAX_SPOOL_BYTES=original
    def test_remote_http_and_embedded_credentials_rejected(self):
        with self.assertRaises(ValueError):agent.API('http://example.com','token',True)
        with self.assertRaises(ValueError):agent.API('https://user:password@example.com','token')

CLOUD=os.environ.get('LENSDROP_CLOUD_DIR')
@unittest.skipUnless(CLOUD,'Set LENSDROP_CLOUD_DIR to run integration tests against the real cloud core.')
class IntegrationTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.q=agent.Queue(self.root/'state')
        self.key='integration-admin-key-not-for-production-1234'
        code="const {createApp}=require(process.argv[1]);const a=createApp({dataDir:process.argv[2],adminKey:process.env.TEST_KEY,freeReserve:0});a.server.listen(0,'127.0.0.1',()=>console.log(a.server.address().port));process.on('SIGTERM',()=>a.close().then(()=>process.exit(0)));"
        self.server=subprocess.Popen(['node','-e',code,str(Path(CLOUD)/'src'/'server.cjs'),str(self.root/'cloud')],env={**os.environ,'TEST_KEY':self.key},stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
        port=self.server.stdout.readline().strip();self.base='http://127.0.0.1:'+port
        admin=agent.API(self.base,self.key,True);self.event=admin.call('/v1/events',{'name':'Integration','policy':'live'})
        self.device=admin.call('/v1/events/'+self.event['id']+'/devices',{'label':'Reference agent'})
        self.api=agent.API(self.base,self.device['token'],True)
        self.folder=self.root/'camera';self.folder.mkdir()
    def tearDown(self):
        self.q.close();self.server.terminate();self.server.wait(timeout=15);self.server.stdout.close();self.tmp.cleanup()
    def make_photo(self,name='IMG_0001.JPG',large=False,colour='#a07888'):
        out=self.folder/name
        script="const sharp=require('sharp');const c=require('node:crypto');const i=process.argv[2]==='large'?sharp(c.randomBytes(1800*1400*3),{raw:{width:1800,height:1400,channels:3}}):sharp({create:{width:160,height:120,channels:3,background:process.argv[3]}});i.jpeg({quality:97}).toFile(process.argv[1]);"
        subprocess.run(['node','-e',script,str(out),'large' if large else 'small',colour],check=True)
        self.q.scan(self.folder,0);self.q.scan(self.folder,0);return out
    def test_ack_loss_then_reopen_recovers_without_duplicate(self):
        source=self.make_photo(large=True)
        real=self.api
        class LostAck:
            failed=False
            def call(inner,path,body=None,sha=None):
                result=real.call(path,body,sha)
                if '/chunks?' in path and not inner.failed:
                    inner.failed=True;raise agent.TransferError('simulated_lost_ack')
                return result
        a=agent.Agent(self.q,LostAck(),0);self.assertFalse(a.one(self.q.due()))
        self.q.close();self.q=agent.Queue(self.root/'state');self.q.db.execute('UPDATE jobs SET next_try=0');self.q.db.commit()
        a=agent.Agent(self.q,self.api,0);self.assertTrue(a.one(self.q.due()));self.assertEqual(self.q.counts()['uploaded'],1)
        self.assertTrue(source.exists());self.assertEqual(len(list(self.q.spool.glob('*.jpg'))),0)
        gallery=self.api.call('/v1/g/'+self.event['slug']);self.assertEqual(len(gallery['photos']),1)
    def test_revoked_token_blocks_not_infinite_retries(self):
        self.make_photo();admin=agent.API(self.base,self.key,True);admin.call('/v1/devices/'+self.device['device_id']+'/revoke',{})
        a=agent.Agent(self.q,self.api,0);self.assertFalse(a.one(self.q.due()));self.assertEqual(self.q.counts()['blocked'],1);self.assertTrue(a.stop)
    def test_three_distinct_photos_all_accounted_for(self):
        sources=[self.make_photo(f'IMG_{i}.JPG',colour=colour) for i,colour in enumerate(['#335577','#778899','#bb7799'])]
        a=agent.Agent(self.q,self.api,0)
        while self.q.due():self.assertTrue(a.one(self.q.due()))
        self.assertEqual(self.q.counts()['uploaded'],3);self.assertTrue(all(p.exists() for p in sources));self.assertEqual(len(self.api.call('/v1/g/'+self.event['slug'])['photos']),3)

if __name__=='__main__':unittest.main(verbosity=2)
