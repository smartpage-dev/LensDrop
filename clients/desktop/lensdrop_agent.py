#!/usr/bin/env python3
"""LensDrop durable watch-folder reference agent. Python 3.10+, standard library only.
This desktop reference tests the phone upload protocol; it is not an Android app.
"""
from __future__ import annotations
import argparse
import hashlib
import ipaddress
import json
import os
import random
import shutil
import signal
import sqlite3
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any

CHUNK = 1024 * 1024
MAX_FILE = 64 * CHUNK
MAX_SPOOL_BYTES = 256 * CHUNK
TERMINAL_HTTP = {400, 403, 404, 413, 415, 422}

class TransferError(Exception):
    def __init__(self, code: str, retryable: bool = True, retry_after: float = 0):
        super().__init__(code)
        self.code, self.retryable, self.retry_after = code, retryable, retry_after

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args: Any, **kwargs: Any) -> None:
        return None

class API:
    def __init__(self, base: str, token: str, allow_local_http: bool = False):
        parsed = urllib.parse.urlparse(base)
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
            raise ValueError('Use an HTTPS origin without credentials, query or path.')
        local = parsed.hostname in ('localhost', '127.0.0.1', '::1')
        if parsed.scheme != 'https' and not (allow_local_http and parsed.scheme == 'http' and local):
            raise ValueError('HTTPS is required. Only loopback HTTP is allowed for explicit local tests.')
        self.base, self.token = base.rstrip('/'), token
        self.opener = urllib.request.build_opener(NoRedirect)

    def call(self, path: str, body: dict[str, Any] | bytes | None = None, sha: str | None = None) -> dict[str, Any]:
        headers = {'Authorization': 'Bearer ' + self.token}
        data = None
        if isinstance(body, bytes):
            data = body
            headers.update({'Content-Type': 'application/octet-stream', 'X-Chunk-SHA256': sha or ''})
        elif body is not None:
            data = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method='GET' if body is None else 'POST')
        try:
            with self.opener.open(req, timeout=45) as res:
                raw = res.read(65537)
                if len(raw) > 65536:
                    raise TransferError('server_response_too_large', False)
                return json.loads(raw)
        except urllib.error.HTTPError as e:
            try:
                code = json.loads(e.read(65536)).get('error', 'http_' + str(e.code))
            except (ValueError, TypeError):
                code = 'http_' + str(e.code)
            try:
                retry_after = float(e.headers.get('Retry-After', 0))
            except ValueError:
                from email.utils import parsedate_to_datetime
                try:
                    retry_after = max(0, parsedate_to_datetime(e.headers['Retry-After']).timestamp() - time.time())
                except (ValueError, TypeError, KeyError):
                    retry_after = 0
            retryable = e.code in {408, 409, 425, 429, 500, 502, 503, 504, 507}
            if code in {'client_id_reused_for_different_content', 'chunk_conflict', 'test_production_content_conflict', 'test_photo_cannot_publish'}:
                retryable = False
            raise TransferError(code, retryable, retry_after) from e
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
            raise TransferError('network_unavailable') from e
        except (ValueError, TypeError) as e:
            raise TransferError('invalid_server_response') from e

class Queue:
    def __init__(self, root: Path):
        root.mkdir(parents=True, exist_ok=True)
        self.root = root
        self.spool = root / 'spool'
        self.spool.mkdir(exist_ok=True)
        self.db = sqlite3.connect(root / 'queue.sqlite')
        self.db.row_factory = sqlite3.Row
        self.db.executescript('''PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;
          CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT NOT NULL);
          CREATE TABLE IF NOT EXISTS observed(path TEXT PRIMARY KEY,size INTEGER,mtime INTEGER,stable_since REAL);
          CREATE TABLE IF NOT EXISTS jobs(
            id TEXT PRIMARY KEY, source TEXT NOT NULL, name TEXT NOT NULL,
            size INTEGER NOT NULL, mtime INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'queued',
            spool TEXT, sha256 TEXT, upload_id TEXT, offset INTEGER NOT NULL DEFAULT 0,
            attempts INTEGER NOT NULL DEFAULT 0, next_try REAL NOT NULL DEFAULT 0,
            error TEXT, receipt TEXT, created REAL NOT NULL,
            UNIQUE(source,size,mtime));
          CREATE INDEX IF NOT EXISTS pending ON jobs(state,next_try,created);
        ''')
        columns = {r['name'] for r in self.db.execute('PRAGMA table_info(jobs)')}
        if 'is_test' not in columns:
            self.db.execute('ALTER TABLE jobs ADD COLUMN is_test INTEGER NOT NULL DEFAULT 0')
        if 'last_progress' not in columns:
            self.db.execute('ALTER TABLE jobs ADD COLUMN last_progress REAL NOT NULL DEFAULT 0')
        self.db.execute('PRAGMA user_version=2')
        self.db.execute("UPDATE jobs SET state='retry',next_try=0 WHERE state='uploading'")
        self.db.commit()
        for row in self.db.execute("SELECT id,spool FROM jobs WHERE state='uploaded' AND spool IS NOT NULL").fetchall():
            candidate=Path(row['spool'])
            if candidate.resolve().parent == self.spool.resolve():
                try:
                    candidate.unlink(missing_ok=True)
                    self.db.execute('UPDATE jobs SET spool=NULL WHERE id=?',(row['id'],))
                except OSError:
                    pass
        self.db.commit()

    def bind_scope(self, scope: str) -> None:
        old = self.db.execute("SELECT value FROM meta WHERE key='scope'").fetchone()
        if old and old['value'] != scope:
            raise ValueError('This queue belongs to another event/device. Use a separate --state directory; do not reuse its pending queue.')
        self.db.execute("INSERT OR IGNORE INTO meta VALUES('scope',?)", (scope,))
        self.db.commit()

    def set(self, jid: str, **fields: Any) -> None:
        permitted = {'state','spool','sha256','upload_id','offset','attempts','next_try','error','receipt','last_progress'}
        if not fields or not fields.keys() <= permitted:
            raise ValueError('Invalid queue update')
        self.db.execute('UPDATE jobs SET ' + ','.join(f'{k}=?' for k in fields) + ' WHERE id=?', [*fields.values(), jid])
        self.db.commit()

    def scan(self, folder: Path, settle: float = 2.0) -> int:
        added, now = 0, time.time()
        # Metadata-only traversal; no image buffers or thumbnail grid are constructed.
        for base, dirs, files in os.walk(folder, followlinks=False):
            dirs[:] = [d for d in dirs if not d.startswith('.') and not (Path(base)/d).is_symlink()]
            for name in files:
                if name.startswith(('.', '~')) or Path(name).suffix.lower() not in {'.jpg', '.jpeg'}:
                    continue
                p = Path(base)/name
                if p.is_symlink():
                    continue
                try:
                    s = p.stat()
                    if not 0 < s.st_size <= MAX_FILE:
                        continue
                    old = self.db.execute('SELECT * FROM observed WHERE path=?', (str(p),)).fetchone()
                    if not old or old['size'] != s.st_size or old['mtime'] != s.st_mtime_ns:
                        self.db.execute('INSERT OR REPLACE INTO observed VALUES(?,?,?,?)', (str(p),s.st_size,s.st_mtime_ns,now))
                        continue
                    if now - old['stable_since'] < settle:
                        continue
                    known = self.db.execute('SELECT 1 FROM jobs WHERE source=? AND size=? AND mtime=?', (str(p),s.st_size,s.st_mtime_ns)).fetchone()
                    if known:
                        self.db.execute("UPDATE jobs SET state='queued',next_try=0,error=NULL WHERE source=? AND size=? AND mtime=? AND error='source_missing'", (str(p),s.st_size,s.st_mtime_ns))
                        continue
                    # A cheap completion guard, not a substitute for the server JPEG decoder.
                    with p.open('rb') as source:
                        if source.read(2) != b'\xff\xd8':
                            continue
                        source.seek(-2, 2)
                        if source.read(2) != b'\xff\xd9':
                            continue
                    cur = self.db.execute('INSERT OR IGNORE INTO jobs(id,source,name,size,mtime,created) VALUES(?,?,?,?,?,?)',
                        (str(uuid.uuid4()),str(p),name,s.st_size,s.st_mtime_ns,now))
                    added += cur.rowcount
                    self.db.execute("UPDATE jobs SET state='queued',next_try=0,error=NULL WHERE source=? AND size=? AND mtime=? AND error='source_missing'", (str(p),s.st_size,s.st_mtime_ns))
                except (FileNotFoundError, PermissionError, OSError):
                    continue
        self.db.commit()
        return added

    def due(self) -> sqlite3.Row | None:
        return self.db.execute("SELECT * FROM jobs WHERE state IN ('queued','retry','uploading') AND next_try<=? ORDER BY (spool IS NULL),next_try,created,id LIMIT 1", (time.time(),)).fetchone()

    def counts(self) -> dict[str, int]:
        return {r['state']:r['n'] for r in self.db.execute('SELECT state,count(*) n FROM jobs GROUP BY state')}

    def bind_source(self, folder: Path) -> None:
        source = str(folder.resolve())
        old = self.db.execute("SELECT value FROM meta WHERE key='source_folder'").fetchone()
        if old and old['value'] != source:
            raise ValueError('This queue is bound to a different source folder. Use a separate event folder and state directory; do not repoint an existing queue.')
        self.db.execute("INSERT OR IGNORE INTO meta VALUES('source_folder',?)",(source,))
        self.db.commit()

    def add_preflight(self, source: Path) -> str:
        p = source.resolve()
        info = p.stat()
        if p.suffix.lower() not in {'.jpg','.jpeg'} or not 4 <= info.st_size <= MAX_FILE:
            raise ValueError('Preflight requires a complete JPEG up to 64 MiB.')
        with p.open('rb') as f:
            if f.read(2) != b'\xff\xd8':
                raise ValueError('JPEG not ready.')
            f.seek(-2,2)
            if f.read(2) != b'\xff\xd9':
                raise ValueError('JPEG not ready.')
        old = self.db.execute('SELECT id,is_test FROM jobs WHERE source=? AND size=? AND mtime=?',(str(p),info.st_size,info.st_mtime_ns)).fetchone()
        if old:
            if not old['is_test']:
                raise ValueError('This photograph already belongs to the production queue. Take a separate test photo before starting the watcher.')
            return old['id']
        jid = str(uuid.uuid4())
        self.db.execute('INSERT INTO jobs(id,source,name,size,mtime,created,is_test) VALUES(?,?,?,?,?,?,1)',(jid,str(p),p.name,info.st_size,info.st_mtime_ns,time.time()))
        self.db.commit()
        return jid

    def snapshot(self) -> dict[str, Any]:
        groups = {}
        for flag, label in [(0,'production'),(1,'preflight')]:
            rows = self.db.execute('SELECT state,count(*) n FROM jobs WHERE is_test=? GROUP BY state',(flag,)).fetchall()
            counts = {r['state']:r['n'] for r in rows}
            pending = sum(v for k,v in counts.items() if k not in {'uploaded','blocked'})
            extra = self.db.execute("SELECT coalesce(sum(CASE WHEN state<>'uploaded' THEN size-offset ELSE 0 END),0) remaining,coalesce(max(created),0) last_received,coalesce(max(last_progress),0) last_progress,coalesce(min(CASE WHEN state NOT IN ('uploaded','blocked') THEN created END),0) oldest FROM jobs WHERE is_test=?",(flag,)).fetchone()
            groups[label] = {'received':sum(counts.values()),'uploaded':counts.get('uploaded',0),'queued':pending,'blocked':counts.get('blocked',0),
                'queue_bytes':extra['remaining'],'last_received':int(extra['last_received']*1000),'last_progress':int(extra['last_progress']*1000),
                'attention_no_progress':pending>0 and time.time()-max(extra['last_progress'],extra['oldest'])>120,'states':counts}
        return {'schema':'lensdrop-local-snapshot-v1','version':'0.2.0-alpha.3-hostinger.2','generated_at':int(time.time()*1000),
            'scope':'files-observed-by-this-client-only','camera_card_complete':False,'source_originals_may_be_deleted':False,**groups}

    def diagnostics(self) -> dict[str, Any]:
        report = self.snapshot()
        safe_codes = {'source_missing','source_changed','network_unavailable','io_or_protocol_error','device_token_invalid_or_revoked','device_revoked',
          'phone_or_agent_storage_low','cloud_storage_low','event_quota_reached','staging_limit_reached','server_identity_mismatch','receipt_verification_failed',
          'invalid_or_unsupported_jpeg','file_checksum_mismatch','test_production_content_conflict','preflight_requires_h2_server','paused_by_user'}
        errors = {}
        for row in self.db.execute('SELECT error,count(*) n FROM jobs WHERE error IS NOT NULL GROUP BY error'):
            key = row['error'] if row['error'] in safe_codes else 'other_error'
            errors[key] = errors.get(key,0)+row['n']
        report['error_counts'] = errors
        report['omitted'] = ['tokens','server URLs','event names','filenames','source paths','photographs','raw server errors']
        return report

    def close(self) -> None:
        self.db.close()

class Agent:
    def __init__(self, queue: Queue, api: API, min_free_bytes: int = 512*CHUNK):
        self.q, self.api, self.min_free_bytes = queue, api, min_free_bytes
        self.stop = False
        cached=queue.db.execute("SELECT value FROM meta WHERE key='cooldown'").fetchone()
        self.cooldown_until=float(cached['value']) if cached else 0.0

    def stage(self, j: sqlite3.Row) -> tuple[Path,str]:
        if j['spool'] and j['sha256']:
            p = Path(j['spool'])
            if p.is_file() and p.stat().st_size == j['size']:
                return p,j['sha256']
        source = Path(j['source'])
        try:
            before = source.stat()
        except FileNotFoundError as e:
            raise TransferError('source_missing',False) from e
        if before.st_size != j['size'] or before.st_mtime_ns != j['mtime']:
            raise TransferError('source_changed',False)
        target = self.q.spool/(j['id']+'.jpg')
        temp = target.with_suffix('.part')
        temp.unlink(missing_ok=True)  # Restart after a crash during staging.
        target.unlink(missing_ok=True)  # No committed usable snapshot; source verified above.
        staged_bytes=sum(p.stat().st_size for p in self.q.spool.iterdir() if p.is_file() and p != target)
        if staged_bytes + j['size'] > MAX_SPOOL_BYTES:
            raise TransferError('staging_limit_reached',True,60)
        if shutil.disk_usage(self.q.root).free < self.min_free_bytes + j['size']:
            raise TransferError('phone_or_agent_storage_low',True,60)
        digest = hashlib.sha256()
        count = 0
        try:
            with source.open('rb') as src,temp.open('wb') as out:
                while block := src.read(64*1024):
                    if self.stop:
                        raise TransferError('paused_by_user')
                    count += len(block)
                    if count > j['size']:
                        raise TransferError('source_changed',False)
                    digest.update(block)
                    out.write(block)
                out.flush()
                os.fsync(out.fileno())
            after = source.stat()
            if count != j['size'] or (before.st_size,before.st_mtime_ns)!=(after.st_size,after.st_mtime_ns):
                raise TransferError('source_changed',False)
            os.replace(temp,target)
            # fsync the directory on POSIX before committing the staged filename.
            if os.name != 'nt':
                fd=os.open(self.q.spool,os.O_RDONLY)
                try:
                    os.fsync(fd)
                finally:
                    os.close(fd)
            sha=digest.hexdigest()
            self.q.set(j['id'],spool=str(target),sha256=sha)
            return target,sha
        finally:
            temp.unlink(missing_ok=True)

    def one(self, j: sqlite3.Row) -> bool:
        try:
            if j['is_test'] and not j['upload_id']:
                identity = self.api.call('/v1/device')
                if not identity.get('capabilities',{}).get('isolated_preflight'):
                    raise TransferError('preflight_requires_h2_server',False)
            p,sha=self.stage(j)
            self.q.set(j['id'],state='uploading',error=None)
            if j['upload_id']:
                remote=self.api.call('/v1/uploads/'+j['upload_id'])
            else:
                remote=self.api.call('/v1/uploads',{'client_id':j['id'],'filename':j['name'],'size':j['size'],'sha256':sha,'is_test':bool(j['is_test'])})
            if j['is_test'] and not remote.get('is_test'):
                raise TransferError('preflight_requires_h2_server',False)
            uid=remote['upload_id']
            self.q.set(j['id'],upload_id=uid,offset=remote['offset'])
            if remote['size']!=j['size'] or remote['sha256']!=sha:
                raise TransferError('server_identity_mismatch',False)
            with p.open('rb') as file:
                while remote['state']!='complete' and remote['offset']<j['size']:
                    if self.stop:
                        raise TransferError('paused_by_user')
                    offset=remote['offset']
                    if not isinstance(offset,int) or offset<0 or offset%CHUNK:
                        raise TransferError('invalid_server_offset',False)
                    file.seek(offset)
                    block=file.read(min(CHUNK,j['size']-offset))
                    if not block:
                        raise TransferError('staged_file_truncated',False)
                    remote=self.api.call(f'/v1/uploads/{uid}/chunks?offset={offset}',block,hashlib.sha256(block).hexdigest())
                    if remote['offset']<=offset:
                        raise TransferError('server_made_no_progress')
                    self.q.set(j['id'],offset=remote['offset'],last_progress=time.time())
            if remote['state']!='complete':
                remote=self.api.call(f'/v1/uploads/{uid}/complete',{})
            receipt=remote.get('receipt')
            if not receipt or receipt['sha256']!=sha or receipt['bytes']!=j['size'] or not receipt.get('photo_id'):
                raise TransferError('receipt_verification_failed',False)
            self.q.set(j['id'],state='uploaded',offset=j['size'],error=None,receipt=json.dumps(receipt),last_progress=time.time())
            # Only the app-owned staged copy is removed. The watched original is never deleted.
            p.unlink(missing_ok=True)
            self.q.set(j['id'],spool=None)
            return True
        except TransferError as e:
            attempts=j['attempts']+1
            delay=max(e.retry_after,random.uniform(1,min(60,2**min(attempts,6))))
            self.q.set(j['id'],state='retry' if e.retryable else 'blocked',attempts=attempts,next_try=time.time()+delay,error=e.code)
            if e.retryable:
                self.cooldown_until=time.time()+min(60,max(2,delay))
                self.q.db.execute("INSERT OR REPLACE INTO meta VALUES('cooldown',?)",(str(self.cooldown_until),))
                self.q.db.commit()
            if e.code in {'device_token_invalid_or_revoked','device_revoked'}:
                self.stop=True
        except (OSError,KeyError,ValueError,TypeError):
            self.q.set(j['id'],state='retry',attempts=j['attempts']+1,next_try=time.time()+30,error='io_or_protocol_error')
            self.cooldown_until=time.time()+30
        return False

class ProcessLock:
    def __init__(self, root: Path):
        root.mkdir(parents=True,exist_ok=True)
        self.file=(root/'agent.lock').open('a+b')
        self.file.seek(0);self.file.write(b'0');self.file.flush();self.file.seek(0)
        try:
            if os.name=='nt':
                import msvcrt
                msvcrt.locking(self.file.fileno(),msvcrt.LK_NBLCK,1)
            else:
                import fcntl
                fcntl.flock(self.file,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except OSError as e:
            self.file.close()
            raise RuntimeError('Another agent owns this queue.') from e
    def close(self) -> None:
        self.file.close()

def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_name(path.name+'.tmp')
    with temp.open('w',encoding='utf-8') as out:
        json.dump(report,out,ensure_ascii=False,indent=2)
        out.flush();os.fsync(out.fileno())
    os.replace(temp,path)

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server',required=True)
    parser.add_argument('--folder',type=Path,required=True)
    parser.add_argument('--state',type=Path,default=Path.home()/'.lensdrop-agent')
    parser.add_argument('--allow-http-local',action='store_true')
    parser.add_argument('--retry-blocked',action='store_true')
    parser.add_argument('--scan-seconds',type=float,default=5)
    parser.add_argument('--min-free-mb',type=int,default=512)
    parser.add_argument('--preflight-photo',type=Path,help='Upload only this separate test JPEG, never public. Do this before starting folder watch.')
    parser.add_argument('--drain',action='store_true',help='Stop discovering new files; finish the already observed queue only.')
    parser.add_argument('--diagnostics',type=Path,help='Write a redacted snapshot on exit.')
    parser.add_argument('--reconcile',type=Path,help='Write local and available cloud counts on exit; never certifies camera-card completeness.')
    parser.add_argument('--status-file',type=Path,help='Atomic, redacted status file for the desktop console.')
    args=parser.parse_args()
    token=os.environ.get('LENSDROP_DEVICE_TOKEN','')
    if not token:
        import getpass
        token=getpass.getpass('Device token (not the administrator key): ')
    if args.preflight_photo and args.drain:
        parser.error('Use preflight or drain, not both.')
    folder,state=args.folder.resolve(),args.state.resolve()
    if not folder.is_dir() or state==folder or folder in state.parents:
        parser.error('Watch folder must exist; queue state must be outside it.')
    lock=ProcessLock(state)
    q=Queue(state)
    try:
        api=API(args.server,token,args.allow_http_local)
        try:
            identity=api.call('/v1/device')

        except TransferError as error:
            cached=q.db.execute("SELECT value FROM meta WHERE key='identity'").fetchone()
            if not error.retryable or not cached:
                raise
            identity=json.loads(cached['value'])
        q.bind_scope(args.server.rstrip('/')+'|'+identity['event']['id']+'|'+identity['device_id'])
        q.db.execute("INSERT OR REPLACE INTO meta VALUES('identity',?)",(json.dumps(identity),))
        q.db.commit()
        q.bind_source(folder)
        test_id = None
        if args.preflight_photo:
            source = args.preflight_photo.resolve()
            if folder not in source.parents:
                parser.error('Test photo must be inside this event source folder.')
            test_id = q.add_preflight(source)
        if args.retry_blocked:
            q.db.execute("UPDATE jobs SET state='retry',next_try=0 WHERE state='blocked'");q.db.commit()
        agent=Agent(q,api,args.min_free_mb*CHUNK)
        for sig in (signal.SIGINT,signal.SIGTERM,*([signal.SIGBREAK] if hasattr(signal,'SIGBREAK') else [])):
            signal.signal(sig,lambda *_:setattr(agent,'stop',True))
        print('LensDrop reference agent: JPEG only; all existing files in selected folder are eligible.')
        print('Stop with Ctrl+C. Pending work is retained; source files are never deleted.')
        next_scan,next_status=0.0,0.0
        cloud_snapshot=None
        while not agent.stop:
            t=time.monotonic()
            if t>=next_scan and not args.drain and not test_id:
                q.scan(folder);next_scan=t+max(2,args.scan_seconds)
            job = q.db.execute("SELECT * FROM jobs WHERE id=? AND state IN ('queued','retry','uploading') AND next_try<=?",(test_id,time.time())).fetchone() if test_id else q.due()
            if test_id:
                row = q.db.execute('SELECT state FROM jobs WHERE id=?',(test_id,)).fetchone()
                if row['state'] in {'uploaded','blocked'}:
                    if row['state']=='uploaded':
                        print(json.dumps(api.call('/v1/device/preflight?client_id='+test_id)),flush=True)
                    break
            if args.drain and not q.db.execute("SELECT 1 FROM jobs WHERE state IN ('queued','retry','uploading') LIMIT 1").fetchone():
                break
            if job and time.time()>=agent.cooldown_until:
                agent.one(job)
            else:
                time.sleep(.5)
            if t>=next_status:
                snapshot=q.snapshot()
                print(json.dumps(snapshot),flush=True)
                try:
                    heartbeat={**snapshot['production'],'last_scan':int(time.time()*1000),'scan_paused':int(args.drain or bool(test_id)),
                        'test_received':snapshot['preflight']['received'],'test_uploaded':snapshot['preflight']['uploaded'],'test_pending':snapshot['preflight']['queued']+snapshot['preflight']['blocked']}
                    api.call('/v1/device/heartbeat',heartbeat)
                    cloud_snapshot={'fetched_at':int(time.time()*1000),'counts':api.call('/v1/device/summary')}
                except TransferError:
                    pass
                if args.status_file:
                    safe_cloud = None if cloud_snapshot is None else {'fetched_at':cloud_snapshot['fetched_at'],**{k:cloud_snapshot['counts'].get(k) for k in ('verified_photos','verified_uploads','live','review','hidden','incomplete')}}
                    write_report(args.status_file,{**snapshot,'cloud':safe_cloud})
                next_status=t+10
        snap=q.snapshot()
        try:
            api.call('/v1/device/heartbeat',{**snap['production'],'scan_paused':1,'test_received':snap['preflight']['received'],'test_uploaded':snap['preflight']['uploaded'],'test_pending':snap['preflight']['queued']+snap['preflight']['blocked']})
        except TransferError:
            pass
        if args.diagnostics:
            write_report(args.diagnostics,q.diagnostics())
        if args.reconcile:
            report=snap
            try:
                c=api.call('/v1/device/summary')
                report['cloud']={k:c.get(k) for k in ('verified_photos','verified_uploads','live','review','hidden','incomplete','reconciled','heartbeat_fresh')}
            except TransferError:
                report['cloud']=None
                report['cloud_unavailable']=True
            write_report(args.reconcile,report)
        if args.status_file:
            write_report(args.status_file,{**snap,'stopped':True})
        return 2 if snap['production']['blocked'] or snap['preflight']['blocked'] else 0
    finally:
        q.close();lock.close()

if __name__=='__main__':
    try:
        raise SystemExit(main())
    except (ValueError,RuntimeError,TransferError) as error:
        print('LensDrop stopped: '+str(error),file=sys.stderr)
        raise SystemExit(1)
