from PIL import Image
import numpy as np
P = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
im = Image.open(P)
def fr(i):
    im.seek(i); return np.asarray(im.convert('RGB')).astype(np.int16)
A = fr(401)
rows = A.sum(axis=2).mean(axis=1)
for y in range(540, 666, 4):
    print(y, round(float(rows[y]),1), 'max', int(A[y].max()))
print('unique bright bottom:')
a = A[560:666]
m = a.sum(axis=2)>300
cols,counts=np.unique(a[m],axis=0,return_counts=True)
o=np.argsort(-counts)[:10]
print([(tuple(int(v) for v in cols[k]),int(counts[k])) for k in o])
# save unboosted frame 220 and 401 crops
for i in (200,220,240,260,300,401):
    im.seek(i)
    im.convert('RGB').crop((20,20,640,666)).save(rf'E:\模组开发\GTQT\QuantumHue-Visualizer\build\gifprobe\raw_{i}.png')
print('saved')
