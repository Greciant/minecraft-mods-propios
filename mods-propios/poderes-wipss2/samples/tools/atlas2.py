# Hoja 6x6 premultiplicada desde una carpeta de fotogramas (jpg sobre fondo negro).
# uso: atlas2.py carpeta salida.png [gray] [a-b]   a-b = rango de fotogramas (1-based) a usar en vez del auto
# imprime "cw ch"
import sys, glob
from PIL import Image, ImageChops, ImageDraw, ImageFilter
src, out = sys.argv[1], sys.argv[2]
gray = "gray" in sys.argv[3:]
smoke = "smoke" in sys.argv[3:]
gain = float(next((a[5:] for a in sys.argv[3:] if a.startswith("gain=")), "1"))
rng = next((a for a in sys.argv[3:] if "-" in a and a[0].isdigit()), None)
N, CELL = 36, 192
fs = sorted(glob.glob(f"{src}/*.jpg"))
if rng:
    a, b = map(int, rng.split("-")); a -= 1; b -= 1
else:
    lum = [sum(Image.open(f).convert("L").resize((32, 32)).tobytes()) for f in fs]
    peak = max(lum)
    act = [i for i, v in enumerate(lum) if v > peak * 0.06]
    a, b = (act[0], act[-1]) if act else (0, len(fs) - 1)
pick = [fs[a + round(i * (b - a) / (N - 1))] for i in range(N)]
bbox = None
for f in pick:
    m = Image.open(f).convert("L").point(lambda v: 255 if v > 16 else 0).getbbox()
    if m:
        bbox = m if bbox is None else (min(bbox[0], m[0]), min(bbox[1], m[1]), max(bbox[2], m[2]), max(bbox[3], m[3]))
W, H = Image.open(pick[0]).size
bbox = bbox or (0, 0, W, H)
pad = 8
bbox = (max(0, bbox[0] - pad), max(0, bbox[1] - pad), min(W, bbox[2] + pad), min(H, bbox[3] + pad))
bw, bh = bbox[2] - bbox[0], bbox[3] - bbox[1]
if bw >= bh:
    cw, ch = CELL, max(24, round(CELL * bh / bw))
else:
    ch, cw = CELL, max(24, round(CELL * bw / bh))
mask = Image.new("L", (cw, ch), 0)
ImageDraw.Draw(mask).rectangle((5, 5, cw - 6, ch - 6), fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(3))
sheet = Image.new("RGBA", (cw * 6, ch * 6), (0, 0, 0, 0))
for i, f in enumerate(pick):
    im = Image.open(f).convert("RGB").crop(bbox).resize((cw, ch), Image.LANCZOS)
    # negro profundo a 0 (los jpg traen ruido de compresion)
    im = im.point(lambda v: 0 if v < 10 else int((v - 10) * 255 / 245))
    if gain != 1:
        im = im.point(lambda v: min(255, int(v * gain)))
    if smoke:
        # humo: blanco con alfa = densidad; se tine y se mezcla normal (oscurece)
        g = im.convert("L").point(lambda v: min(255, int(v * 3.2)))
        g = ImageChops.multiply(g, mask)
        w = Image.new("L", (cw, ch), 255)
        sheet.paste(Image.merge("RGBA", (w, w, w, g)), ((i % 6) * cw, (i // 6) * ch))
        continue
    if gray:
        g = im.convert("L"); im = Image.merge("RGB", (g, g, g))
    r, g, b = im.split()
    al = ImageChops.lighter(ImageChops.lighter(r, g), b)
    r, g, b, al = [ImageChops.multiply(c, mask) for c in (r, g, b, al)]
    sheet.paste(Image.merge("RGBA", (r, g, b, al)), ((i % 6) * cw, (i // 6) * ch))
sheet.save(out, optimize=True)
print(cw, ch)
