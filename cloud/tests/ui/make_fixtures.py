from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import os
work=Path(os.environ.get('LENSDROP_UI_WORKDIR','.ui-rehearsal')).resolve()
out=work/'ui-fixtures';out.mkdir(parents=True,exist_ok=True)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
for i,(w,h) in enumerate([(1920,1080),(1200,1800),(1920,1080),(1600,1200)],1):
 im=Image.new('RGB',(w,h));d=ImageDraw.Draw(im)
 for y in range(h):
  t=y/h;d.line((0,y,w,y),fill=(int(30+100*t),int(70+85*t),int(100+65*t)))
 d.ellipse((w*.68,h*.1,w*.68+w*.13,h*.1+w*.13),fill=(247,216,156))
 d.polygon([(0,h*.63),(w*.25,h*.3),(w*.55,h*.73),(w*.8,h*.45),(w,h*.63),(w,h),(0,h)],fill=(61,104,113))
 d.polygon([(0,h*.8),(w*.22,h*.57),(w*.5,h*.8),(w*.8,h*.62),(w,h*.83),(w,h),(0,h)],fill=(36,72,78))
 d.rectangle((0,h*.86,w,h),fill=(19,43,48))
 d.text((w*.045,h*.90),f'SYNTHETIC TEST PHOTO {i:02d} / NOT A REAL EVENT',fill='white',font=ImageFont.truetype(font,max(15,int(w*.018))))
 im.save(out/f'fixture_{i}.jpg',quality=90)
im=Image.new('RGBA',(660,160),(0,0,0,0));d=ImageDraw.Draw(im);d.rounded_rectangle((3,3,145,145),radius=40,fill=(245,248,239,255));d.text((40,16),'L',fill=(27,74,60),font=ImageFont.truetype(font,102));d.text((175,25),'LUCE STUDIO',fill=(246,248,240),font=ImageFont.truetype(font,48));d.text((178,93),'EVENT REHEARSAL',fill=(194,220,203),font=ImageFont.truetype(font,24));im.save(out/'logo.png')
