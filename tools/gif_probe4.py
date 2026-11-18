from PIL import Image
import numpy as np, os

p = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
out = r'E:\模组开发\GTQT\QuantumHue-Visualizer\build\gifprobe'
os.makedirs(out, exist_ok=True)
im = Image.open(p)
N = im.n_frames
frames = []
for i in range(N):
    im.seek(i)
    frames.append(np.asarray(im.convert('RGB')).astype(np.int16))

# activity: number of pixels differing from frame 0 significantly, and mean luminance
base = frames[0]
prev = frames[0]
rows = []
for i,f in enumerate(frames):
    d = np.abs(f-base).sum(axis=2)
    active = int((d>30).sum())
    dp = np.abs(f-prev).sum(axis=2)
    chg = int((dp>30).sum())
    lum = float(f.mean())
    rows.append((i, active, chg, lum))
    prev = f

print('frame active chg lum')
for i in range(0, N, 10):
    print(rows[i])

# find frames where change is large
print('big changes:')
for i,(fr,a,c,l) in enumerate(rows):
    if c > 3000:
        print(fr, a, c, round(l,2))

# timeline of text rows: count non-dark pixels per horizontal band (rows of text)
# text color bright (lum>90) pixels
def band_counts(f):
    m = (f.sum(axis=2) > 90*3)
    return m.sum(axis=1)
for i in [30,40,50,60,70,80,100,120,140,160,180,200,220,240,260,280,300,320,340,360,380,400]:
    f = frames[i]
    bc = band_counts(f)
    # bands with >5 bright pixels
    bands=[]
    inb=False
    for y,v in enumerate(bc):
        if v>3 and not inb: start=y; inb=True
        elif v<=3 and inb: bands.append((start,y)); inb=False
    if inb: bands.append((start,len(bc)))
    print(i, bands[:20])
