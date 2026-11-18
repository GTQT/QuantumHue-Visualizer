from PIL import Image
import numpy as np, os

p = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
out = r'E:\模组开发\GTQT\QuantumHue-Visualizer\build\gifprobe'
im = Image.open(p)

def boost(f, k=6.0):
    a = np.asarray(f).astype(np.float32)
    a = np.clip((a - 14) * k, 0, 255)
    return Image.fromarray(a.astype(np.uint8))

# final frame
im.seek(401); boost(im.convert('RGB')).save(os.path.join(out,'final.png'))

# zoom on "HOOVER" glitch region frames 180..280 every 10, crop x 240..560 y 320..420
crops=[]
for i in range(180, 281, 10):
    im.seek(i)
    f = boost(im.convert('RGB'))
    c = f.crop((230, 320, 620, 420)).resize((780, 200), Image.NEAREST)
    crops.append((i,c))
sheet = Image.new('RGB', (780, 200*len(crops)), (30,30,30))
from PIL import ImageDraw
d = ImageDraw.Draw(sheet)
for k,(i,c) in enumerate(crops):
    sheet.paste(c, (0, k*200))
    d.text((5, k*200+2), str(i), fill=(0,255,0))
sheet.save(os.path.join(out,'glitch_zoom.png'))

# consecutive frame diff to characterize per-frame noise
def diffimg(i,j):
    im.seek(i); a = np.asarray(im.convert('RGB')).astype(int)
    im.seek(j); b = np.asarray(im.convert('RGB')).astype(int)
    d = np.abs(b-a).sum(axis=2)
    return d

for i,j in [(300,301),(300,302),(300,310)]:
    d = diffimg(i,j)
    print(i,j,'diff>20', int((d>20).sum()), 'max', int(d.max()))
    m = np.clip(d*3,0,255).astype(np.uint8)
    Image.fromarray(m).save(os.path.join(out,f'diff_{i}_{j}.png'))

# crop of top area at frames 0..60 to see intro
crops=[]
for i in range(0, 61, 4):
    im.seek(i)
    f = boost(im.convert('RGB'))
    c = f.crop((25, 25, 640, 230)).resize((900, 300), Image.LANCZOS)
    crops.append((i,c))
sheet = Image.new('RGB', (900, 300*len(crops)), (30,30,30))
d = ImageDraw.Draw(sheet)
for k,(i,c) in enumerate(crops):
    sheet.paste(c, (0, k*300))
    d.text((5, k*300+2), str(i), fill=(0,255,0))
sheet.save(os.path.join(out,'intro_zoom.png'))
print('done')
