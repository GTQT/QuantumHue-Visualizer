from PIL import Image
import numpy as np, os

p = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
out = r'E:\模组开发\GTQT\QuantumHue-Visualizer\build\gifprobe'
im = Image.open(p)
N = im.n_frames
frames = []
for i in range(N):
    im.seek(i)
    frames.append(np.asarray(im.convert('RGB')).astype(np.int16))
arr = np.stack(frames)

bands = {
 'header': (36,57), 'huge': (60,117), 'sub': (130,141), 'warning': (144,166),
 'l1': (168,195), 'l2': (196,221), 'l3': (225,246), 'l4': (252,276), 'l5': (279,300),
 'bulletin': (333,357), 'h1': (360,381), 'h2': (385,410), 'h3': (414,438), 'h4': (441,462),
 'h5': (468,489), 'h6': (492,519), 'h7': (522,546), 'conn': (578,640)
}

def bright(f, y0,y1, thr=90):
    a = f[y0:y1]
    m = a.sum(axis=2) > thr*3
    return m

print('band first/last visible (min 20px), width growth')
for name,(y0,y1) in bands.items():
    counts = np.array([bright(f,y0,y1).sum() for f in frames])
    idx = np.where(counts>20)[0]
    first = idx[0] if len(idx) else -1
    print(f'{name:9s} first={first:3d} last={idx[-1] if len(idx) else -1:3d} maxpx={counts.max()}')
    # print count samples during growth
    if first>=0:
        samples = []
        for i in range(max(0,first-2), min(N, first+40), 4):
            samples.append((i,int(counts[i])))
        print('   ', samples)

# cursor? check for small block trailing text early frames - x extent of content in l1 band over time
print('\nline1 x extent over frames 25..70')
for i in range(25, 71, 3):
    m = bright(frames[i], 168,195)
    xs = np.where(m.any(axis=0))[0]
    print(i, (xs.min(), xs.max()) if len(xs) else None, m.sum())

# colour of hydrogen line over time
print('\nhydrogen line dominant color over time')
for i in range(340, N, 3):
    a = frames[i][360:382]
    m = a.sum(axis=2) > 150*3
    if m.sum()<10: continue
    px = a[m]
    print(i, m.sum(), px.mean(axis=0).round(0), 'bright frac', round(float((a.sum(axis=2)>220*3).mean()),3))

# global flicker: mean luminance of fixed flat region and full frame
print('\nflat area stats over time')
flat = arr[:, 600:640, 200:600].astype(float).mean(axis=(1,2,3))
print('flat mean', flat.mean(), 'std', flat.std(), 'min', flat.min(), 'max', flat.max())
# per-frame diff in flat area
df = np.abs(arr[1:,600:640,200:600]-arr[:-1,600:640,200:600]).mean()
print('flat frame-to-frame diff', df)
# panel background area (no text) at y 300..320 x 100..500
flat2 = arr[:, 300:325, 60:600].astype(float).mean(axis=(1,2,3))
print('flat2 mean', flat2.mean(), 'std', flat2.std(), 'min', flat2.min(), 'max', flat2.max())
print('flat2 first 10', flat2[:10].round(2))
