#!/usr/bin/env python3
"""Small native operator console for the reference uploader. Not a phone app or an installer."""
from __future__ import annotations
import json
import os
from pathlib import Path
import queue
import signal
import sqlite3
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import lensdrop_agent as agent


def read_diagnostics(state: Path) -> dict:
    """Read-only inspection: never instantiate Queue while a transfer process owns it."""
    path=(state/'queue.sqlite').resolve()
    db=sqlite3.connect(path.as_uri()+'?mode=ro',uri=True,timeout=2)
    db.row_factory=sqlite3.Row
    try:
        proxy=agent.Queue.__new__(agent.Queue)
        proxy.db=db
        return proxy.diagnostics()
    finally:
        db.close()


class Console:
    def __init__(self, root: tk.Tk):
        self.root=root;self.process=None;self.events=queue.Queue();self.closing=False
        root.title('LensDrop H2 | Transfer Operator');root.geometry('850x780');root.minsize(680,650)
        style=ttk.Style(root);style.theme_use('clam');style.configure('TLabel',font=('Arial',10));style.configure('TButton',padding=8);style.configure('Title.TLabel',font=('Arial',22,'bold'))
        frame=ttk.Frame(root,padding=22);frame.pack(fill='both',expand=True);frame.columnconfigure(1,weight=1)
        ttk.Label(frame,text='LensDrop / Operator H2',style='Title.TLabel').grid(row=0,column=0,columnspan=3,sticky='w')
        ttk.Label(frame,text='Engineering alpha. Queue and source originals are retained. No camera control.',wraplength=770).grid(row=1,column=0,columnspan=3,sticky='w',pady=(4,15))
        self.server=tk.StringVar();self.token=tk.StringVar();self.folder=tk.StringVar();self.state=tk.StringVar(value=str(Path.home()/'.lensdrop-event-01'));self.local=tk.BooleanVar()
        self.inputs=[]
        for row,label,var in [(2,'HTTPS server',self.server),(3,'Device token (not admin key)',self.token),(4,'Event photo folder',self.folder),(5,'Event queue directory',self.state)]:
            ttk.Label(frame,text=label).grid(row=row,column=0,sticky='w',pady=6)
            e=ttk.Entry(frame,textvariable=var,show='*' if row==3 else '');e.grid(row=row,column=1,sticky='ew',padx=10,pady=6);self.inputs.append(e)
            if row in (4,5):
                b=ttk.Button(frame,text='Browse',command=lambda v=var:self.pick_folder(v));b.grid(row=row,column=2);self.inputs.append(b)
        check=ttk.Checkbutton(frame,text='Allow loopback HTTP for a local computer test only',variable=self.local);check.grid(row=6,column=0,columnspan=3,sticky='w',pady=8);self.inputs.append(check)
        ttk.Label(frame,text='Use a separate source folder AND queue directory for every event. An existing queue cannot be reassigned. Tokens are not saved.',wraplength=770).grid(row=7,column=0,columnspan=3,sticky='w',pady=5)
        buttons=ttk.Frame(frame);buttons.grid(row=8,column=0,columnspan=3,sticky='ew',pady=12)
        self.start_buttons=[]
        for title,mode in [('1. Test one separate JPEG','preflight'),('2. Start / resume','watch'),('3. Finish observed queue','drain')]:
            b=ttk.Button(buttons,text=title,command=lambda m=mode:self.start(m));b.pack(side='left',padx=(0,8));self.start_buttons.append(b)
        self.pause=ttk.Button(buttons,text='Pause',command=self.stop,state='disabled');self.pause.pack(side='left')
        self.summary=tk.StringVar(value='Not started. Take and save one separate test JPEG before watching the production folder.')
        ttk.Label(frame,textvariable=self.summary,wraplength=770,justify='left',background='#eaf1ed',padding=16).grid(row=9,column=0,columnspan=3,sticky='ew',pady=6)
        actions=ttk.Frame(frame);actions.grid(row=10,column=0,columnspan=3,sticky='w',pady=10)
        ttk.Button(actions,text='Export redacted diagnostics',command=self.export).pack(side='left',padx=(0,10))
        ttk.Button(actions,text='Open last wrap-up',command=self.show_wrapup).pack(side='left')
        ttk.Label(frame,text='Activity (bounded display; it is not the upload queue)').grid(row=11,column=0,columnspan=3,sticky='w')
        self.log=tk.Text(frame,height=8,wrap='word',font=('Courier',9),state='disabled');self.log.grid(row=12,column=0,columnspan=3,sticky='nsew',pady=6);frame.rowconfigure(12,weight=1)
        root.protocol('WM_DELETE_WINDOW',self.close);root.after(500,self.tick)

    def pick_folder(self,var):
        p=filedialog.askdirectory(parent=self.root)
        if p:var.set(p)

    def running(self):
        return self.process is not None and self.process.poll() is None

    def controls(self,running):
        for e in self.inputs+self.start_buttons:e.configure(state='disabled' if running else 'normal')
        self.pause.configure(state='normal' if running else 'disabled')

    def start(self,mode):
        if self.running():return
        source=Path(self.folder.get()).expanduser().resolve();state=Path(self.state.get()).expanduser().resolve()
        try:
            agent.API(self.server.get().strip(),self.token.get().strip(),self.local.get())
            if not source.is_dir() or state==source or source in state.parents:raise ValueError('Select an existing event folder; keep the queue directory outside it.')
            if not self.token.get().strip():raise ValueError('Enter the device token.')
            extra=[]
            if mode=='preflight':
                photo=filedialog.askopenfilename(parent=self.root,title='Choose one NEW, separate test JPEG',initialdir=str(source),filetypes=[('JPEG photos','*.jpg *.jpeg *.JPG *.JPEG')])
                if not photo:return
                if source not in Path(photo).resolve().parents:raise ValueError('Test photo must be in this event source folder.')
                extra=['--preflight-photo',photo]
            elif mode=='drain':extra=['--drain']
            state.mkdir(parents=True,exist_ok=True)
            self.status_file=state/'operator-status.json'
            self.status_file.unlink(missing_ok=True)
            command=[sys.executable,str(Path(__file__).with_name('lensdrop_agent.py')),'--server',self.server.get().strip(),'--folder',str(source),'--state',str(state),'--status-file',str(self.status_file),'--diagnostics',str(state/'diagnostics-H2.json'),'--reconcile',str(state/'wrapup-H2.json'),*extra]
            if self.local.get():command.append('--allow-http-local')
            env={**os.environ,'LENSDROP_DEVICE_TOKEN':self.token.get().strip(),'PYTHONUNBUFFERED':'1'}
            kwargs={'creationflags':subprocess.CREATE_NEW_PROCESS_GROUP} if os.name=='nt' else {}
            self.process=subprocess.Popen(command,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',**kwargs)
            self.controls(True);self.summary.set('Starting. Destination and saved queue identity will be checked before transfer.')
            def read_output(proc):
                try:
                    for line in proc.stdout:self.events.put(('line',line.strip()))
                    proc.wait();self.events.put(('exit',proc.returncode))
                finally:proc.stdout.close()
            threading.Thread(target=read_output,args=(self.process,),daemon=True).start()
        except Exception as e:messagebox.showerror('Cannot start',str(e),parent=self.root)

    def stop(self):
        if not self.running():return
        try:self.process.send_signal(signal.CTRL_BREAK_EVENT if os.name=='nt' else signal.SIGINT)
        except OSError:pass
        self.summary.set('Pausing after a transfer checkpoint. Pending work is retained; do not clear the queue directory.')
        self.pause.configure(state='disabled')

    def append(self,text):
        # A bounded display is safe; only persistent queue records determine work.
        self.log.configure(state='normal');self.log.insert('end',text+'\n')
        lines=int(self.log.index('end-1c').split('.')[0])
        if lines>80:self.log.delete('1.0',f'{lines-80}.0')
        self.log.see('end');self.log.configure(state='disabled')

    def tick(self):
        try:
            while True:
                kind,value=self.events.get_nowait()
                if kind=='exit':
                    self.controls(False);self.summary.set(f'Stopped (exit {value}). Queue retained. Review the wrap-up report before switching event.')
                    if self.closing:self.root.destroy();return
                elif value.startswith('{'):
                    try:
                        report=json.loads(value)
                        if 'production' in report:self.display(report)
                        elif report.get('publication')=='isolated-test-never-public':self.append('Preflight verified. Test photo stays private; camera-card completeness is not checked.')
                    except ValueError:self.append('A status record could not be displayed; queue is unchanged.')
                else:self.append(value[:1000])
        except queue.Empty:pass
        if self.running() and hasattr(self,'status_file'):
            try:self.display(json.loads(self.status_file.read_text(encoding='utf-8')))
            except (OSError,ValueError):pass
        self.root.after(1000,self.tick)

    def display(self,r):
        p=r.get('production',{});c=r.get('cloud');t=r.get('preflight',{})
        cloud='Unknown / awaiting cloud check' if not c else str(c.get('live','?'))+' published (snapshot; may be stale)'
        self.summary.set(f"01 Source received: {p.get('received',0)}  |  Waiting: {p.get('queued',0)}  |  Need attention: {p.get('blocked',0)}\n02 Verified receipts (local): {p.get('uploaded',0)}  |  03 Guest-visible: {cloud}\nIsolated tests: {t.get('uploaded',0)} verified. Camera-card completeness is not established.")

    def export(self):
        try:
            report=read_diagnostics(Path(self.state.get()).expanduser())
            name=filedialog.asksaveasfilename(parent=self.root,defaultextension='.json',initialfile='LensDrop-diagnostics-H2.json')
            if name:agent.write_report(Path(name),report);self.append('Redacted diagnostics exported; no photos, filenames, paths or credentials.')
        except Exception as e:messagebox.showerror('Diagnostics unavailable',str(e),parent=self.root)

    def show_wrapup(self):
        try:
            report=json.loads((Path(self.state.get()).expanduser()/'wrapup-H2.json').read_text(encoding='utf-8'))
            win=tk.Toplevel(self.root);win.title('Wrap-up snapshot - keep originals');text=tk.Text(win,width=88,height=30,wrap='word');text.pack(fill='both',expand=True);text.insert('1.0',json.dumps(report,indent=2));text.configure(state='disabled')
        except (OSError,ValueError):messagebox.showinfo('No wrap-up yet','Pause or finish a run first. A report will be written without stopping or deleting source files.',parent=self.root)

    def close(self):
        if self.running():
            if not messagebox.askyesno('Pause and close?','Pause transfer and close after the checkpoint? All pending work stays in the queue.',parent=self.root):return
            self.closing=True;self.stop()
        else:self.root.destroy()


def main():
    root=tk.Tk();Console(root);root.mainloop()

if __name__=='__main__':main()
