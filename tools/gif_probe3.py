from PIL import Image, ImageEnhance
import numpy as np, os

p = r'C:\Users\Meowmel\Desktop\1c87a0ecd90679bac420a4f1cf143ebf.gif'
out = r'E:\模组开发\GTQT\QuantumHue-Visualizer\build\gifprobe'
os.makedirs(out, exist_ok=True)
im = Image.open(p)

def boost(f):
    a = np.asarray(f).astype(np.float32)
    # subtract dark floor and stretch
    a = np.clip((a - 14) * 6.0, 0, 255)
    return Image.fromarray(a.astype(np.uint8))

for i in [0, 60, 120, 200, 300, 400]:
    im.seek(i); f = im.convert('RGB')
    f.save(os.path.join(out, f'f{i}_raw.png'))
    boost(f).save(os.path.join(out, f'f{i}_boost.png'))

# boosted contact sheet full frames
sel = list(range(0, 402, 40))
ims = []
for i in sel:
    im.seek(i); f = im.convert('RGB')
    ims.append(boost(f).resize((327,333), Image.LANCZOS))
sheet = Image.new('RGB', (327*5, 333*2))
for k,fr in enumerate(ims[:10]):
    sheet.paste(fr, ((k%5)*327, (k//5)*333))
sheet.save(os.path.join(out,'boost_sheet.png'))
print('ok')
