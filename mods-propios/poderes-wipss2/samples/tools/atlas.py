# Convierte un video sobre fondo negro en una hoja de animacion (flipbook) 6x6 premultiplicada.
# uso: atlas.py video salida.png [gray] ; imprime "cw ch" (tamano de celda)
import sys, subprocess, os, tempfile, glob
from PIL import Image, ImageChops
vid, out = sys.argv[1], sys.argv[2]
gray = len(sys.argv) > 3 and sys.argv[3] == "gray"
N = 36
tmp = tempfile.mkdtemp()
subprocess.run(["ffmpeg", "-v", "error", "-i", vid, "-vf", "scale=512:-2", f"{tmp}/f%04d.png"], check=True)
fs = sorted(glob.glob(f"{tmp}/f*.png"))
lum = []
for f in fs:
    im = Image.open(f).convert("L"); lum.append(sum(im.resize((32, 32)).getdata()))
peak = max(lum)
act = [i for i, v in enumerate(lum) if v > peak * 0.08]
a, b = (act[0], act[-1]) if act else (0, len(fs) - 1)
pick = [fs[a + round(i * (b - a) / (N - 1))] for i in range(N)]
bbox = None
for f in pick:
    m = Image.open(f).convert("L").point(lambda v: 255 if v > 14 else 0).getbbox()
    if m:
        bbox = m if bbox is None else (min(bbox[0], m[0]), min(bbox[1], m[1]), max(bbox[2], m[2]), max(bbox[3], m[3]))
W, H = Image.open(pick[0]).size
if bbox is None:
    bbox = (0, 0, W, H)
pad = 6
bbox = (max(0, bbox[0] - pad), max(0, bbox[1] - pad), min(W, bbox[2] + pad), min(H, bbox[3] + pad))
bw, bh = bbox[2] - bbox[0], bbox[3] - bbox[1]
if bw >= bh:
    cw, ch = 192, max(24, round(192 * bh / bw))
else:
    ch, cw = 192, max(24, round(192 * bw / bh))
sheet = Image.new("RGBA", (cw * 6, ch * 6), (0, 0, 0, 0))
for i, f in enumerate(pick):
    im = Image.open(f).convert("RGB").crop(bbox).resize((cw, ch), Image.LANCZOS)
    # bordes a negro para que el quad no muestre corte
    if gray:
        g = im.convert("L"); im = Image.merge("RGB", (g, g, g))
    r, gch, bch = im.split()
    al = ImageChops.lighter(ImageChops.lighter(r, gch), bch)
    tile = Image.merge("RGBA", (r, gch, bch, al))
    # desvanecer bordes del recuadro
    mask = Image.new("L", (cw, ch), 0)
    from PIL import ImageDraw, ImageFilter
    ImageDraw.Draw(mask).rectangle((4, 4, cw - 5, ch - 5), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(3))
    px = tile.load(); mk = mask.load()
    for y in range(ch):
        for x in range(cw):
            p = px[x, y]; k = mk[x, y] / 255.0
            px[x, y] = (int(p[0] * k), int(p[1] * k), int(p[2] * k), int(p[3] * k))
    sheet.paste(tile, ((i % 6) * cw, (i // 6) * ch))
sheet.save(out, optimize=True)
print(cw, ch)
