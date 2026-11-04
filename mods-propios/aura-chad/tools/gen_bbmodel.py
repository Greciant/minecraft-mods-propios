#!/usr/bin/env python3
"""Genera ref/blockbench/chad_poses.bbmodel y src/main/resources/assets/aurachad/animations/chad.animation.json
a partir de la tabla de golpes (HITS) de abajo: cortes secos, la pose queda quieta entre golpes y salta de una vez en cada uno. Despues se edita en Blockbench y se exporta la animacion
(Animation > Export Bedrock animation) encima del JSON; ChadPoses lo lee en tiempo de ejecucion.
Convencion: los grados del archivo son los radianes de Java (PlayerModel) en grados, con Z negado (signo de Blockbench): file = (deg(x), deg(y), -deg(z))."""
import base64, io, json, math, os, uuid
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOTAL = 21.8
#            rArm (x y z)           lArm (x y z)          headX headY bodyX yawDeg spread chest walk
P = {
 "SHY":    [-0.35, 0.55, 0.12,  -0.35, -0.55, -0.12,  0.40, 0.15, 0.10, 0,   0,    1.00, 0],
 "SHY2":   [-0.45, 0.65, 0.08,  -0.45, -0.65, -0.08,  0.30,-0.15, 0.12, 0,   0,    1.00, 0],
 "RELAX":  [ 0.00, 0.00, 0.15,   0.00,  0.00, -0.15,  0.00, 0.00, 0.00, 0,   0.05, 1.00, 0],
 "UPV":    [-0.10, 0.00, 2.50,  -0.10,  0.00, -1.80, -0.05, 0.00, 0.00, 0,   0.10, 1.06, 0],
 "DOUBLE": [-0.15, 0.00, 1.75,  -0.15,  0.00, -1.75, -0.10, 0.00,-0.05, 0,   0.22, 1.12, 0],
 "SIDE":   [-0.35, 0.55, 0.00,  -0.35, -0.45,  0.00,  0.25,-1.10, 0.05, 65,  0.18, 1.10, 0],
 "ONEUP":  [-0.20, 0.00, 1.35,   0.00,  0.00, -2.40, -0.10, 0.00,-0.05,-15,  0.20, 1.10, 0],
 "FINGER": [-0.90, 0.00, 0.10,  -0.60, -0.40, -0.20,  0.00, 0.00, 0.00, 0,   0.10, 1.10, 0],
 "SHYMUS": [-0.35, 0.55, 0.12,  -0.35, -0.55, -0.12,  0.20, 0.00, 0.05, 0,   0.15, 1.12, 0],
 "THIN":   [-0.05, 0.00, 0.15,   0.10,  0.00, -0.35,  0.00, 0.10, 0.00, 0,   0.05, 1.00, 0],
 "TURN":   [ 0.05, 0.00, 0.12,   0.05,  0.00, -0.12,  0.00, 0.00, 0.00, 180, 0.00, 1.00, 1],
}
P["DBLTHIN"] = P["DOUBLE"][:11] + [1.00] + P["DOUBLE"][12:]   # doble biceps delgado: mismo gesto, pecho sin inflar
P["DOUBLE"] = P["DOUBLE"]
# ---- Tabla unica de golpes (cortes secos). Entre golpes la pose queda quieta; en cada golpe salta de una vez a la nueva. ----
# Objetivos de antebrazo (afuera, arriba, frente), definidos mas abajo (T_*)
T_SHY = (-0.35, -0.8, 0.5); T_UPV_R = (0.5, 0.85, 0.0); T_UPV_L = (0.2, 1.0, 0.0); T_DBL = (-0.3, 1.0, 0.0); T_MUS = (-0.4, 1.0, 0.0)
T_SIDE = (-0.5, 0.0, 0.85); T_ONE_R = (0.0, 1.0, 0.1); T_ONE_L = (0.9, 1.0, 0.0); T_FNG_R = (0.0, 1.0, 0.3); T_FNG_L = (0.3, -0.9, 0.35)
# (t, pose, codo D, codo I, objetivo D, objetivo I, camara, fuerza del estallido)
HITS = [
 (0.00,  "SHY",     25,  25, T_SHY,   T_SHY,   1.0, 0.5),
 (3.86,  "UPV",      5,  65, T_UPV_R, T_UPV_L, 1.0, 0.7),
 (4.50,  "DBLTHIN", 100, 100, T_DBL,   T_DBL,   1.0, 0.8),
 (5.13,  "DOUBLE",  110, 110, T_MUS,   T_MUS,   1.0, 1.5),   # la transformacion en el drop; la camara se acerca suave hasta ZOOM_RAMP
 (7.69,  "SIDE",     95,  95, T_SIDE,  T_SIDE,  0.8, 1.0),
 (9.93,  "ONEUP",   110,  10, T_ONE_R, T_ONE_L, 1.0, 1.0),
 (12.81, "FINGER",  120,  10, T_FNG_R, T_FNG_L, 0.6, 1.2),
 (14.08, "SHYMUS",   30,  30, T_SHY,   T_SHY,   1.0, 1.2),
 (15.04, "THIN",     12,  12, None,    None,    1.0, 0.7),
 (17.91, "TURN",     12,  12, None,    None,    1.0, 0.5),   # giro seco + caminata hasta el final
]
ZOOM_RAMP = [(7.6, 0.8)]      # lo unico continuo ademas de la caminata: acercamiento de camara 5,13 -> 7,6 s
WALK_START = 17.91
D = math.degrees
RX,RY,RZ,LX,LY,LZ,HX,HY,BX,YAW,SPREAD,CHEST,WALK = range(13)
def r(x): return round(x, 4)
def fmt(v): return [int(a) if a == int(a) else a for a in v]
def U(): return str(uuid.uuid4())

HIT_T = [h[0] for h in HITS]
def hit_at(t):
    """Indice del golpe vigente en t (meseta): el ultimo con tiempo <= t."""
    i = 0
    for k, ht in enumerate(HIT_T):
        if ht <= t + 1e-9: i = k
    return i
def pose_at(t): return P[HITS[hit_at(t)][1]]

# ---- Pistas con cortes: lista de (t, valor); si el valor cambia respecto del anterior la clave es {"pre": viejo, "post": nuevo} ----
# PAL (AnimationLoader.addBedrockKeyframes) guarda "pre" en t-0.001 s y "post" en t, asi que es un salto real; Blockbench lo muestra
# como una clave con dos puntos y interpolacion "step".
def build(vals, smooth=()):
    """smooth = tiempos de claves continuas (rampas): sin salto aunque el valor cambie."""
    out, prev = [], None
    for t, v in sorted(vals, key=lambda x: x[0]):
        v = [r(a) for a in v]
        out.append((t, None if (prev is None or v == prev or t in smooth) else prev, v))
        prev = v
    return out                      # [(t, pre | None, post)]
def to_json(keys): return {"%g" % t: (fmt(post) if pre is None else {"pre": fmt(pre), "post": fmt(post)}) for t, pre, post in keys}
def hit_vals(fn, extra=()):
    vals = [(h[0], fn(h)) for h in HITS] + [(t, v) for t, v in extra] + [(TOTAL, fn(HITS[-1]))]
    return vals

# ---- Canales de la animacion Bedrock del Mixin (animations/chad.animation.json): hueso -> canal -> [(t, pre, post)] ----
def pv(h): return P[h[1]]
bones = {}
def put(bone, ch, keys): bones.setdefault(bone, {})[ch] = keys
def rotz(v): return [v[0], v[1], -v[2]]   # Blockbench: Z de rotacion con signo contrario al de Java
put("right_arm", "rotation", build(hit_vals(lambda h: rotz([D(pv(h)[RX]), D(pv(h)[RY]), D(pv(h)[RZ])]))))
put("left_arm", "rotation", build(hit_vals(lambda h: rotz([D(pv(h)[LX]), D(pv(h)[LY]), D(pv(h)[LZ])]))))
put("head", "rotation", build(hit_vals(lambda h: [D(pv(h)[HX]), D(pv(h)[HY]), 0])))
put("body", "rotation", build(hit_vals(lambda h: [D(pv(h)[BX]), 0, 0])))
put("body", "scale", build(hit_vals(lambda h: [pv(h)[CHEST], 1, 1])))
put("right_leg", "rotation", build(hit_vals(lambda h: [0, 0, D(pv(h)[SPREAD])])))
put("left_leg", "rotation", build(hit_vals(lambda h: [0, 0, -D(pv(h)[SPREAD])])))
put("chad_root", "rotation", build(hit_vals(lambda h: [0, pv(h)[YAW], 0])))
put("chad_walk", "position", build(hit_vals(lambda h: [pv(h)[WALK], 0, 0])))
put("chad_camera", "position", build(hit_vals(lambda h: [h[6], 0, 0], [(t, [z, 0, 0]) for t, z in ZOOM_RAMP]), {t for t, _ in ZOOM_RAMP}))

anim = {"format_version": "1.8.0", "animations": {"chad": {"loop": False, "animation_length": TOTAL, "bones": {
    b: {ch: to_json(ks) for ch, ks in chs.items()} for b, chs in bones.items()}}}}
out_json = os.path.join(ROOT, "src/main/resources/assets/aurachad/animations/chad.animation.json")
os.makedirs(os.path.dirname(out_json), exist_ok=True)
json.dump(anim, open(out_json, "w"), indent=1)

# ---- Golpes para el cliente: assets/aurachad/chad_hits.json (t, pose, fuerza + la pose esperada en radianes, para el autotest) ----
hits_json = {"walk_start": WALK_START, "total": TOTAL, "hits": [
    {"t": h[0], "pose": h[1], "strength": h[7], "model": {
        "right_arm": [r(a) for a in pv(h)[RX:RZ + 1]], "left_arm": [r(a) for a in pv(h)[LX:LZ + 1]], "head": [r(pv(h)[HX]), r(pv(h)[HY])],
        "body": r(pv(h)[BX]), "chest": pv(h)[CHEST], "spread": r(pv(h)[SPREAD]), "yaw": pv(h)[YAW], "walk": pv(h)[WALK], "zoom": h[6]}}
    for h in HITS]}
json.dump(hits_json, open(os.path.join(ROOT, "src/main/resources/assets/aurachad/chad_hits.json"), "w"), indent=1)

# ---- Skin 64x64 sencilla (cada parte de un color) ----
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
def box(u, v, w, h, dp, col):
    d.rectangle([u, v + dp, u + 2*dp + 2*w - 1, v + dp + h - 1], fill=col)
    d.rectangle([u + dp, v, u + dp + 2*w - 1, v + dp - 1], fill=col)
box(0, 0, 8, 8, 8, (200, 160, 130, 255)); box(16, 16, 8, 12, 4, (60, 120, 200, 255))
box(40, 16, 4, 12, 4, (200, 160, 130, 255)); box(32, 48, 4, 12, 4, (200, 160, 130, 255))
box(0, 16, 4, 12, 4, (50, 50, 120, 255)); box(16, 48, 4, 12, 4, (50, 50, 120, 255))
buf = io.BytesIO(); img.save(buf, "PNG"); png_b64 = base64.b64encode(buf.getvalue()).decode()
img.save(os.path.join(ROOT, "ref/blockbench/skin_placeholder.png"))


# ---- bbmodel (rig vanilla, camino Mixin) ----
U = lambda: str(uuid.uuid4())
PARTS = [  # nombre, origen del cubo, tamano, uv, pivote
 ("head",      [-4, 24, -4],   [8, 8, 8],   [0, 0],   [0, 24, 0]),
 ("body",      [-4, 12, -2],   [8, 12, 4],  [16, 16], [0, 24, 0]),
 ("right_arm", [-8, 12, -2],   [4, 12, 4],  [40, 16], [-5, 22, 0]),
 ("left_arm",  [4, 12, -2],    [4, 12, 4],  [32, 48], [5, 22, 0]),
 ("right_leg", [-3.9, 0, -2],  [4, 12, 4],  [0, 16],  [-1.9, 12, 0]),
 ("left_leg",  [-0.1, 0, -2],  [4, 12, 4],  [16, 48], [1.9, 12, 0]),
]

def kf_list(chs, gname):
    kfs = []
    for ch, ks in chs.items():
        for t, pre, post in ks:
            dps = ([{"x": str(pre[0]), "y": str(pre[1]), "z": str(pre[2])}] if pre is not None else []) + [{"x": str(post[0]), "y": str(post[1]), "z": str(post[2])}]
            kfs.append({"channel": ch, "data_points": dps, "uuid": U(), "time": t, "color": -1, "interpolation": "step" if pre is not None else "linear"})
    return kfs
elements, outliner, gid = [], [], {}
for name, o, s_, uv, pv_ in PARTS:
    eu = U()
    elements.append({"name": name, "box_uv": True, "rescale": False, "locked": False, "type": "cube", "uuid": eu,
        "from": o, "to": [o[0]+s_[0], o[1]+s_[1], o[2]+s_[2]], "autouv": 0, "color": 0, "origin": pv_, "uv_offset": uv,
        "faces": {f: {"uv": [0, 0, 0, 0], "texture": 0} for f in ("north", "east", "south", "west", "up", "down")}})
    gu = U(); gid[name] = gu
    outliner.append({"name": name, "origin": pv_, "rotation": [0, 0, 0], "color": 0, "uuid": gu, "export": True, "isOpen": False,
                     "locked": False, "visibility": True, "autouv": 0, "children": [eu]})
for name, pvt in (("chad_root", [0, 0, 0]), ("chad_walk", [0, 0, 0]), ("chad_camera", [0, 0, 0])):
    gu = U(); gid[name] = gu
    outliner.append({"name": name, "origin": pvt, "rotation": [0, 0, 0], "color": 0, "uuid": gu, "export": True, "isOpen": False,
                     "locked": False, "visibility": True, "autouv": 0, "children": []})
animators = {gid[b]: {"name": b, "type": "bone", "keyframes": kf_list(chs, b)} for b, chs in bones.items()}
bb = {"meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": True},
      "name": "chad_poses", "model_identifier": "chad_poses", "visible_box": [1, 1, 0],
      "variable_placeholders": "", "bedrock_animation_mode": "entity",
      "resolution": {"width": 64, "height": 64}, "elements": elements, "outliner": outliner,
      "textures": [{"path": "", "name": "skin_placeholder.png", "folder": "", "namespace": "", "id": "0", "particle": False,
                    "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                    "frame_order": "", "frame_interpolate": False, "visible": True, "mode": "bitmap", "saved": True,
                    "uuid": U(), "source": "data:image/png;base64," + png_b64}],
      "animations": [{"uuid": U(), "name": "chad", "loop": "once", "override": False, "length": TOTAL, "snapping": 20,
                      "selected": True, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                      "animators": animators}]}
json.dump(bb, open(os.path.join(ROOT, "ref/blockbench/chad_poses.bbmodel"), "w"), indent=1)

# ---- Animacion para Player Animation Library (assets/aurachad/player_animations/chad.json) ----
# PAL lee huesos con nombres snake_case (head, torso, right_arm...). Signos/ejes calibrados contra el Mixin con el log del autotest.
PAL_SIGN = {  # hueso PAL -> (sx, sy, sz) aplicado a los valores del archivo Bedrock de arriba (que ya traen Z con signo de Blockbench)
    "right_arm": (1, 1, -1), "left_arm": (1, 1, -1), "head": (1, 1, 1), "torso": (1, 1, 1), "right_leg": (1, 1, -1), "left_leg": (1, 1, -1),
}
# ---- Codos y rodillas (bend) ----
# Objetivo = direccion deseada del antebrazo en espacio del personaje (afuera, arriba, frente); "afuera" se invierte en el brazo izquierdo.
# El eje de bend (grados, alrededor del brazo) se resuelve aqui para que el antebrazo se acerque al objetivo; ChadBend aplica
# applyBend(eje, codo) de bendy-lib y el autotest mide la direccion real del antebrazo contra estos mismos objetivos.
def _mm(A, B): return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
def _mv(A, v): return [sum(A[i][k] * v[k] for k in range(3)) for i in range(3)]
def _rot(x, y, z):  # JOML rotationZYX(z, y, x)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return _mm([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], _mm([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))
def _flocal(a, th): return [-math.sin(a) * math.sin(th), math.cos(th), math.cos(a) * math.sin(th)]
def _player(m): return [-m[0], -m[1], -m[2]]   # modelo (x izq, y abajo, z atras) -> personaje (derecha, arriba, frente)
def solve_axis(rot3, bend_deg, tgt, right):
    """Eje (grados 0..360) que acerca la direccion del antebrazo al objetivo (afuera, arriba, frente)."""
    if tgt is None: return None
    t = [tgt[0] if right else -tgt[0], tgt[1], tgt[2]]; n = math.sqrt(sum(c * c for c in t)); t = [c / n for c in t]
    R = _rot(*rot3); best = (9e9, 0)
    for k in range(720):
        f = _player(_mv(R, _flocal(math.radians(k * 0.5), math.radians(bend_deg))))
        e = -sum(a * b for a, b in zip(f, t))
        if e < best[0]: best = (e, k * 0.5)
    return best[1]

# pista de bend: t, post (bend, axis, target) y pre (valor anterior; igual al post si no hay salto)
BEND_TRACKS = {n: {"t": [], "bend": [], "axis": [], "bendPre": [], "axisPre": []} for n in ("right_arm", "left_arm", "right_leg", "left_leg")}
for n in ("right_arm", "left_arm"): BEND_TRACKS[n]["target"] = []
last_ax = {"right_arm": 0.0, "left_arm": 0.0}
for t, pose, br, bl, tr, tl, _z, _s in HITS:
    p = P[pose]
    for bone, bd, tg, rot3, right in (("right_arm", br, tr, (p[RX], p[RY], p[RZ]), True), ("left_arm", bl, tl, (p[LX], p[LY], p[LZ]), False)):
        ax = solve_axis(rot3, bd, tg, right)
        if ax is None: ax = last_ax[bone]
        last_ax[bone] = ax
        tk = BEND_TRACKS[bone]
        tk["bendPre"].append(tk["bend"][-1] if tk["bend"] else bd); tk["axisPre"].append(tk["axis"][-1] if tk["axis"] else round(ax, 1))
        tk["t"].append(t); tk["bend"].append(bd); tk["axis"].append(round(ax, 1)); tk["target"].append(list(tg) if tg else None)
def leg_add(bone, t, bend, jump=False):
    tk = BEND_TRACKS[bone]
    tk["bendPre"].append(tk["bend"][-1] if jump else bend); tk["axisPre"].append(0)
    tk["t"].append(t); tk["bend"].append(bend); tk["axis"].append(0)
# Rodillas: la pierna que avanza dobla hasta 30 grados; eje 0 = rodilla hacia atras. La caminata arranca de golpe en WALK_START.
for bone in ("right_leg", "left_leg"): leg_add(bone, 0, 0)
STEP = 0.05
walk_times = []
tt = WALK_START
while tt <= TOTAL + 1e-6:
    walk_times.append(round(tt, 3)); tt += STEP
def walk_swing(t): return math.sin((t - WALK_START) * 8) * 0.8     # x de la pierna derecha (la izquierda al reves)
def walk_knee(t, bone): c = math.cos((t - WALK_START) * 8); return round(30 * max(0.0, -c if bone == "right_leg" else c), 2)
for t in walk_times:
    for bone in ("right_leg", "left_leg"): leg_add(bone, t, walk_knee(t, bone), jump=(t == WALK_START))

# ---- Canales PAL ----
pal = {}
def pput(bone, ch, vals, sign=(1, 1, 1), smooth=()):
    pal.setdefault(bone, {})[ch] = build([(t, [v[i] * sign[i] for i in range(3)]) for t, v in vals], smooth)
def hv(fn, extra=()): return hit_vals(lambda h: fn(P[h[1]]), extra)
for bone, (rx, ry, rz) in (("right_arm", (RX, RY, RZ)), ("left_arm", (LX, LY, LZ))):
    pput(bone, "rotation", hv(lambda p: [D(p[rx]), D(p[ry]), -D(p[rz])]), PAL_SIGN[bone])
    pput(bone, "scale", hv(lambda p: [1 + (p[CHEST] - 1) * 0.9, 1, 1 + (p[CHEST] - 1) * 0.9]))   # los brazos engordan con la pose, como en el Mixin
pput("head", "rotation", hv(lambda p: [D(p[HX]), D(p[HY]), 0]), PAL_SIGN["head"])
pput("torso", "rotation", hv(lambda p: [D(p[BX]), 0, 0]), PAL_SIGN["torso"])
pput("torso", "scale", hv(lambda p: [p[CHEST], 1, 1 + (p[CHEST] - 1) * 0.6]))
# Piernas: separacion por golpe + la oscilacion de la caminata (muestras cada 0.05 s desde WALK_START, lineal entre ellas)
for bone, sg in (("right_leg", 1), ("left_leg", -1)):
    times = sorted(set(HIT_T + walk_times + [TOTAL]))
    vals = []
    for t in times:
        p = pose_at(t)
        sw = walk_swing(t) * p[WALK] if t >= WALK_START else 0.0
        vals.append((t, [D(sg * sw), 0, -sg * D(p[SPREAD]) * 1.0]))
    # signo: el original ponia right_leg z = -D(spread) y left_leg z = +D(spread), luego PAL_SIGN invierte; aqui sg*-1 reproduce eso
    pput(bone, "rotation", vals, PAL_SIGN[bone], smooth={t for t in walk_times + [TOTAL] if t != WALK_START})
for bone, tk in BEND_TRACKS.items():
    keys = [(t, pre if pre != post else None, [post, 0, 0]) for t, pre, post in zip(tk["t"], tk["bendPre"], tk["bend"])]
    keys = [(t, None if pre is None else [pre, 0, 0], post) for t, pre, post in keys]
    pal.setdefault(bone, {})["bend"] = [(t, pre, [r(a) for a in post]) for t, pre, post in keys]

out_bend = os.path.join(ROOT, "src/main/resources/assets/aurachad/chad_bend.json")
json.dump(BEND_TRACKS, open(out_bend, "w"), indent=1)
pal_json = {b: {ch: to_json(ks) for ch, ks in chs.items()} for b, chs in pal.items()}
pal_anim = {"format_version": "1.8.0", "animations": {"chad": {
    "loop": False, "animation_length": TOTAL,
    "player_animation_library": {"name": "Aura Chad", "description": "Poses del modo chad (aurachad)", "author": "Benjamin Greciet", "applyBendToOtherBones": True},
    "bones": pal_json}}, "geckolib_format_version": 2}
out_pal = os.path.join(ROOT, "src/main/resources/assets/aurachad/player_animations/chad.json")
os.makedirs(os.path.dirname(out_pal), exist_ok=True)
json.dump(pal_anim, open(out_pal, "w"), indent=1)

# ---- bbmodel con la rig de PAL (ref/blockbench/chad_pal.bbmodel) ----
PAL_PARTS = [  # hueso, padre, origen del cubo, tamano, uv, pivote (Blockbench: derecha = +X)
 ("body",      None,    None,           None,      None,     [0, 12, 0]),
 ("torso",     "body",  [-4, 12, -2],   [8, 12, 4], [16, 16], [0, 24, 0]),
 ("head",      "torso", [-4, 24, -4],   [8, 8, 8],  [0, 0],   [0, 24, 0]),
 ("right_arm", "torso", [4, 12, -2],    [4, 12, 4], [40, 16], [5, 22, 0]),
 ("left_arm",  "torso", [-8, 12, -2],   [4, 12, 4], [32, 48], [-5, 22, 0]),
 ("right_leg", "body",  [0, 0, -2],     [4, 12, 4], [0, 16],  [2, 12, 0]),
 ("left_leg",  "body",  [-4, 0, -2],    [4, 12, 4], [16, 48], [-2, 12, 0]),
]
els, nodes, guid = [], {}, {}
for name, parent, o, sz, uv, pv in PAL_PARTS:
    gu = U(); guid[name] = gu
    node = {"name": name, "origin": pv, "rotation": [0, 0, 0], "color": 0, "uuid": gu, "export": True, "isOpen": True,
            "locked": False, "visibility": True, "autouv": 0, "children": []}
    if o:
        eu = U()
        els.append({"name": name + "_cube", "box_uv": True, "rescale": False, "locked": False, "type": "cube", "uuid": eu,
            "from": o, "to": [o[0]+sz[0], o[1]+sz[1], o[2]+sz[2]], "autouv": 0, "color": 0, "origin": pv, "uv_offset": uv,
            "faces": {f: {"uv": [0, 0, 0, 0], "texture": 0} for f in ("north", "east", "south", "west", "up", "down")}})
        node["children"].append(eu)
    nodes[name] = node
roots = []
for name, parent, *_ in PAL_PARTS:
    (nodes[parent]["children"] if parent else roots).append(nodes[name])
pal_animators = {guid[b]: {"name": b, "type": "bone", "keyframes": kf_list(chs, b)} for b, chs in pal.items()}
bb2 = {"meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": True},
       "name": "chad_pal", "model_identifier": "chad_pal", "visible_box": [1, 1, 0], "variable_placeholders": "",
       "bedrock_animation_mode": "entity", "resolution": {"width": 64, "height": 64}, "elements": els, "outliner": roots,
       "textures": bb["textures"],
       "animations": [{"uuid": U(), "name": "chad", "loop": "once", "override": False, "length": TOTAL, "snapping": 20,
                       "selected": True, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                       "animators": pal_animators}]}
json.dump(bb2, open(os.path.join(ROOT, "ref/blockbench/chad_pal.bbmodel"), "w"), indent=1)
print("ok", out_json, out_pal)
