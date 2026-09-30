import glob, os, sys
from PIL import Image, ImageDraw

ORIGEN = "icono.png"
if not os.path.exists(ORIGEN):
    print("No hay icono.png: se dejan los íconos actuales.")
    sys.exit(0)

img = Image.open(ORIGEN).convert("RGBA")
if img.getbbox():
    img = img.crop(img.getbbox())
lado = max(img.size)
cuadro = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
cuadro.paste(img, ((lado - img.width) // 2, (lado - img.height) // 2), img)
img = cuadro

transparente = img.getextrema()[3][0] < 250
esquina = img.getpixel((2, 2))
fondo = (255, 255, 255, 255) if transparente or esquina[3] < 250 else (esquina[0], esquina[1], esquina[2], 255)

def escalar(tam):
    return img.resize((tam, tam), Image.LANCZOS)

def componer(total, fraccion, color, redondo=False):
    base = Image.new("RGBA", (total, total), (0, 0, 0, 0))
    if color is not None:
        capa = Image.new("RGBA", (total, total), color)
        if redondo:
            m = Image.new("L", (total * 4, total * 4), 0)
            ImageDraw.Draw(m).ellipse((0, 0, total * 4 - 1, total * 4 - 1), fill=255)
            base.paste(capa, (0, 0), m.resize((total, total), Image.LANCZOS))
        else:
            base = capa
    t = max(1, int(total * fraccion))
    e = escalar(t)
    if redondo and not transparente:
        m = Image.new("L", (t * 4, t * 4), 0)
        ImageDraw.Draw(m).ellipse((0, 0, t * 4 - 1, t * 4 - 1), fill=255)
        e.putalpha(Image.eval(m.resize((t, t), Image.LANCZOS), lambda v: v))
    base.alpha_composite(e, ((total - t) // 2, (total - t) // 2))
    return base

fr = 0.86 if transparente else 1.0
componer(192, fr, fondo).convert("RGB").save("www/icon-192.png", optimize=True)
componer(512, fr, fondo).convert("RGB").save("www/icon-512.png", optimize=True)

res = "android/app/src/main/res"
tam = {"ldpi": 36, "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dens, t in tam.items():
    d = f"{res}/mipmap-{dens}"
    os.makedirs(d, exist_ok=True)
    componer(t, fr, fondo).save(f"{d}/ic_launcher.png", optimize=True)
    componer(t, 0.80 if transparente else 1.0, fondo, redondo=True).save(f"{d}/ic_launcher_round.png", optimize=True)
    fg = t * 108 // 48
    componer(fg, 0.62 if transparente else 0.70, None).save(f"{d}/ic_launcher_foreground.png", optimize=True)

with open(f"{res}/values/ic_launcher_background.xml", "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            f'    <color name="ic_launcher_background">#{fondo[0]:02X}{fondo[1]:02X}{fondo[2]:02X}</color>\n'
            '</resources>\n')

for ruta in glob.glob(f"{res}/drawable*/splash.png"):
    w, h = Image.open(ruta).size
    s = Image.new("RGBA", (w, h), (15, 32, 39, 255))
    t = int(min(w, h) * 0.45)
    s.alpha_composite(componer(t, 1.0, None), ((w - t) // 2, (h - t) // 2))
    s.convert("RGB").save(ruta, optimize=True)

print("Iconos generados desde icono.png")
