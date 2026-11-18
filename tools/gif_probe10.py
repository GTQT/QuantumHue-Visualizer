from PIL import Image
import numpy as np
P = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
im = Image.open(P)
N = im.n_frames
arr = []
for i in range(N):
    im.seek(i)
    arr.append(np.asarray(im.convert('RGB')).astype(np.float32))
arr = np.stack(arr)
def series(y0,y1,x0,x1):
    return arr[:, y0:y1, x0:x1].mean(axis=(1,2,3))
for name,(y0,y1,x0,x1) in {
  'warning':(144,166,36,320), 'huge':(60,117,36,300), 'conn':(575,665,30,620), 'l1':(168,195,36,560)}.items():
    s = series(y0,y1,x0,x1)[300:]
    s = s - s.mean()
    f = np.abs(np.fft.rfft(s))
    freqs = np.fft.rfftfreq(len(s), d=0.15)
    top = np.argsort(-f)[:5]
    print(name, [(round(float(freqs[k]),2), round(float(f[k]),1)) for k in top])
    print('  min/max/std', round(float(s.min()),2), round(float(s.max()),2), round(float(s.std()),2))
# warning line brightness over time at 5-frame steps
s = series(144,166,36,320)
print('warning samples', [round(float(s[i]),1) for i in range(300,401,5)])
