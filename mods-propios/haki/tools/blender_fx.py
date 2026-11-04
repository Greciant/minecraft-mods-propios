"""
Genera las hojas de sprites animadas (flipbooks) del Haki con Blender + numpy.

Uso:
  blender -b --python tools/blender_fx.py -- src/main/resources/assets/haki/textures/fx

Blender renderiza la geometria (curvas con bisel y grosor variable, vista ortografica, Workbench plano) como
mascara blanca sobre negro; despues numpy compone: nucleo negro + borde rojo vivo + halo (varias capas) + chispas.
Todas las hojas son de 4x4 cuadros (16 cuadros) y las semillas son fijas: ejecutarlo otra vez da los mismos PNG.
Hojas: bolt (4:1, rayo con ramas), ring (onda de choque en el suelo), burst (explosion radial),
       crack (grietas que se abren desde el centro), smoke (humo oscuro), debris (4 piedras).
Sueltas: sigil (circulo runico, para el suelo y para el emblema sobre la cabeza), band (banda ornamental
         para los anillos orbitales) y beam (columna de luz).
"""
import math
import os
import random
import sys
import tempfile

import bpy
import numpy as np

OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "."
TMP = tempfile.mkdtemp(prefix="hakifx_")
RED = np.array([0.95, 0.06, 0.03])
HOT = np.array([1.0, 0.50, 0.32])


# ------------------------------------------------------------------ Blender: mascara de curvas
def setup_scene(w, h, xr, yr):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "BLENDER_WORKBENCH"
    sc.render.resolution_x, sc.render.resolution_y = w, h
    sc.render.resolution_percentage = 100
    sc.render.film_transparent = False
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGB"
    sc.view_settings.view_transform = "Standard"
    sc.display.render_aa = "16"
    sh = sc.display.shading
    sh.light = "FLAT"
    sh.color_type = "SINGLE"
    sh.single_color = (1, 1, 1)
    sh.show_object_outline = False
    sh.show_cavity = False
    world = bpy.data.worlds.new("w")
    world.color = (0, 0, 0)
    sc.world = world
    cam = bpy.data.cameras.new("cam")
    cam.type = "ORTHO"
    cam.ortho_scale = xr if w >= h else yr
    co = bpy.data.objects.new("cam", cam)
    sc.collection.objects.link(co)
    co.location = (0, 0, 10)
    sc.camera = co
    return sc


def render_mask(sc, polys, cx, cy, w, h):
    """polys: lista de (puntos [(x, y)], anchos [ancho total en cada punto]). Devuelve mascara float (h, w) en [0, 1]."""
    for o in [o for o in bpy.data.objects if o.type == "CURVE"]:
        bpy.data.objects.remove(o, do_unlink=True)
    for c in list(bpy.data.curves):
        bpy.data.curves.remove(c)
    sc.camera.location = (cx, cy, 10)
    for pts, widths in polys:
        if len(pts) < 2:
            continue
        cu = bpy.data.curves.new("c", "CURVE")
        cu.dimensions = "3D"
        cu.bevel_depth = 1.0
        cu.bevel_resolution = 1
        cu.use_fill_caps = True
        sp = cu.splines.new("POLY")
        sp.points.add(len(pts) - 1)
        for p, (x, y), wd in zip(sp.points, pts, widths):
            p.co = (x, y, 0, 1)
            p.radius = max(wd, 1e-4) * 0.5
        ob = bpy.data.objects.new("c", cu)
        sc.collection.objects.link(ob)
    path = os.path.join(TMP, "m.png")
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    img = bpy.data.images.load(path)
    img.colorspace_settings.name = "Non-Color"
    a = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1, :, 0].copy()
    bpy.data.images.remove(img)
    return np.clip(a, 0, 1)


# ------------------------------------------------------------------ numpy: composicion
def blur(a, s):
    if s <= 0.01:
        return a
    pad = int(s * 4) + 2
    p = np.pad(a, pad)
    fy = np.fft.fftfreq(p.shape[0])[:, None]
    fx = np.fft.fftfreq(p.shape[1])[None, :]
    g = np.exp(-2 * (np.pi ** 2) * (s ** 2) * (fx ** 2 + fy ** 2))
    r = np.real(np.fft.ifft2(np.fft.fft2(p) * g))
    return r[pad:-pad, pad:-pad]


def compose(m, scale=1.0, glow=1.0, sparks=None, rng=None, extra=None):
    """Mascara -> RGBA recto. scale: tamano relativo del halo (1 = para 64 px de alto)."""
    g1, g2, g3 = blur(m, 1.6 * scale), blur(m, 5.0 * scale), blur(m, 13.0 * scale)
    gl = 1 - np.exp(-glow * (2.4 * g1 + 1.3 * g2 + 0.7 * g3))
    thin = blur(m, 1.1 * scale)
    rim = np.clip((thin - m * 0.55) * 2.2, 0, 1) * (1 - m)
    a = np.maximum(gl, m)
    col = np.zeros(m.shape + (3,))
    base = RED[None, None, :] * np.ones_like(col)
    col = base * (1 - np.clip(rim * 2.5, 0, 1))[..., None] + HOT[None, None, :] * np.clip(rim * 2.5, 0, 1)[..., None]
    col = col * (1 - m)[..., None] + 0.012 * m[..., None]
    if sparks:
        spark = np.zeros_like(m)
        ys, xs = np.nonzero(m > 0.6)
        if len(ys):
            for _ in range(sparks):
                i = rng.randrange(len(ys))
                dy, dx = rng.randint(-5, 5), rng.randint(-5, 5)
                y, x = min(max(ys[i] + dy, 0), m.shape[0] - 1), min(max(xs[i] + dx, 0), m.shape[1] - 1)
                spark[y, x] = 1.0
        sp = np.clip(blur(spark, 0.7 * scale) * 6 + spark, 0, 1)
        col = col * (1 - sp)[..., None] + np.array([1, 0.92, 0.9])[None, None, :] * sp[..., None]
        a = np.maximum(a, sp)
    if extra is not None:                     # (alfa, color) de un destello aditivo
        ea, ec = extra
        col = col * (1 - ea)[..., None] + np.asarray(ec)[None, None, :] * ea[..., None]
        a = np.maximum(a, ea)
    return np.dstack([np.clip(col, 0, 1), np.clip(a, 0, 1)]).astype(np.float32)


def write_sheet(name, frames, cols=4):
    n = len(frames)
    rows = (n + cols - 1) // cols
    fh, fw = frames[0].shape[:2]
    sheet = np.zeros((rows * fh, cols * fw, 4), dtype=np.float32)
    for i, f in enumerate(frames):
        r, c = divmod(i, cols)
        sheet[r * fh:(r + 1) * fh, c * fw:(c + 1) * fw] = f
    img = bpy.data.images.new(name, cols * fw, rows * fh, alpha=True)
    img.colorspace_settings.name = "Non-Color"
    img.alpha_mode = "STRAIGHT"
    img.pixels = sheet[::-1].reshape(-1).tolist()
    img.filepath_raw = os.path.join(OUT, name + ".png")
    img.file_format = "PNG"
    img.save()
    print("escrito", img.filepath_raw)


# ------------------------------------------------------------------ geometria
def displace(rng, p0, p1, levels, amp, rough, wob=None):
    """Desplazamiento del punto medio entre p0 y p1 (2D). wob(k, n) -> desvio extra por cuadro."""
    n = 1 << levels
    px, py = p1[0] - p0[0], p1[1] - p0[1]
    ln = math.hypot(px, py)
    nx, ny = -py / ln, px / ln
    off = [0.0] * (n + 1)
    off[n] = rng.uniform(-1, 1) * amp
    a = amp
    step = n
    while step > 1:
        for i in range(0, n, step):
            m = i + step // 2
            off[m] = (off[i] + off[i + step]) * 0.5 + rng.uniform(-1, 1) * a
        a *= rough
        step //= 2
    pts = []
    for k in range(n + 1):
        t = k / n
        o = off[k] + (wob(k, n) if wob and 0 < k else 0)
        pts.append((p0[0] + px * t + nx * o, p0[1] + py * t + ny * o))
    return pts


def taper(n, w0, w1, power=1.2):
    return [w1 + (w0 - w1) * (1 - k / (n - 1)) ** power for k in range(n)]


def bolt_frame(seed, f, nf=16):
    """Un rayo horizontal (origen a la izquierda, punta a la derecha) en x:[0,4], y:[-0.5,0.5]."""
    ph = random.Random(seed + 77)
    rng = random.Random(seed)
    polys = []
    ph_k = [ph.uniform(0, 6.28) for _ in range(80)]
    sp_k = [ph.choice([1, 2]) for _ in range(80)]   # enteros: el ciclo de 16 cuadros cierra sin salto

    def wob(k, n):
        return 0.085 * math.sin(ph_k[k % 80] + sp_k[k % 80] * 6.2832 * f / nf)

    main = displace(rng, (0.06, 0.0), (3.7, rng.uniform(-0.07, 0.07)), 6, 0.17, 0.60, wob)
    mw = taper(len(main), 0.085, 0.012)
    polys.append((main, mw))
    nforks = rng.randint(3, 5)
    for i in range(nforks):
        k0 = rng.randint(6, len(main) - 12)
        x0, y0 = main[k0]
        dirs = rng.choice([-1, 1])
        ang = dirs * rng.uniform(0.35, 0.9)
        ln = rng.uniform(0.7, 1.7) * (1 - k0 / len(main) * 0.6)
        p1 = (x0 + math.cos(ang) * ln, y0 + math.sin(ang) * ln)
        if abs(p1[1]) > 0.46:
            p1 = (p1[0], math.copysign(0.46, p1[1]))
        fp = displace(rng, (x0, y0), p1, 4, ln * 0.14, 0.58, lambda k, n: 0.05 * math.sin(ph_k[(k + i * 7) % 80] + 6.2832 * f / nf))
        polys.append((fp, taper(len(fp), mw[k0] * 0.55, 0.006, 1.0)))
        if rng.random() < 0.6:                    # rama de la rama
            kk = rng.randint(3, len(fp) - 4)
            x1, y1 = fp[kk]
            a2 = ang + rng.choice([-1, 1]) * rng.uniform(0.4, 0.8)
            l2 = ln * 0.4
            q = displace(rng, (x1, y1), (x1 + math.cos(a2) * l2, max(-0.46, min(0.46, y1 + math.sin(a2) * l2))), 3, l2 * 0.15, 0.6)
            polys.append((q, taper(len(q), 0.012, 0.004, 1.0)))
    return polys


def make_bolts():
    w, h = 512, 128
    sc = setup_scene(w, h, 4.0, 1.0)
    frames = []
    for f in range(16):
        # 16 cuadros = 1 forma base con temblor (cierra el ciclo), 2 formas base distintas no caben: se alterna con espejo en el codigo
        polys = bolt_frame(1000, f)
        m = render_mask(sc, polys, 2.0, 0.0, w, h)
        frames.append(compose(m, 2.0, 1.0, sparks=14, rng=random.Random(f)))
    write_sheet("bolt_sheet", frames)


def make_rings():
    s = 256
    sc = setup_scene(s, s, 2.0, 2.0)
    frames = []
    rng0 = random.Random(55)
    jit = [rng0.uniform(-1, 1) for _ in range(200)]
    for f in range(16):
        t = f / 15
        e = 1 - (1 - t) ** 3
        r = 0.08 + 0.88 * e
        u = max(0.0, (t - 0.35) / 0.65)
        fade = (1 - u * u * (3 - 2 * u)) * min(1, f / 1.5)
        polys = []
        n = 72
        for ring, (rr, wd) in enumerate(((r, 0.07 * (1 - t) + 0.015), (r * 0.82, 0.03 * (1 - t) + 0.008))):
            pts = [(math.cos(2 * math.pi * k / n) * rr * (1 + 0.035 * jit[(k * 3 + ring * 50 + f // 2) % 200]),
                    math.sin(2 * math.pi * k / n) * rr * (1 + 0.035 * jit[(k * 3 + ring * 50 + f // 2) % 200])) for k in range(n + 1)]
            polys.append((pts, [wd * (0.7 + 0.5 * abs(jit[(k + ring * 17) % 200])) for k in range(n + 1)]))
        rng = random.Random(900 + f // 3)         # puas radiales que salen del anillo
        for i in range(26):
            a = 2 * math.pi * i / 26 + rng.uniform(-0.08, 0.08)
            l = rng.uniform(0.07, 0.2) * (1 - t * 0.5)
            polys.append(([(math.cos(a) * r, math.sin(a) * r), (math.cos(a) * (r + l), math.sin(a) * (r + l))], [0.05 * (1 - t) + 0.01, 0.004]))
        m = render_mask(sc, polys, 0, 0, s, s)
        fr = compose(m, 1.2, 1.0)
        fr[..., 3] *= fade
        frames.append(fr)
    write_sheet("ring_sheet", frames)


def make_bursts():
    s = 256
    sc = setup_scene(s, s, 2.0, 2.0)
    yy, xx = np.mgrid[0:s, 0:s]
    rr = np.hypot((xx + 0.5 - s / 2) / (s / 2), (yy + 0.5 - s / 2) / (s / 2))
    frames = []
    base = random.Random(321)
    rays = [(base.uniform(0, 6.28), base.uniform(0.5, 0.95), base.uniform(0.6, 1.0), base.random()) for _ in range(30)]
    for f in range(16):
        t = f / 15
        e = 1 - (1 - min(1, t * 2.2)) ** 3
        fade = 1 - max(0, (t - 0.35) / 0.65) ** 1.5
        polys = []
        for i, (a, ln, thick, rn) in enumerate(rays):
            l = ln * e * 0.97
            rng = random.Random(i * 31 + f // 2)
            pts = displace(rng, (math.cos(a) * 0.05, math.sin(a) * 0.05), (math.cos(a) * l, math.sin(a) * l), 4, l * 0.12, 0.6)
            polys.append((pts, taper(len(pts), 0.14 * thick * (1 - t * 0.6), 0.008, 1.1)))
        m = render_mask(sc, polys, 0, 0, s, s)
        flash = np.exp(-(rr / (0.18 + 0.5 * t)) ** 2) * max(0, 1 - t * 1.7)
        halo = np.exp(-(rr / 0.55) ** 2) * 0.55 * fade
        ea = np.clip(flash * 1.2 + halo * 0.0, 0, 1)
        fr = compose(m, 2.1, 1.4, extra=(ea, [1, 0.85 - 0.5 * t, 0.8 - 0.55 * t]))
        fr[..., 3] = np.clip(fr[..., 3] * np.where(rr < 1.0, 1, 0) * (fade * 0.9 + 0.1 * (ea > 0.1)), 0, 1)
        frames.append(fr)
    write_sheet("burst_sheet", frames)


def make_cracks():
    s = 384
    sc = setup_scene(s, s, 2.0, 2.0)
    frames = []
    base = random.Random(4444)
    cr = []
    for i in range(15):
        a = 2 * math.pi * i / 15 + base.uniform(-0.18, 0.18)
        cr.append((a, base.uniform(0.55, 0.97), base.randint(0, 10 ** 6)))
    for f in range(16):
        t = min(1.0, f / 12)
        e = 1 - (1 - t) ** 3
        polys = []
        for a, ln, sd in cr:
            rng = random.Random(sd)
            l = ln * e
            if l < 0.04:
                continue
            p1 = (math.cos(a) * l, math.sin(a) * l)
            pts = displace(rng, (0.0, 0.0), p1, 5, l * 0.11, 0.6)
            ws = taper(len(pts), 0.075, 0.006, 0.9)
            polys.append((pts, ws))
            for _ in range(rng.randint(1, 3)):          # ramas
                k0 = rng.randint(4, len(pts) - 6)
                bx, by = pts[k0]
                aa = a + rng.choice([-1, 1]) * rng.uniform(0.4, 0.9)
                ll = l * rng.uniform(0.2, 0.4) * (1 - k0 / len(pts) * 0.5)
                q = displace(rng, (bx, by), (bx + math.cos(aa) * ll, by + math.sin(aa) * ll), 3, ll * 0.14, 0.6)
                polys.append((q, taper(len(q), ws[k0] * 0.6, 0.004, 1.0)))
        m = render_mask(sc, polys, 0, 0, s, s)
        fr = compose(m, 2.0, 1.5)
        frames.append(fr)
    write_sheet("crack_sheet", frames)


def vnoise(rng_state, shape3, x, y, z):
    """Ruido de valor 3D con interpolacion suave; x, y, z son arrays del mismo tamano."""
    lat = rng_state
    x0, y0, z0 = np.floor(x).astype(int), np.floor(y).astype(int), np.floor(z).astype(int)
    fx, fy, fz = x - x0, y - y0, z - z0
    sx, sy, sz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    n = lat.shape[0]

    def L(i, j, k):
        return lat[i % n, j % n, k % n]
    c = 0
    for dx, wx in ((0, 1 - sx), (1, sx)):
        for dy, wy in ((0, 1 - sy), (1, sy)):
            for dz, wz in ((0, 1 - sz), (1, sz)):
                c = c + L(x0 + dx, y0 + dy, z0 + dz) * wx * wy * wz
    return c


def make_smoke():
    """Volutas verticales de humo: ruido estirado hacia arriba, fino y con el borde rojo."""
    s = 128
    rng = np.random.RandomState(11)
    lat = rng.rand(64, 64, 64)
    yy, xx = np.mgrid[0:s, 0:s]
    u, v = (xx + 0.5) / s * 2 - 1, (yy + 0.5) / s * 2 - 1
    frames = []
    for f in range(16):
        t = f / 16
        z = np.full_like(u, t * 1.4 + 3)
        wob = (vnoise(lat, None, (u * 0.5 + 1) * 1.5, (v * 0.5 + 1 - t) * 0.8, z) - 0.5) * 0.9
        fb, amp = 0, 0.6
        for o in range(3):
            fr = 2.6 * 2 ** o
            fb = fb + amp * vnoise(lat, None, (u * 0.5 + 1 + wob) * fr, (v * 0.5 + 1 - t * 1.2) * fr * 0.28, z + o * 5.3)
            amp *= 0.5
        fb = fb / 1.05
        rad = np.clip(1.0 - np.abs(u) * 1.15, 0, 1) * np.clip(1.0 - np.abs(v) ** 2, 0, 1)
        rad = rad * rad * (3 - 2 * rad)
        d = np.clip((fb * 1.3 - 0.47) / 0.3, 0, 1) * rad
        d = d * d * (3 - 2 * d)
        rim = np.clip(1 - np.abs(d - 0.2) / 0.18, 0, 1)
        col = np.zeros(u.shape + (3,))
        col[...] = 0.015
        rc = np.clip(rim * 1.5, 0, 1)[..., None]
        col = col * (1 - rc) + np.array([0.85, 0.07, 0.04]) * rc
        a_ = np.clip(d * 1.4, 0, 0.9)
        frames.append(np.dstack([col, a_]).astype(np.float32))
    write_sheet("smoke_sheet", frames)


def make_debris():
    s = 16
    rng = random.Random(8)
    frames = []
    yy, xx = np.mgrid[0:s, 0:s]
    for i in range(4):
        n = rng.randint(5, 7)
        pts = [(s / 2 + math.cos(2 * math.pi * k / n + rng.uniform(-0.3, 0.3)) * rng.uniform(4.3, 6.8),
                s / 2 + math.sin(2 * math.pi * k / n + rng.uniform(-0.3, 0.3)) * rng.uniform(4.3, 6.8)) for k in range(n)]
        inside = np.ones((s, s), dtype=bool)
        for k in range(n):
            (x1, y1), (x2, y2) = pts[k], pts[(k + 1) % n]
            cross = (x2 - x1) * (yy + 0.5 - y1) - (y2 - y1) * (xx + 0.5 - x1)
            inside &= cross >= -0.0
        m = inside.astype(np.float32) if inside.any() else np.zeros((s, s), np.float32)
        if m.sum() < 8:                       # poligono no convexo: circulo irregular
            m = ((xx + 0.5 - s / 2) ** 2 + (yy + 0.5 - s / 2) ** 2 < 5.5 ** 2).astype(np.float32)
        er = blur(m, 1.0) > 0.8
        rim = (m > 0.5) & ~er
        col = np.zeros((s, s, 3))
        col[...] = 0.025
        col[rim] = [0.9, 0.12, 0.05]
        a = np.where(m > 0.5, 1.0, 0.0)
        gl = np.clip(blur(m, 1.6) * 0.7, 0, 1)
        col[(m <= 0.5)] = RED
        a = np.maximum(a, gl * 0.6 * (m <= 0.5))
        frames.append(np.dstack([col, a]).astype(np.float32))
    write_sheet("debris", frames, cols=4)


def circle(r, n=160, w=0.02, cx=0.0, cy=0.0):
    pts = [(cx + math.cos(2 * math.pi * k / n) * r, cy + math.sin(2 * math.pi * k / n) * r) for k in range(n + 1)]
    return pts, [w] * (n + 1)


def make_sigil():
    """Circulo runico: anillos, glifos, octograma y una corona central. Un solo cuadro de 384 px."""
    s = 384
    sc = setup_scene(s, s, 2.0, 2.0)
    rng = random.Random(2026)
    polys = [circle(0.97, 200, 0.03), circle(0.90, 200, 0.012), circle(0.64, 160, 0.022), circle(0.60, 160, 0.01),
             circle(0.30, 100, 0.03)]
    for i in range(48):                                    # marcas entre los anillos
        a = 2 * math.pi * i / 48
        r0, r1 = 0.915, 0.955 if i % 2 else 0.935
        polys.append(([(math.cos(a) * r0, math.sin(a) * r0), (math.cos(a) * r1, math.sin(a) * r1)], [0.014, 0.014]))
    for i in range(24):                                    # glifos entre 0,67 y 0,87
        a0 = 2 * math.pi * i / 24
        nseg = rng.randint(3, 5)
        pts, last = [], None
        for _ in range(nseg):
            r = rng.uniform(0.69, 0.85)
            a = a0 + rng.uniform(-0.09, 0.09)
            pts.append((math.cos(a) * r, math.sin(a) * r))
        polys.append((pts, [0.016] * len(pts)))
    for k in range(8):                                     # octograma {8/3}
        a1, a2 = 2 * math.pi * k / 8, 2 * math.pi * ((k + 3) % 8) / 8
        polys.append(([(math.cos(a1) * 0.60, math.sin(a1) * 0.60), (math.cos(a2) * 0.60, math.sin(a2) * 0.60)], [0.02, 0.02]))
    for k in range(4):                                     # rombo y cruz centrales
        a1, a2 = math.pi / 2 * k, math.pi / 2 * (k + 1)
        polys.append(([(math.cos(a1) * 0.30, math.sin(a1) * 0.30), (math.cos(a2) * 0.30, math.sin(a2) * 0.30)], [0.03, 0.03]))
    polys.append(([(0, -0.22), (0, 0.22)], [0.03, 0.03]))
    polys.append(([(-0.22, 0), (0.22, 0)], [0.03, 0.03]))
    crown = [(-0.16, -0.08), (-0.16, 0.08), (-0.08, 0.0), (0.0, 0.12), (0.08, 0.0), (0.16, 0.08), (0.16, -0.08), (-0.16, -0.08)]
    polys.append((crown, [0.022] * len(crown)))
    m = render_mask(sc, polys, 0, 0, s, s)
    fr = compose(m, 1.4, 1.5)
    yy, xx = np.mgrid[0:s, 0:s]
    rr = np.hypot((xx + 0.5 - s / 2) / (s / 2), (yy + 0.5 - s / 2) / (s / 2))
    fr[..., 3] *= np.clip((1.0 - rr) / 0.04, 0, 1)
    write_sheet("sigil", [fr], cols=1)


def make_band():
    """Banda ornamental (u se repite alrededor del anillo, v = alto): lineas rojas, fondo oscuro y dientes."""
    w, h = 256, 32
    a = np.zeros((h, w), np.float32)
    col = np.zeros((h, w, 3), np.float32)
    col[...] = [0.03, 0.0, 0.0]
    al = np.full((h, w), 0.80, np.float32)
    for y in (2, 3, 28, 29):                                # lineas de borde
        col[y, :] = [1.0, 0.18, 0.1]
        al[y, :] = 1.0
    for y in (0, 1, 30, 31):                                # borde difuso
        col[y, :] = [0.9, 0.06, 0.03]
        al[y, :] = 0.35 if y in (1, 30) else 0.12
    for x in range(w):
        m = x % 32
        if 6 <= m <= 8 or 14 <= m <= 16 or 23 <= m <= 25:   # dientes verticales
            col[7:25, x] = [0.95, 0.15, 0.08]
            al[7:25, x] = 1.0
        if 10 <= m <= 12 or 19 <= m <= 21:                  # rombos
            d = abs(m - (11 if m < 16 else 20))
            col[15 - (2 - d):17 + (2 - d), x] = [1.0, 0.45, 0.3]
            al[15 - (2 - d):17 + (2 - d), x] = 1.0
    img = np.dstack([col, al]).astype(np.float32)
    write_sheet("band", [img], cols=1)


def make_beam():
    """Columna de luz: u = ancho (suave), v = alto (0 arriba, 1 abajo)."""
    w, h = 32, 64
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    g = np.exp(-(((u - 0.5) / 0.2) ** 2))[None, :] * np.ones((h, 1))
    vert = (np.clip((1 - v) * 0.0 + v, 0, 1) ** 0.6)[:, None] * np.clip(v * 14, 0, 1)[:, None]
    a = np.clip(g * vert, 0, 1)
    core = np.exp(-(((u - 0.5) / 0.07) ** 2))[None, :] * np.ones((h, 1))
    col = np.zeros((h, w, 3), np.float32)
    col[..., 0] = 1.0
    col[..., 1] = 0.08 + 0.8 * core
    col[..., 2] = 0.05 + 0.75 * core
    write_sheet("beam", [np.dstack([col, a]).astype(np.float32)], cols=1)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    make_bolts()
    make_rings()
    make_bursts()
    make_cracks()
    make_smoke()
    make_debris()
    make_sigil()
    make_band()
    make_beam()
