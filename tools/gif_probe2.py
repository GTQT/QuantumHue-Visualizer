from PIL import Image
import numpy as np, os

p = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
out = r'E:\模组开发\GTQT\QuantumHue-Visualizer\build\gifprobe'
os.makedirs(out, exist_ok=True)
im = Image.open(p)
im.seek(0)
f = im.convert('RGB')
a = np.asarray(f).astype(int)
lum = a.sum(axis=2)
print('lum percentiles', np.percentile(lum, [0,10,50,90,99,99.9,100]))
# histogram of colors by frequency
cols, counts = np.unique(a.reshape(-1,3), axis=0, return_counts=True)
idx = np.argsort(-counts)
for i in idx[:15]:
    print(cols[i], counts[i])
# bright pixels
ys,xs = np.where(lum>250)
print('bright count', len(xs))
if len(xs):
    print('bright bbox', xs.min(), ys.min(), xs.max(), ys.max())
    # crop it
    x0,y0,x1,y1 = max(0,xs.min()-20), max(0,ys.min()-20), min(654,xs.max()+20), min(666,ys.max()+20)
    crop = f.crop((x0,y0,x1,y1))
    crop = crop.resize((crop.width*6, crop.height*6), Image.NEAREST)
    crop.save(os.path.join(out,'bright.png'))
    print('crop', x0,y0,x1,y1, crop.size)

# difference between frame 0 and 200
im.seek(200); f2 = im.convert('RGB'); a2 = np.asarray(f2).astype(int)
d = np.abs(a2-a).sum(axis=2)
ys,xs = np.where(d>10)
print('diff pixels', len(xs))
if len(xs):
    print('diff bbox', xs.min(),ys.min(),xs.max(),ys.max())
    x0,y0,x1,y1 = xs.min(), ys.min(), xs.max()+1, ys.max()+1
    c = f2.crop((x0-10,y0-10,x1+10,y1+10)).resize(((x1-x0+20)*4,(y1-y0+20)*4), Image.NEAREST)
    c.save(os.path.join(out,'diff_0_200.png'))
    print(c.size)
