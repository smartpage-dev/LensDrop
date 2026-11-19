#!/usr/bin/env python3
"""H5.1 targeted source/API contract regression tests (not an Android SDK build).

The real DirectorySync.java is compiled with small, fail-injectable public API
stubs. The StaffSetupActivity method declaration and callback name are projected
into a minimal Activity contract, not a reimplementation of enrollment logic.
Requires JDK17+ and Python3; no downloads, secrets, network or Android device.
"""
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ANDROID = Path(__file__).resolve().parents[1]
SRC = ANDROID / 'app/src/main/java/app/lensdrop/transfer'

STUBS = {
'android/system/ErrnoException.java': '''package android.system;
public class ErrnoException extends Exception {public ErrnoException(String s){super(s);}}''',
'android/system/StructStat.java': '''package android.system;
public class StructStat {public int st_mode;public StructStat(int value){st_mode=value;}}''',
'android/system/OsConstants.java': '''package android.system;
public final class OsConstants {
 public static final int O_RDONLY=0;public static final int S_IFDIR=0040000;
 public static boolean S_ISDIR(int mode){return (mode&0170000)==S_IFDIR;}
}''',
'android/system/Os.java': '''package android.system;
import java.io.FileDescriptor;
public final class Os {
 public static String scenario="ok",events="";public static int opens,stats,syncs,closes;
 public static FileDescriptor open(String p,int flags,int mode)throws ErrnoException {
  opens++;events+="open ";if(flags!=OsConstants.O_RDONLY||mode!=0)throw new AssertionError("wrong flags");
  if(scenario.equals("openFailure"))throw new ErrnoException("open");return new FileDescriptor();
 }
 public static StructStat fstat(FileDescriptor fd)throws ErrnoException {
  stats++;events+="stat ";if(scenario.equals("statFailure"))throw new ErrnoException("stat");
  return new StructStat(scenario.equals("nonDirectory")?0100000:OsConstants.S_IFDIR);
 }
 public static void fsync(FileDescriptor fd)throws ErrnoException {syncs++;events+="sync ";if(scenario.equals("syncFailure"))throw new ErrnoException("sync");}
 public static void close(FileDescriptor fd)throws ErrnoException {closes++;events+="close ";if(scenario.equals("closeFailure"))throw new ErrnoException("close");}
}''',
'app/lensdrop/transfer/DirectorySyncScenario.java': '''package app.lensdrop.transfer;
import java.io.File;import java.io.IOException;import android.system.*;
public class DirectorySyncScenario {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Os.scenario=args[0];Exception failure=null;try{DirectorySync.sync(new File("private-spool"));}catch(Exception e){failure=e;}
  check(Os.opens==1,"exactly one open");
  if(Os.scenario.equals("ok")){check(failure==null,"success");check(Os.events.equals("open stat sync close "),"durability ordering");}
  else{check(failure!=null,"failure must propagate");}
  if(Os.scenario.equals("openFailure")){check(Os.stats==0&&Os.syncs==0&&Os.closes==0,"unopened descriptor not used");}
  else{check(Os.stats==1&&Os.closes==1,"opened descriptor always closed once");}
  if(Os.scenario.equals("nonDirectory")){check(failure instanceof IOException,"reject nondirectory");check(Os.syncs==0,"do not sync wrong object");}
  if(Os.scenario.equals("statFailure")){check(Os.syncs==0,"do not skip stat failure");}
  if(Os.scenario.equals("syncFailure")){check(failure instanceof ErrnoException&&failure.getMessage().equals("sync"),"sync failure retained");}
  if(Os.scenario.equals("closeFailure")){check(failure instanceof ErrnoException&&failure.getMessage().equals("close"),"close failure propagated");}
  System.out.println("PASS DirectorySync "+Os.scenario+"; "+Os.events);
 }
}'''
}

def compile_files(target, files):
    return subprocess.run(['javac','--release','17','-d',str(target),*map(str,files)],capture_output=True,text=True,timeout=45)

class BuildFixTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory(prefix='lensdrop-h51-contract-')
        cls.base=Path(cls.tmp.name);cls.tree=cls.base/'os';cls.classes=cls.base/'classes'
        for rel,content in STUBS.items():
            p=cls.tree/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8')
        cls.directory=SRC/'DirectorySync.java';cls.staff=(SRC/'StaffSetupActivity.java').read_text()
        cls.upload=(SRC/'UploadEngine.java').read_text()
        result=compile_files(cls.classes,[*cls.tree.rglob('*.java'),cls.directory])
        if result.returncode:raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):cls.tmp.cleanup()

    def scenario(self,mode):
        r=subprocess.run(['java','-cp',str(self.classes),'app.lensdrop.transfer.DirectorySyncScenario',mode],capture_output=True,text=True,timeout=15)
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    def test_01_directory_success_keeps_sync_order(self):self.scenario('ok')
    def test_02_open_failure_propagates_without_close(self):self.scenario('openFailure')
    def test_03_stat_failure_closes_descriptor(self):self.scenario('statFailure')
    def test_04_regular_file_is_rejected(self):self.scenario('nonDirectory')
    def test_05_fsync_failure_is_not_ignored(self):self.scenario('syncFailure')
    def test_06_close_failure_is_not_ignored(self):self.scenario('closeFailure')
    def test_07_hidden_constant_negative_control(self):
        bad=self.base/'bad-os/DirectorySync.java';bad.parent.mkdir();bad.write_text(self.directory.read_text().replace('OsConstants.O_RDONLY,','OsConstants.O_RDONLY | OsConstants.O_DIRECTORY,'))
        result=compile_files(self.base/'bad-classes',[*self.tree.rglob('*.java'),bad])
        self.assertNotEqual(result.returncode,0);self.assertIn('O_DIRECTORY',result.stderr)
    def activity_projection(self,old=False):
        declaration=re.search(r'private void (completePairing)\(\)throws Exception',self.staff)
        self.assertIsNotNone(declaration)
        name='finish' if old else declaration.group(1)
        folder=self.base/('bad-activity' if old else 'good-activity');folder.mkdir(exist_ok=True)
        p=folder/'PairingContract.java'
        p.write_text('class Activity {public void finish(){}}\npublic class PairingContract extends Activity {'
                    +'interface Task {void run() throws Exception;} void work(Task t){} '
                    +'void click(){work(this::'+name+');} private void '+name+'()throws Exception {}}')
        return compile_files(folder/'classes',[p])
    def test_08_new_pairing_callback_compiles_against_activity(self):
        r=self.activity_projection();self.assertEqual(r.returncode,0,r.stderr)
    def test_09_original_finish_collision_negative_control(self):
        r=self.activity_projection(True);self.assertNotEqual(r.returncode,0);self.assertIn('weaker access',r.stderr)
    def test_10_button_invokes_pairing_not_activity_finish(self):
        self.assertIn('work(this::completePairing)',self.staff);self.assertNotIn('this::finish',self.staff)
        self.assertNotRegex(self.staff,r'\bvoid\s+finish\s*\(')
    def test_11_pairing_still_waits_for_admin_approval(self):
        self.assertIn('!"approved".equals(result.getString("state"))',self.staff)
        self.assertIn('q.hasOtherPending(scope)||prints.hasPending()',self.staff)
    def test_12_upload_records_only_after_directory_sync(self):
        move=self.upload.index('Files.move(temp.toPath()')
        sync=self.upload.index('DirectorySync.sync(dir);',move)
        save=self.upload.index('queue.update(j.id,v)',sync)
        self.assertLess(move,sync);self.assertLess(sync,save)
        self.assertIn('out.getFD().sync()',self.upload[:move])
    def test_13_no_hidden_android_flags_in_production(self):
        for p in SRC.glob('*.java'):self.assertNotIn('OsConstants.O_DIRECTORY',p.read_text(),str(p))
    def test_14_version_and_app_identity(self):
        t=(ANDROID/'app/build.gradle').read_text()
        for expected in ["applicationId 'app.lensdrop.transfer'","versionName '0.2.0-alpha.6-h5.1'",'versionCode 6','minSdk 29','compileSdk 35','targetSdk 35']:
            self.assertIn(expected,t)
    def test_15_sync_errors_reach_existing_upload_retry(self):
        self.assertIn('catch(Exception e)',self.upload)
        self.assertIn('"network_or_io_error"',self.upload)
        self.assertNotIn('catch',self.directory.read_text())

if __name__=='__main__':unittest.main(verbosity=2)
