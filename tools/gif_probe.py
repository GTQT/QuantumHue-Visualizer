from PIL import Image
import numpy as np, os, sys

p = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
out = r'E:\模组开发\GTQT\QuantumHue-Visualizer\build\gifprobe'
os.makedirs(out, exist_ok=True)

im = Image.open(p)
print('frames', im.n_frames, 'size', im.size)
frames = []
for i in range(im.n_frames):
    im.seek(i)
    frames.append(im.convert('RGB'))

# background estimate: corner color
a0 = np.asarray(frames[0]).astype(int)
print('corner colors', a0[0,0], a0[0,-1], a0[-1,0], a0[-1,-1])

# per-frame bbox of pixels differing from bg by > threshold
bg = np.median(a0[0:10,0:10].reshape(-1,3), axis=0)
print('bg', bg)
boxes = []
for i,f in enumerate(frames):
    a = np.asarray(f).astype(int)
    d = np.abs(a-bg).sum(axis=2)
    m = d > 40
    ys, xs = np.where(m)
    if len(xs)==0:
        boxes.append(None); continue
    boxes.append((xs.min(), ys.min(), xs.max(), ys.max(), len(xs)))

nonnull = [ (i,b) for i,b in enumerate(boxes) if b ]
print('frames with content', len(nonnull))
xs0 = min(b[0] for i,b in nonnull); ys0=min(b[1] for i,b in nonnull)
xs1 = max(b[2] for i,b in nonnull); ys1=max(b[3] for i,b in nonnull)
print('global bbox', xs0, ys0, xs1, ys1)

# sample bboxes over time
for i in range(0, im.n_frames, 20):
    print(i, boxes[i])

# Save crops of interesting frames in a grid
sel = list(range(0, im.n_frames, 25))
print('sel', len(sel))
grid_w, grid_h = 5, (len(sel)+4)//5
cw, ch = (xs1-xs0+1), (ys1-ys0+1)
scale = 480.0/cw
tw, th = int(cw*scale), int(ch*scale)
sheet = Image.new('RGB', (tw*grid_w, th*grid_h), (40,40,40))
for k,i in enumerate(sel):
    f = frames[i].crop((xs0, ys0, xs1+1, ys1+1)).resize((tw, th), Image.LANCZOS)
    sheet.paste(f, ((k%grid_w)*tw, (k//grid_w)*th))
sheet.save(os.path.join(out,'sheet.png'))
print('sheet', sheet.size)

# also save a few full frames
for i in [0, 50, 100, 150, 200, 250, 300, 350, 400]:
    if i < len(frames):
        frames[i].save(os.path.join(out, f'frame_{i:03d}.png'))
