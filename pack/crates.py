"""Modèles 3D des caisses (corps, couvercle articulé, joyau) et leurs textures, pour le pack God Civilisation.

Chaque caisse = 3 objets affichés par le serveur (item_display) :
  godciv:crate_<type>_body  – le coffre (pieds, corps, cerclages, serrure)
  godciv:crate_<type>_lid   – le couvercle, dont la charnière est au centre du modèle (8, 8, 8) pour pouvoir pivoter
  godciv:crate_<type>_gem   – le joyau qui flotte au-dessus
Types : commune, rare, epique, divine.
"""
import json, random
from PIL import Image, ImageDraw

def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)

PALETTES = {
    "commune": {"wood": ("8a6136", "6b4826", "a57a46"), "trim": ("b9bfc7", "7f858e", "eef1f4"), "gem": ("f3f0fa", "b8b2d0", "ffffff"), "glow": "fff1c9", "style": "planks"},
    "rare":    {"wood": ("2a4f8f", "1b3463", "3f6fbf"), "trim": ("d3dde9", "8797ad", "ffffff"), "gem": ("4fd9ff", "1a86b0", "d4f7ff"), "glow": "a8ecff", "style": "planks"},
    "epique":  {"wood": ("3b2257", "26153a", "56337f"), "trim": ("c58cff", "7d47bd", "f0dcff"), "gem": ("ff5ce1", "a42491", "ffd6f6"), "glow": "f0a8ff", "style": "obsidian"},
    "divine":  {"wood": ("ebe6f3", "c4bdd6", "ffffff"), "trim": ("e6b84c", "8a6416", "fbe7a6"), "gem": ("ffd23f", "c08300", "fff4bf"), "glow": "fff0a0", "style": "marble"},
}

# ---------------------------------------------------------------- Textures 16 × 16
def tex_side(p, seed):
    base, dark, light = (hx(c) for c in p["wood"])
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), base)
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = rnd.random()
            c = base
            if p["style"] == "planks":
                if y % 4 == 3:
                    c = dark
                elif n < 0.12:
                    c = mix(base, dark, 0.45)
                elif n > 0.93:
                    c = mix(base, light, 0.5)
                if y % 4 != 3 and (x + (y // 4) * 5) % 16 == 0:
                    c = dark  # joints des planches
            elif p["style"] == "obsidian":
                c = dark if n < 0.35 else (mix(base, light, 0.6) if n > 0.95 else base)
            else:  # marbre
                c = base if n > 0.1 else mix(base, dark, 0.35)
            px[x, y] = c
    if p["style"] == "marble":
        for _ in range(3):
            x, y = rnd.randint(0, 15), rnd.randint(0, 15)
            for _ in range(14):
                px[x % 16, y % 16] = mix(base, dark, 0.7)
                x += rnd.choice([1, 1, 0]); y += rnd.choice([1, 0, -1])
    return img

def tex_trim(p):
    base, dark, light = (hx(c) for c in p["trim"])
    img = Image.new("RGBA", (16, 16), base)
    d = ImageDraw.Draw(img)
    d.line([0, 0, 15, 0], fill=light); d.line([0, 1, 15, 1], fill=mix(base, light, 0.5))
    d.line([0, 15, 15, 15], fill=dark); d.line([0, 14, 15, 14], fill=mix(base, dark, 0.5))
    for x in (2, 13):
        for y in (4, 11):
            img.putpixel((x, y), light); img.putpixel((x + 1, y + 1), dark)
    return img

def tex_lock(p):
    base, dark, light = (hx(c) for c in p["trim"])
    img = tex_trim(p)
    d = ImageDraw.Draw(img)
    d.rectangle([5, 4, 10, 11], fill=mix(base, dark, 0.3))
    d.rectangle([7, 6, 8, 7], fill=(20, 16, 28, 255))
    d.line([7, 8, 8, 10], fill=(20, 16, 28, 255)); d.point([8, 8], fill=(20, 16, 28, 255))
    d.line([5, 4, 10, 4], fill=light)
    return img

def tex_top(p, seed):
    img = tex_side(p, seed).rotate(90)
    base, dark, light = (hx(c) for c in p["trim"])
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=base)
    d.point([0, 0], fill=light); d.point([15, 15], fill=dark)
    return img

def tex_inner(p):
    """Intérieur : lueur douce, plus claire au centre, avec quelques étincelles."""
    glow = hx(p["glow"])
    img = Image.new("RGBA", (16, 16), glow)
    px = img.load()
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 10.6
            px[x, y] = mix(mix(glow, (255, 255, 255, 255), 0.35), mix(glow, (70, 50, 40, 255), 0.45), min(1, d))
    for _ in range(6):
        px[rnd.randint(2, 13), rnd.randint(2, 13)] = (255, 255, 255, 255)
    return img

def tex_gem(p):
    base, dark, light = (hx(c) for c in p["gem"])
    img = Image.new("RGBA", (16, 16), base)
    px = img.load()
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30
            c = mix(light, dark, t)
            if (x + y) % 5 == 0:
                c = mix(c, light, 0.35)
            px[x, y] = c
    for x, y in [(3, 3), (4, 3), (3, 4), (11, 5)]:
        px[x, y] = (255, 255, 255, 255)
    return img

# ---------------------------------------------------------------- Géométrie
FACES = ("north", "south", "east", "west", "up", "down")

def el(frm, to, tex, faces=None, rot=None):
    """Élément cubique ; uv proportionnels à la taille (16 au plus) ; tex = texture ou {face: texture}."""
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    def uv(face):
        w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]
        return [0, 0, round(min(16, max(0.5, w)), 2), round(min(16, max(0.5, h)), 2)]
    out = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {}}
    for f in (faces or FACES):
        t = tex.get(f) if isinstance(tex, dict) else tex
        if t:
            out["faces"][f] = {"uv": [0, 0, 16, 16] if t == "inner" else uv(f), "texture": "#" + t}
    if rot:
        out["rotation"] = rot
    return out

def body(kind):
    """Corps : aucune face n'est dans le même plan qu'une autre (sinon le jeu les fait « clignoter »)."""
    e = [
        el([1, 0, 1], [15, 1.5, 15], "trim"),                                     # socle
        el([2, 1.5, 2], [14, 9, 14], {"north": "side", "south": "side", "east": "side", "west": "side", "up": "inner"}),
        el([1.6, 5.5, 1.6], [14.4, 6.5, 14.4], "trim", faces=("north", "south", "east", "west")),  # cerclage
        # rebord du haut : un cadre (et non une plaque), plus haut que l'intérieur lumineux
        el([1.6, 8.2, 12.8], [14.4, 9.4, 14.4], "trim"),
        el([1.6, 8.2, 1.6], [14.4, 9.4, 3.2], "trim"),
        el([1.6, 8.2, 3.2], [3.2, 9.4, 12.8], "trim", faces=("east", "west", "up", "down")),
        el([12.8, 8.2, 3.2], [14.4, 9.4, 12.8], "trim", faces=("east", "west", "up", "down")),
        el([7, 3.2, 14], [9, 5.4, 14.7], "lock"),                                 # serrure (avant = sud du modèle)
    ]
    for x in (1.0, 13.0):                                                         # montants d'angle (dépassent du rebord)
        for z in (1.0, 13.0):
            e.append(el([x, 1.5, z], [x + 2, 9.8, z + 2], "trim"))
    if kind == "epique":                                                          # éclats d'améthyste
        for x, z in ((0.4, 0.4), (13.6, 13.6), (0.4, 13.6), (13.6, 0.4)):
            e.append(el([x, 9.8, z], [x + 2, 12, z + 2], "gem", rot={"angle": 22.5, "axis": "y", "origin": [x + 1, 9.8, z + 1]}))
    if kind == "divine":                                                          # pieds en griffe dorés
        for x in (0.4, 13.6):
            for z in (0.4, 13.6):
                e.append(el([x, -0.4, z], [x + 2, 2.2, z + 2], "trim"))
    return e

# Charnière du couvercle dans le corps (coordonnées du modèle) : au-dessus du rebord, à l'arrière.
HINGE = (8, 9.4, 2)

def lid(kind):
    """Charnière en (8, 8, 8) : le couvercle s'étend de z = 7.6 (arrière) à z = 20.4 (avant), posé sur le rebord."""
    e = [
        el([1.6, 8, 7.6], [14.4, 10.8, 20.4], {"north": "side", "south": "side", "east": "side", "west": "side", "up": "top", "down": "inner"}),
        # cadre doré sur le dessus (barres le long des bords, sans recouvrir le dessus)
        el([1.6, 10.8, 19], [14.4, 11.4, 20.4], "trim"),
        el([1.6, 10.8, 7.6], [14.4, 11.4, 9], "trim"),
        el([1.6, 10.8, 9], [3, 11.4, 19], "trim", faces=("east", "west", "up")),
        el([13, 10.8, 9], [14.4, 11.4, 19], "trim", faces=("east", "west", "up")),
        el([7, 7.2, 20.4], [9, 10.4, 21], "lock"),                                # moraillon
        el([5, 10.8, 12], [11, 11.6, 16], "trim", faces=("north", "south", "east", "west", "up")),  # écusson
    ]
    if kind in ("rare", "epique", "divine"):
        e.append(el([7, 11.6, 13], [9, 12.6, 15], "gem"))
    if kind == "divine":                                                          # couronne
        for x in (1.8, 12.8):
            for z in (7.8, 18.8):
                e.append(el([x, 11.4, z], [x + 1.4, 14, z + 1.4], "trim"))
        e.append(el([6.5, 11.4, 8.2], [9.5, 14.4, 9.2], "trim"))
    return e

def gem(kind):
    r45 = lambda o=(8, 8, 8): {"angle": 45, "axis": "y", "origin": list(o)}
    return [
        el([5.5, 6, 5.5], [10.5, 10, 10.5], "gem", rot=r45()),
        el([6.3, 10, 6.3], [9.7, 11.6, 9.7], "gem", rot=r45()),
        el([7.1, 11.6, 7.1], [8.9, 12.8, 8.9], "gem", rot=r45()),
        el([6.3, 4.4, 6.3], [9.7, 6, 9.7], "gem", rot=r45()),
        el([7.1, 3.2, 7.1], [8.9, 4.4, 8.9], "gem", rot=r45()),
    ]

def model(kind, part, elements):
    t = "godciv:item/crate/%s_" % kind
    return {"textures": {"particle": t + "side", "side": t + "side", "trim": t + "trim", "lock": t + "lock",
                         "top": t + "top", "inner": t + "inner", "gem": t + "gem"},
            "elements": elements}

def files(png):
    """Renvoie {chemin: octets} des modèles, définitions d'objets et textures des caisses."""
    out = {}
    for i, (kind, p) in enumerate(PALETTES.items()):
        tex = {"side": tex_side(p, 10 + i), "trim": tex_trim(p), "lock": tex_lock(p), "top": tex_top(p, 20 + i),
               "inner": tex_inner(p), "gem": tex_gem(p)}
        for name, img in tex.items():
            out["assets/godciv/textures/item/crate/%s_%s.png" % (kind, name)] = png(img)
        for part, els in (("body", body(kind)), ("lid", lid(kind)), ("gem", gem(kind))):
            name = "crate_%s_%s" % (kind, part)
            out["assets/godciv/models/item/%s.json" % name] = json.dumps(model(kind, part, els), indent=1).encode()
            out["assets/godciv/items/%s.json" % name] = json.dumps({"model": {"type": "minecraft:model", "model": "godciv:item/" + name}}).encode()
    return out

# ---------------------------------------------------------------- Aperçu (rendu isométrique simple, pour vérifier)
def preview(path, open_angle=-55):
    import math
    W, H, S = 1000, 300, 9
    img = Image.new("RGBA", (W, H), (21, 23, 42, 255))
    d = ImageDraw.Draw(img)
    for i, (kind, p) in enumerate(PALETTES.items()):
        tex = {"side": tex_side(p, 10 + i), "trim": tex_trim(p), "lock": tex_lock(p), "top": tex_top(p, 20 + i),
               "inner": tex_inner(p), "gem": tex_gem(p)}
        avg = {k: v.resize((1, 1), Image.BOX).getpixel((0, 0)) for k, v in tex.items()}
        quads = []
        def add(els, tf):
            for e in els:
                (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
                c = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
                rot = e.get("rotation")
                if rot:
                    a = math.radians(rot["angle"]); ox, oy, oz = rot["origin"]
                    c = [(ox + (x - ox) * math.cos(a) + (z - oz) * math.sin(a), y, oz - (x - ox) * math.sin(a) + (z - oz) * math.cos(a)) for x, y, z in c]
                c = [tf(v) for v in c]
                faces = {"up": (2, 3, 7, 6), "south": (1, 3, 7, 5), "east": (4, 5, 7, 6), "north": (0, 2, 6, 4), "west": (0, 1, 3, 2), "down": (0, 1, 5, 4)}
                shade = {"up": 1.0, "south": 0.8, "east": 0.65, "north": 0.5, "west": 0.55, "down": 0.4}
                for f, idx in faces.items():
                    if f not in e["faces"]:
                        continue
                    col = avg[e["faces"][f]["texture"][1:]]
                    pts = [c[k] for k in idx]
                    depth = sum(x + z + y for x, y, z in pts) / 4
                    quads.append((depth, pts, tuple(int(v * shade[f]) for v in col[:3])))
        add(body(kind), lambda v: v)
        a = math.radians(open_angle)
        def lid_tf(v):
            x, y, z = v[0], v[1] - 8, v[2] - 8
            y2, z2 = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
            return (x, y2 + HINGE[1], z2 + HINGE[2])
        add(lid(kind), lid_tf)
        add(gem(kind), lambda v: (v[0], v[1] + 14, v[2]))
        ox, oy = 130 + i * 240, 240
        def proj(v):
            x, y, z = v
            return (ox + (x - z) * S * 0.87, oy - y * S + (x + z) * S * 0.5 - 70)
        for _, pts, col in sorted(quads, key=lambda q: q[0]):
            d.polygon([proj(v) for v in pts], fill=col, outline=(0, 0, 0, 90))
        d.text((ox - 25, 270), kind.upper(), fill=(230, 230, 240, 255))
    img.save(path)
