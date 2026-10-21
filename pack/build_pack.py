"""Construit le pack de ressources de God Civilisation (pack.zip), dessiné entièrement par ce script.

Contenu :
  - badges en image dans le texte (police) : rangs, paliers, canaux du chat, icônes (pièces, Essence, Foi, clés…) ;
  - fond des coffres et menus (marbre et or), boutons, barre d'objets, barres de boss.

Usage : python3 pack/build_pack.py [dossier_de_sortie]   (par défaut site/dist)
Les caractères utilisés par le mod sont listés dans GLYPHS ; ils doivent correspondre à util/Glyphs.java.
"""
import io, json, os, random, sys, zipfile
from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "site", "dist")

# ---------------------------------------------------------------- Couleurs
def hx(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,)

GOLD, GOLD_LIGHT, GOLD_DARK = hx("e6b84c"), hx("f6dc8e"), hx("8a6416")
INK, INK2 = hx("15172a"), hx("23264a")
MARBLE, MARBLE_SHADE, MARBLE_LIGHT = hx("dcd8e6"), hx("bdb6d0"), hx("f3f0fa")
SLOT, SLOT_DARK, SLOT_LIGHT = hx("57517a"), hx("2f2a45"), hx("f6f3fc")
CLEAR = (0, 0, 0, 0)

# ---------------------------------------------------------------- Petite police 5 px (pour les badges)
FONT = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "C": ["###", "#..", "#..", "#..", "###"],
    "D": ["##.", "#.#", "#.#", "#.#", "##."], "E": ["###", "#..", "##.", "#..", "###"],
    "F": ["###", "#..", "##.", "#..", "#.."], "G": ["###", "#..", "#.#", "#.#", "###"],
    "H": ["#.#", "#.#", "###", "#.#", "#.#"], "I": ["###", ".#.", ".#.", ".#.", "###"],
    "K": ["#.#", "#.#", "##.", "#.#", "#.#"], "L": ["#..", "#..", "#..", "#..", "###"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"], "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
    "O": ["###", "#.#", "#.#", "#.#", "###"], "P": ["##.", "#.#", "##.", "#..", "#.."],
    "R": ["##.", "#.#", "##.", "#.#", "#.#"], "S": ["###", "#..", "###", "..#", "###"],
    "T": ["###", ".#.", ".#.", ".#.", ".#."], "U": ["#.#", "#.#", "#.#", "#.#", "###"],
    "V": ["#.#", "#.#", "#.#", "#.#", ".#."], "-": ["..", "..", "##", "..", ".."],
    " ": ["..", "..", "..", "..", ".."],
}
ACCENT = {"É": ("E", [2]), "Ê": ("E", [1])}  # lettre de base, colonnes de l'accent (ligne au-dessus)

def text_width(t):
    w = 0
    for i, ch in enumerate(t):
        base = ACCENT.get(ch, (ch, None))[0]
        w += len(FONT[base][0]) + (1 if i < len(t) - 1 else 0)
    return w

def draw_text(img, x, y, t, color):
    px = img.load()
    for ch in t:
        base, acc = ACCENT.get(ch, (ch, None))
        rows = FONT[base]
        for r, row in enumerate(rows):
            for c, v in enumerate(row):
                if v == "#":
                    px[x + c, y + r] = color
        if acc:
            for c in acc:
                px[x + c, y - 1] = color
        x += len(rows[0]) + 1

def badge(label, base, text=None):
    """Pastille de 9 px de haut : bord de la couleur, fond assombri, texte clair."""
    fill = mix(base, (12, 10, 24), 0.55)
    border = base
    txt = text or mix(base, (255, 255, 255), 0.75)
    w = text_width(label) + 6
    img = Image.new("RGBA", (w, 9), CLEAR)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, 8], fill=border)
    d.rectangle([1, 1, w - 2, 7], fill=fill)
    for x, y in [(0, 0), (w - 1, 0), (0, 8), (w - 1, 8)]:
        img.putpixel((x, y), CLEAR)
    # reflet en haut
    for x in range(2, w - 2):
        img.putpixel((x, 1), mix(fill, (255, 255, 255), 0.12))
    draw_text(img, 3, 2, label, txt)
    return img

def square(letter, base):
    img = badge(letter, base)
    if img.width < 9:  # carré de 9 × 9
        sq = Image.new("RGBA", (9, 9), CLEAR)
        sq.paste(img, ((9 - img.width) // 2, 0))
        return sq
    return img

def pixel_icon(rows, palette):
    img = Image.new("RGBA", (len(rows[0]), len(rows)), CLEAR)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    return img

ICONS = {
    "coin": (["..###..", ".#yyy#.", "#yyYyy#", "#yYyyy#", "#yyyyy#", ".#yyy#.", "..###.."],
             {"#": GOLD_DARK, "y": GOLD, "Y": GOLD_LIGHT}),
    "essence": (["...#...", "..#p#..", ".#ppP#.", "#ppppP#", ".#ppp#.", "..#p#..", "...#..."],
                {"#": hx("5b3a8e"), "p": hx("a984e6"), "P": hx("e4d4ff")}),
    "faith": (["...b...", "..bB...", "..bBb..", ".bBWBb.", ".bBWBb.", "bBBWBBb", ".bBBBb."],
              {"b": hx("2a78c9"), "B": hx("5fc4ff"), "W": hx("e6f7ff")}),
    "key": (["..###..", ".#y.y#.", ".#yyy#.", "..#y#..", "...y...", "...yy..", "...y...", "...yy.."],
            {"#": GOLD_DARK, "y": GOLD}),
    "mail": (["#########", "#w#www#w#", "#ww#w#ww#", "#www#www#", "#wwwwwww#", "#########"],
             {"#": hx("b04a9b"), "w": hx("ffd6f2")}),
    "star": (["....#....", "...#y#...", "#########", ".#yyYyy#.", "..#yyy#..", ".#yy#yy#.", "#y#...#y#"],
             {"#": GOLD_DARK, "y": GOLD, "Y": GOLD_LIGHT}),
}

TIER_COLORS = {
    "MORTEL": hx("aaaaaa"), "ÉVEILLÉ": hx("55ff55"), "DEMI-DIEU": hx("55ffff"), "DIEU": hx("ffaa00"),
    "SUPRÊME": hx("ff55ff"), "TITAN": hx("e04848"), "PRIMORDIAL": hx("22c4c4"), "ASTRAL": hx("7a7aff"),
    "ÉTERNEL": hx("ffff55"), "CRÉATEUR": hx("ffffff"),
}

# Caractère → image. Doit rester synchronisé avec util/Glyphs.java.
GLYPHS = {}
GLYPHS[""] = badge("ADMIN", hx("e04848"))
GLYPHS[""] = badge("MODO", hx("39b7e6"))
GLYPHS[""] = badge("AFK", hx("8a8899"))
for i, (name, col) in enumerate(TIER_COLORS.items()):
    GLYPHS[chr(0xE010 + i)] = badge(name, col)
GLYPHS[""] = square("G", hx("55ffff"))
GLYPHS[""] = square("L", hx("ffff55"))
GLYPHS[""] = square("C", hx("ffaa00"))
GLYPHS[""] = square("P", hx("55ff55"))
GLYPHS[""] = pixel_icon(*ICONS["mail"])
GLYPHS[""] = pixel_icon(*ICONS["star"])
GLYPHS[""] = pixel_icon(*ICONS["coin"])
GLYPHS[""] = pixel_icon(*ICONS["essence"])
GLYPHS[""] = pixel_icon(*ICONS["faith"])
GLYPHS[""] = pixel_icon(*ICONS["key"])
GLYPHS[""] = badge("PANTHÉON", GOLD)

# ---------------------------------------------------------------- Fond des coffres (generic_54.png)
def marble(img, box, seed):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            n = rnd.random()
            c = MARBLE if n > 0.12 else (MARBLE_SHADE if n < 0.04 else mix(MARBLE, MARBLE_SHADE, 0.4))
            px[x, y] = c
    # veines
    for _ in range(9):
        x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
        for _ in range(rnd.randint(12, 30)):
            if x0 <= x <= x1 and y0 <= y <= y1:
                px[x, y] = mix(MARBLE_SHADE, hx("9b93b3"), 0.5)
            x += rnd.choice([1, 1, 0, -1]); y += rnd.choice([1, 0, 0, -1])

def slot(img, x, y):
    """Case de 18 × 18 dont le coin haut-gauche est (x, y) : creusée, bord d'ombre et de lumière."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + 17, y + 17], fill=SLOT)
    d.line([x, y, x + 16, y], fill=SLOT_DARK); d.line([x, y, x, y + 16], fill=SLOT_DARK)
    d.line([x + 1, y + 17, x + 17, y + 17], fill=SLOT_LIGHT); d.line([x + 17, y + 1, x + 17, y + 17], fill=SLOT_LIGHT)

def frame(img, x0, y0, x1, y1, top=True, bottom=True):
    d = ImageDraw.Draw(img)
    # bord : contour encre, double filet d'or
    d.line([x0, y0 if top else y0, x0, y1], fill=INK); d.line([x1, y0, x1, y1], fill=INK)
    d.line([x0 + 1, y0, x0 + 1, y1], fill=GOLD); d.line([x1 - 1, y0, x1 - 1, y1], fill=GOLD)
    d.line([x0 + 2, y0, x0 + 2, y1], fill=GOLD_DARK); d.line([x1 - 2, y0, x1 - 2, y1], fill=GOLD_DARK)
    if top:
        d.line([x0, y0, x1, y0], fill=INK); d.line([x0 + 1, y0 + 1, x1 - 1, y0 + 1], fill=GOLD)
        d.line([x0 + 2, y0 + 2, x1 - 2, y0 + 2], fill=GOLD_DARK)
        for p in [(x0, y0), (x1, y0), (x0 + 1, y0), (x0, y0 + 1), (x1 - 1, y0), (x1, y0 + 1)]:
            img.putpixel(p, CLEAR)
    if bottom:
        d.line([x0, y1, x1, y1], fill=INK); d.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=GOLD)
        d.line([x0 + 2, y1 - 2, x1 - 2, y1 - 2], fill=GOLD_DARK)
        for p in [(x0, y1), (x1, y1), (x0 + 1, y1), (x0, y1 - 1), (x1 - 1, y1), (x1, y1 - 1)]:
            img.putpixel(p, CLEAR)

def generic_54():
    img = Image.new("RGBA", (256, 256), CLEAR)
    # Partie haute (titre + 6 rangées) : lignes 0 à 124. Partie basse (inventaire) : lignes 126 à 221.
    marble(img, (1, 1, 174, 124), 1)
    marble(img, (1, 126, 174, 220), 2)
    frame(img, 0, 0, 175, 124, top=True, bottom=False)
    frame(img, 0, 126, 175, 221, top=False, bottom=True)
    d = ImageDraw.Draw(img)
    # ornement du bandeau de titre (à droite, loin du texte)
    star = pixel_icon(*ICONS["star"])
    img.paste(star, (160, 5), star)
    for x in range(8, 168, 2):
        img.putpixel((x, 15), mix(GOLD, MARBLE, 0.45))
    # cases du coffre (6 × 9)
    for r in range(6):
        for c in range(9):
            slot(img, 7 + c * 18, 17 + r * 18)
    # séparation dorée entre le coffre et l'inventaire
    d.line([4, 126, 171, 126], fill=GOLD_DARK)
    d.line([4, 127, 171, 127], fill=mix(GOLD, MARBLE, 0.3))
    # inventaire (3 × 9) puis barre rapide
    for r in range(3):
        for c in range(9):
            slot(img, 7 + c * 18, 139 + r * 18)
    for c in range(9):
        slot(img, 7 + c * 18, 197)
    return img

# ---------------------------------------------------------------- Boutons, barre rapide, barres de boss
def button(fill_top, fill_bot, border, inner):
    img = Image.new("RGBA", (200, 20), CLEAR)
    d = ImageDraw.Draw(img)
    for y in range(20):
        d.line([0, y, 199, y], fill=mix(fill_top, fill_bot, y / 19))
    d.rectangle([0, 0, 199, 19], outline=border)
    d.line([1, 1, 198, 1], fill=inner)
    d.line([1, 18, 198, 18], fill=mix(fill_bot, (0, 0, 0), 0.3))
    for p in [(0, 0), (199, 0), (0, 19), (199, 19)]:
        img.putpixel(p, CLEAR)
    return img

def hotbar():
    img = Image.new("RGBA", (182, 22), CLEAR)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 181, 21], fill=hx("15172a", 215), outline=GOLD_DARK)
    d.rectangle([1, 1, 180, 20], outline=hx("2f3360"))
    for i in range(9):
        x = 1 + i * 20
        d.rectangle([x + 2, 3, x + 18, 19], fill=hx("23264a", 200))
        if i:
            d.line([x, 3, x, 18], fill=hx("3a3e70"))
    return img

def hotbar_selection():
    img = Image.new("RGBA", (24, 23), CLEAR)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 23, 22], outline=GOLD_DARK)
    d.rectangle([1, 1, 22, 21], outline=GOLD)
    d.rectangle([2, 2, 21, 20], outline=GOLD_LIGHT)
    for p in [(0, 0), (23, 0), (0, 22), (23, 22)]:
        img.putpixel(p, CLEAR)
    return img

BOSS = {"pink": "ff6ad5", "blue": "4f8cff", "red": "e04848", "green": "4fd06b", "yellow": "ffd23f", "purple": "a984e6", "white": "e8e6f0"}

def boss_bg():
    img = Image.new("RGBA", (182, 5), hx("15172a"))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 181, 4], outline=GOLD_DARK)
    for x in (0, 181):
        for y in range(5):
            img.putpixel((x, y), GOLD)
    return img

def boss_progress(col):
    c = hx(col)
    img = Image.new("RGBA", (182, 5), CLEAR)
    d = ImageDraw.Draw(img)
    d.line([1, 1, 180, 1], fill=mix(c, (255, 255, 255), 0.45))
    d.line([1, 2, 180, 2], fill=c)
    d.line([1, 3, 180, 3], fill=mix(c, (0, 0, 0), 0.3))
    for y in range(5):
        img.putpixel((0, y), GOLD); img.putpixel((181, y), GOLD)
    return img

def pack_icon():
    img = Image.new("RGBA", (64, 64), INK)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 61, 61], outline=GOLD)
    star = pixel_icon(*ICONS["star"]).resize((45, 35), Image.NEAREST)
    img.paste(star, (9, 14), star)
    return img

# ---------------------------------------------------------------- Écriture du zip (déterministe)
def png(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()

def build():
    files = {}
    files["pack.mcmeta"] = json.dumps({"pack": {"pack_format": 75, "min_format": 75, "max_format": 99,
        "description": "§6God Civilisation §7· badges, menus et interface"}}, ensure_ascii=False, indent=1).encode()
    files["pack.png"] = png(pack_icon())
    providers = []
    for ch, img in sorted(GLYPHS.items()):
        name = "g%04x" % ord(ch)
        files["assets/godciv/textures/font/%s.png" % name] = png(img)
        providers.append({"type": "bitmap", "file": "godciv:font/%s.png" % name, "ascent": 8, "height": img.height, "chars": [ch]})
    files["assets/minecraft/font/default.json"] = json.dumps({"providers": providers}, ensure_ascii=True, indent=1).encode()
    files["assets/minecraft/textures/gui/container/generic_54.png"] = png(generic_54())
    sprites = "assets/minecraft/textures/gui/sprites/"
    nine = lambda: json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": 3}}}).encode()
    files[sprites + "widget/button.png"] = png(button(hx("2b2f5c"), hx("1a1c38"), GOLD_DARK, hx("3d4280")))
    files[sprites + "widget/button_highlighted.png"] = png(button(hx("3a3f7a"), hx("23264a"), GOLD_LIGHT, hx("6a5aa8")))
    files[sprites + "widget/button_disabled.png"] = png(button(hx("26262e"), hx("1c1c22"), hx("44444f"), hx("303038")))
    for n in ("button", "button_highlighted", "button_disabled"):
        files[sprites + "widget/%s.png.mcmeta" % n] = nine()
    files[sprites + "hud/hotbar.png"] = png(hotbar())
    files[sprites + "hud/hotbar_selection.png"] = png(hotbar_selection())
    for name, col in BOSS.items():
        files[sprites + "boss_bar/%s_background.png" % name] = png(boss_bg())
        files[sprites + "boss_bar/%s_progress.png" % name] = png(boss_progress(col))

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "pack.zip")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, files[name])
    # Aperçu des badges (pour le README / le site)
    prev = Image.new("RGBA", (220, 12 * ((len(GLYPHS) + 1) // 2) + 4), hx("1c1f36"))
    for i, (ch, img) in enumerate(sorted(GLYPHS.items())):
        prev.paste(img, (4 + (i % 2) * 110, 4 + (i // 2) * 12), img)
    prev.resize((prev.width * 4, prev.height * 4), Image.NEAREST).save(os.path.join(OUT, "pack-badges.png"))
    generic_54().crop((0, 0, 176, 222)).resize((176 * 3, 222 * 3), Image.NEAREST).save(os.path.join(OUT, "pack-menu.png"))
    import hashlib
    print("pack.zip :", os.path.getsize(path), "octets, sha1", hashlib.sha1(open(path, "rb").read()).hexdigest(), ",", len(GLYPHS), "badges")

if __name__ == "__main__":
    build()
