from PIL import Image
import numpy as np
P = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
im = Image.open(P)
def fr(i):
    im.seek(i); return np.asarray(im.convert('RGB')).astype(np.int16)
for a,b in [(300,301),(300,302),(300,310),(120,121),(50,51)]:
    A,B = fr(a),fr(b)
    d = np.abs(A-B).sum(axis=2)
    ys,xs = np.nonzero(d>12)
    print(a,b,'changed',len(xs),'bbox', (xs.min(),ys.min(),xs.max(),ys.max()) if len(xs) else None, 'max',int(d.max()))
    # rows with most changes
    if len(xs):
        rows = np.bincount(ys, minlength=A.shape[0])
        print('  top rows', sorted(enumerate(rows), key=lambda t:-t[1])[:6])

# unique colors in h2 band per frame
for i in (200,220,230,240,250,260,270,280,300,350):
    a = fr(i)[360:412]
    m = a.sum(axis=2)>200
    px = a[m]
    if len(px)==0: continue
    cols,counts = np.unique(px, axis=0, return_counts=True)
    order = np.argsort(-counts)[:6]
    print(i, [(tuple(int(v) for v in cols[k]), int(counts[k])) for k in order])
