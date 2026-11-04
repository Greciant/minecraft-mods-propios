#!/usr/bin/env python3
"""
Genera los modelos 3D del Haki: rocas flotantes, pinchos de obsidiana del suelo y la corona del emblema.

Salida:
  art/<nombre>.bbmodel                              proyecto de Blockbench (formato java_block, texturas incrustadas)
  src/main/resources/assets/haki/models/fx/<n>.json  modelo Java block listo para el juego
  src/main/resources/assets/haki/textures/block/fx_*.png

Uso (desde la raiz del proyecto):  python3 tools/gen_models.py
Las semillas son fijas: volver a ejecutarlo da los mismos archivos. Blockbench abre art/*.bbmodel para retocar;
al exportar como "Java Block/Item" el JSON queda igual que el que se genera aqui.
"""
import base64
import io
import json
import math
import os
import random
import uuid

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "art")
MODELS = os.path.join(ROOT, "src/main/resources/assets/haki/models/fx")
TEX = os.path.join(ROOT, "src/main/resources/assets/haki/textures/block")
for d in (ART, MODELS, TEX):
    os.makedirs(d, exist_ok=True)

FACES = ("north", "east", "south", "west", "up", "down")
TS = 32  # lado de las texturas en pixeles; 1 unidad uv de Minecraft = TS/16 pixeles


# ------------------------------------------------------------------ texturas
def vein_texture(seed, base, vein_core, vein_glow, veins=7, gem=False):
    rnd = random.Random(seed)
    img = Image.new("RGB", (TS, TS))
    px = img.load()
    for y in range(TS):
        for x in range(TS):
            n = rnd.randint(-7, 7)
            shade = 1.0 + 0.25 * math.sin(x * 0.9 + y * 0.5) * 0.3
            px[x, y] = tuple(max(0, min(255, int(c * shade + n))) for c in base)
    glow = Image.new("RGB", (TS, TS), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    core = Image.new("RGB", (TS, TS), (0, 0, 0))
    cd = ImageDraw.Draw(core)
    for _ in range(veins):
        x, y = rnd.uniform(0, TS), rnd.uniform(0, TS)
        a = rnd.uniform(0, 6.28)
        pts = [(x, y)]
        for _ in range(rnd.randint(4, 8)):
            a += rnd.uniform(-0.9, 0.9)
            x += math.cos(a) * rnd.uniform(2, 5)
            y += math.sin(a) * rnd.uniform(2, 5)
            pts.append((x % TS, y % TS) if False else (x, y))
        gd.line(pts, fill=vein_glow, width=3)
        cd.line(pts, fill=vein_core, width=1)
    glow = glow.filter(ImageFilter.GaussianBlur(0.8))
    out = Image.new("RGB", (TS, TS))
    po, pg, pc, pb = out.load(), glow.load(), core.load(), px
    for y in range(TS):
        for x in range(TS):
            g, c, b = pg[x, y], pc[x, y], pb[x, y]
            r = [min(255, b[i] + g[i]) for i in range(3)]
            if max(c) > 0:
                r = list(c)
            po[x, y] = tuple(r)
    if gem:  # cuadrante uv 12..16 de ambos ejes: gema roja muy brillante
        d = ImageDraw.Draw(out)
        d.rectangle((TS * 3 // 4, TS * 3 // 4, TS - 1, TS - 1), fill=(255, 40, 28))
        d.rectangle((TS * 3 // 4 + 2, TS * 3 // 4 + 2, TS - 3, TS - 3), fill=(255, 150, 90))
    return out


def make_textures():
    vein_texture(11, (52, 38, 62), (255, 80, 50), (170, 24, 14), 9).save(os.path.join(TEX, "fx_rock.png"))
    vein_texture(23, (38, 26, 50), (255, 100, 70), (190, 26, 16), 6).save(os.path.join(TEX, "fx_shard.png"))
    c = vein_texture(37, (28, 20, 36), (230, 40, 28), (120, 14, 10), 3, gem=True)
    d = ImageDraw.Draw(c)
    for y in (3, TS - 5):                       # franjas rojas de adorno
        d.rectangle((0, y, TS - 1, y + 1), fill=(220, 30, 24))
    c.save(os.path.join(TEX, "fx_crown.png"))


# ------------------------------------------------------------------ geometria
def cube(frm, to, rot=None, uvseed=0, gem=False):
    """rot = (eje, angulo) alrededor del centro del cubo (Minecraft solo permite un eje y pasos de 22,5)."""
    rnd = random.Random(uvseed)
    faces = {}
    for f in FACES:
        w = rnd.choice((8, 10, 12, 16))
        u = rnd.randint(0, 16 - w)
        v = rnd.randint(0, 16 - w)
        if gem:
            u, v, w = 12, 12, 4
        faces[f] = {"uv": [u, v, u + w, v + w], "texture": "#0"}
    el = {"from": list(frm), "to": list(to), "faces": faces}
    if rot:
        ctr = [(frm[i] + to[i]) / 2 for i in range(3)]
        el["rotation"] = {"angle": rot[1], "axis": rot[0], "origin": ctr}
    return el


def rock(seed):
    rnd = random.Random(seed)
    els = []
    for i in range(rnd.randint(4, 6)):
        sx, sy, sz = rnd.uniform(4, 9), rnd.uniform(4, 9), rnd.uniform(4, 9)
        cx, cy, cz = 8 + rnd.uniform(-3, 3), 8 + rnd.uniform(-3, 3), 8 + rnd.uniform(-3, 3)
        rot = (rnd.choice("xyz"), rnd.choice((-45, -22.5, 22.5, 45)))
        els.append(cube((cx - sx / 2, cy - sy / 2, cz - sz / 2), (cx + sx / 2, cy + sy / 2, cz + sz / 2), rot, seed * 31 + i))
    return els


def shard(seed):
    rnd = random.Random(seed)
    els = []
    widths = [7.5, 6.5, 5.5, 4.5, 3.5, 2.5, 1.5]
    y = 0.0
    for k, w in enumerate(widths):                 # torre afinada con giro: cristal de obsidiana retorcido
        h = 2.8 + rnd.uniform(-0.3, 0.6)
        els.append(cube((8 - w / 2, y, 8 - w / 2), (8 + w / 2, y + h, 8 + w / 2),
                        ("y", 22.5 if k % 2 else -22.5), seed * 17 + k))
        y += h - 0.2
    for j in range(rnd.randint(2, 3)):             # cristales pequenos inclinados en la base
        a = rnd.uniform(0, 6.28)
        cx, cz = 8 + math.cos(a) * 4.2, 8 + math.sin(a) * 4.2
        w, h = rnd.uniform(2, 3.2), rnd.uniform(6, 10)
        els.append(cube((cx - w / 2, 0, cz - w / 2), (cx + w / 2, h, cz + w / 2),
                        (rnd.choice("xz"), rnd.choice((-22.5, 22.5))), seed * 53 + j))
    return els


def crown(seed):
    els = []
    # aro base
    els.append(cube((3, 0, 3), (13, 3, 5), None, 1))
    els.append(cube((3, 0, 11), (13, 3, 13), None, 2))
    els.append(cube((3, 0, 5), (5, 3, 11), None, 3))
    els.append(cube((11, 0, 5), (13, 3, 11), None, 4))
    # ocho picos: esquinas y mitades, alternando alto y bajo
    spikes = [(4, 4, 1.0), (8, 4, 0.7), (12, 4, 1.0), (12, 8, 0.7), (12, 12, 1.0), (8, 12, 0.7), (4, 12, 1.0), (4, 8, 0.7)]
    for n, (x, z, hs) in enumerate(spikes):
        h1, h2, h3 = 5 * hs, 4 * hs, 2.5 * hs
        els.append(cube((x - 1.5, 3, z - 1.5), (x + 1.5, 3 + h1, z + 1.5), None, 10 + n))
        els.append(cube((x - 1, 3 + h1, z - 1), (x + 1, 3 + h1 + h2, z + 1), ("y", 22.5), 20 + n))
        els.append(cube((x - 0.5, 3 + h1 + h2, z - 0.5), (x + 0.5, 3 + h1 + h2 + h3, z + 0.5), None, 30 + n))
    # gemas rojas brillantes al frente (-z) y atras
    els.append(cube((7, 0.6, 2.6), (9, 2.4, 3.0), None, 40, gem=True))
    els.append(cube((7, 0.6, 13.0), (9, 2.4, 13.4), None, 41, gem=True))
    return els


MODEL_DEFS = {
    "rock_0": (rock(101), "fx_rock"), "rock_1": (rock(202), "fx_rock"),
    "rock_2": (rock(303), "fx_rock"), "rock_3": (rock(404), "fx_rock"),
    "shard_0": (shard(5), "fx_shard"), "shard_1": (shard(6), "fx_shard"), "shard_2": (shard(7), "fx_shard"),
    "crown": (crown(0), "fx_crown"),
}


# ------------------------------------------------------------------ salida
def write_java_model(name, elements, tex):
    model = {
        "credit": "Haki - generado con tools/gen_models.py (editable en Blockbench: art/%s.bbmodel)" % name,
        "texture_size": [TS, TS],
        "textures": {"0": "haki:block/" + tex, "particle": "haki:block/" + tex},
        "elements": elements,
    }
    with open(os.path.join(MODELS, name + ".json"), "w") as f:
        json.dump(model, f, indent=1)


def write_bbmodel(name, elements, tex):
    path = os.path.join(TEX, tex + ".png")
    with open(path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode()
    k = TS / 16.0
    els, ids = [], []
    for i, e in enumerate(elements):
        uid = str(uuid.uuid5(uuid.NAMESPACE_URL, "haki/%s/%d" % (name, i)))
        ids.append(uid)
        rot = e.get("rotation")
        rotation = [0, 0, 0]
        if rot:
            rotation["xyz".index(rot["axis"])] = rot["angle"]
        origin = rot["origin"] if rot else [(e["from"][j] + e["to"][j]) / 2 for j in range(3)]
        els.append({
            "name": "%s_%d" % (name, i), "box_uv": False, "rescale": False, "locked": False, "light_emission": 15,
            "render_order": "default", "allow_mirror_modeling": True,
            "from": e["from"], "to": e["to"], "autouv": 0, "color": i % 8, "origin": origin, "rotation": rotation,
            "faces": {f: {"uv": [c * k for c in e["faces"][f]["uv"]], "texture": 0} for f in FACES},
            "type": "cube", "uuid": uid,
        })
    bb = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": name, "model_identifier": "", "visible_box": [1, 1, 0], "variable_placeholders": "",
        "variable_placeholder_buttons": [], "unhandled_modifications": {},
        "resolution": {"width": TS, "height": TS},
        "elements": els, "outliner": ids,
        "textures": [{"path": "", "name": tex + ".png", "folder": "block", "namespace": "haki", "id": "0",
                      "particle": True, "render_mode": "emissive", "render_sides": "auto", "frame_time": 1,
                      "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
                      "internal": True, "saved": True, "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "haki/tex/" + name)),
                      "source": "data:image/png;base64," + b64}],
    }
    with open(os.path.join(ART, name + ".bbmodel"), "w") as f:
        json.dump(bb, f)


if __name__ == "__main__":
    make_textures()
    for n, (els, tex) in MODEL_DEFS.items():
        write_java_model(n, els, tex)
        write_bbmodel(n, els, tex)
        print("modelo", n, len(els), "cubos")
