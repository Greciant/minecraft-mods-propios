#!/usr/bin/env python3
"""Arma ref/entregas/comparacion.png (juego contra video en cada golpe) y aurachad-preview.mp4 (fotogramas del autotest + musica desde la activacion).
Uso: montar_entregas.py <carpeta aurachad_frames> <carpeta de salida>. Los fotogramas son uno por tick (20 fps) desde la activacion."""
import json, os, subprocess, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
frames, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
hits = json.load(open(os.path.join(ROOT, "src/main/resources/assets/aurachad/chad_hits.json")))["hits"]
video = os.path.join(ROOT, "ref/referencia.mp4")
tmp = os.path.join(out, "_vid"); os.makedirs(tmp, exist_ok=True)
W = 240; cells = []
for h in hits:
    t = h["t"]
    n = min(int((t + 0.2) * 20), len(os.listdir(frames)) - 1)
    g = Image.open(os.path.join(frames, "fr_%04d.png" % n)).convert("RGB")
    vp = os.path.join(tmp, "v%02d.png" % hits.index(h))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "%.2f" % (t + 0.1), "-i", video, "-frames:v", "1", vp], check=True)
    v = Image.open(vp).convert("RGB")
    cells.append((h["pose"], t, g, v))
H = int(W * 16 / 9)
sheet = Image.new("RGB", (W * len(cells) // 2 * 2 // 2 * 2, 1), "black")
cols = 5; rows = (len(cells) + cols - 1) // cols
sheet = Image.new("RGB", (cols * W, rows * (H * 2 + 20)), "black"); d = ImageDraw.Draw(sheet)
for i, (pose, t, g, v) in enumerate(cells):
    x, y = (i % cols) * W, (i // cols) * (H * 2 + 20)
    d.text((x + 4, y + 4), "%s %.2fs  juego (arriba) / video (abajo)" % (pose, t), fill="white")
    sheet.paste(g.resize((W, H)), (x, y + 20)); sheet.paste(v.resize((W, H)), (x, y + 20 + H))
sheet.save(os.path.join(out, "comparacion.png"))
music = os.path.join(ROOT, "src/main/resources/assets/aurachad/sounds/aura_chad.ogg")
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", "20", "-i", os.path.join(frames, "fr_%04d.png"), "-i", music,
                "-map", "0:v", "-map", "1:a", "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
                os.path.join(out, "aurachad-preview.mp4")], check=True)
print("ok", out)
