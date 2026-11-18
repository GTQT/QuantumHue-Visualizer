"""Measure per-line typewriter reveal, jitter, glitch white-out and colour cycle."""
from PIL import Image
import numpy as np

P = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
im = Image.open(P)
N = im.n_frames
frames = []
for i in range(N):
    im.seek(i)
    frames.append(np.asarray(im.convert('RGB')).astype(np.int16))
arr = np.stack(frames)

# text bands (y0, y1 inclusive-exclusive) measured from the composed frames
bands = {
    'header': (36, 57), 'huge': (60, 117), 'sub': (130, 141),
    'warning': (144, 166), 'l1': (168, 195), 'l2': (196, 221),
    'l3': (225, 246), 'l4': (252, 276), 'l5': (279, 300),
    'bulletin': (333, 357), 'h1': (360, 381), 'h2': (385, 410),
    'h3': (414, 438), 'h4': (441, 462), 'h5': (468, 489),
    'h6': (492, 519), 'h7': (522, 546), 'conn': (578, 640),
}


def mask(f, y0, y1, thr=70):
    a = f[y0:y1]
    return a.sum(axis=2) > thr * 3


print('=== per-band first/last frame with >12 lit px ===')
for name, (y0, y1) in bands.items():
    cnt = np.array([mask(f, y0, y1).sum() for f in frames])
    lit = np.where(cnt > 12)[0]
    first, last = (lit[0], lit[-1]) if len(lit) else (-1, -1)
    print(f'{name:9s} first={first:3d} last={last:3d} peak={cnt.max():4d}')

print('\n=== rightmost lit x during reveal (typewriter head) ===')
for name in ('l1', 'l2', 'l3', 'l4', 'l5', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'h7'):
    y0, y1 = bands[name]
    cnt = np.array([mask(f, y0, y1).sum() for f in frames])
    lit = np.where(cnt > 12)[0]
    if not len(lit):
        continue
    first, last = lit[0], lit[-1]
    # find the frame where width stops growing
    xs = []
    for i in range(first, min(N, last + 5)):
        m = mask(frames[i], y0, y1)
        x = np.where(m.any(axis=0))[0]
        xs.append((i, int(x.max()) if len(x) else 0))
    final_x = xs[-1][1]
    settle = next((i for i, x in xs if x >= final_x - 2), last)
    left = np.where(mask(frames[last], y0, y1).any(axis=0))[0]
    print(f'{name:9s} appear={first:3d} settle={settle:3d} frames={settle-first:3d} '
          f'~{(settle-first)*0.15:.2f}s  x=[{left.min() if len(left) else 0},{final_x}]')

print('\n=== vertical jitter while static (std of lit-pixel centre of mass) ===')
for name in ('l1', 'l2', 'l3', 'h2'):
    y0, y1 = bands[name]
    ys = []
    for i in range(300, 330):
        m = mask(frames[i], y0, y1)
        if m.sum() > 12:
            yy, _ = np.nonzero(m)
            ys.append(yy.mean() + y0)
    print(name, 'centre mean', round(float(np.mean(ys)), 2) if ys else None,
          'std', round(float(np.std(ys)), 2) if ys else None,
          'range', (round(min(ys), 1), round(max(ys), 1)) if ys else None)

print('\n=== glitch: white pixels + lit-pixel count in h2 band ===')
y0, y1 = bands['h2']
for i in range(175, 345, 5):
    a = frames[i][y0:y1]
    white = ((a[:, :, 0] > 235) & (a[:, :, 1] > 235) & (a[:, :, 2] > 235)).sum()
    lit = (a.sum(axis=2) > 210).sum()
    print(f'{i:3d} white={white:4d} lit={lit:4d}')

print('\n=== h2 colour cycle: mean RGB of lit pixels, every 10 frames ===')
for i in range(180, 402, 10):
    a = frames[i][bands['h2'][0]:bands['h2'][1]]
    m = a.sum(axis=2) > 120 * 3
    if m.sum() < 50:
        continue
    r, g, b = a[m].mean(axis=0)
    print(f'{i:3d} rgb=({r:5.0f},{g:5.0f},{b:5.0f}) lit={m.sum():4d}')

print('\n=== glow spill: mean bg luminance at offsets above l1 text ===')
for dy in (2, 4, 6, 8, 12):
    y = bands['l1'][0] - dy
    v = arr[300:330, y, 40:560].mean()
    print(f'  -{dy}px: {v:.2f}')

print('\n=== huge number region white flash frames ===')
y0, y1 = bands['conn']
for i in range(330, N, 3):
    a = frames[i][y0:y1]
    white = ((a[:, :, 0] > 235) & (a[:, :, 1] > 235) & (a[:, :, 2] > 235)).sum()
    print(f'{i:3d} white={white:5d}')
