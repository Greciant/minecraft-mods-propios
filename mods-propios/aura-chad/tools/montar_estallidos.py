#!/usr/bin/env python3
"""Arma ref/entregas/estallidos.png: una fila por golpe fuerte (5,13 7,69 12,81 s) con los fotogramas del autotest en t-0,10, t, +0,05, +0,10, +0,20, +0,35 y +0,60,
y mide con numeros que ningun pixel brillante de las chispas tenga R > B (compara contra el mismo cuadro sin estallido: t-0,10).
Uso: montar_estallidos.py <carpeta aurachad_frames> <salida.png>. Fotogramas: uno por tick (20 fps) desde la activacion."""
import sys, os
from PIL import Image, ImageDraw
from PIL import ImageChops

frames, out = sys.argv[1], sys.argv[2]
HITS = [5.13, 7.69, 12.81]
OFFS = [-0.10, 0.0, 0.05, 0.10, 0.20, 0.35, 0.60]
W = 228; H = int(W * 1131 / 720)
sheet = Image.new("RGB", (W * len(OFFS), (H + 16) * len(HITS)), "black"); d = ImageDraw.Draw(sheet)
tot = 0; warm = 0
for r, t in enumerate(HITS):
    for c, o in enumerate(OFFS):
        n = round((t + o) * 20)
        im = Image.open(os.path.join(frames, "fr_%04d.png" % n)).convert("RGB")
        sheet.paste(im.resize((W, H)), (c * W, r * (H + 16) + 16))
        d.text((c * W + 4, r * (H + 16) + 2), "golpe %.2f s  t%+.2f  (tick %d)" % (t, o, n), fill="white")
        R, G, B = im.split()
        mx = ImageChops.lighter(ImageChops.lighter(R, G), B)
        bright = mx.point(lambda v: 255 if v >= 150 else 0)
        rb = ImageChops.subtract(R, B).point(lambda v: 255 if v > 0 else 0)       # R > B
        w = ImageChops.multiply(bright, rb)
        nb, nw = bright.histogram()[255], w.histogram()[255]
        print("t=%.2f%+.2f tick %3d: brillantes=%6d con R>B=%5d" % (t, o, n, nb, nw))
        if o != -0.10:
            tot += nb; warm += nw
sheet.save(out)
print("TOTAL brillantes (cuadros con estallido) =", tot, " con R>B =", warm)
