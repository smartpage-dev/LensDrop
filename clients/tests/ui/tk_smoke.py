from pathlib import Path
import sys,tkinter as tk
from PIL import ImageGrab
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'desktop'))
from lensdrop_console import Console
root=tk.Tk();app=Console(root)
app.server.set('https://your-server.example');app.folder.set('/example/event-rehearsal');app.state.set('/example/queue-rehearsal')
root.update();root.after(500,lambda:None);root.update()
assert len(app.start_buttons)==3
assert app.token.get()==''
assert not app.running()
app.display({'production':{'received':0,'queued':0,'blocked':0,'uploaded':0},'preflight':{'uploaded':0}})
root.update()
ImageGrab.grab().save('LensDrop_H2_Desktop_Console_Linux.png')
root.destroy()
print('PASS Linux/Xvfb Tk operator console creates and renders real widgets; empty token; three mode buttons; idle status. No Windows/macOS execution, real camera or upload-through-GUI test claimed.')
