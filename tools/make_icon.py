"""Erzeugt die App-Icons fuer LoloTrans.

Quelle: app/src/main/res/drawable/lolo_soft_logo.png (Kreis-Logo mit 文A).
Ergebnis:
  - mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png        helles Theme: schwarzes Zeichen
  - mipmap-night-{mdpi..xxxhdpi}/ic_launcher_foreground.png  dunkles Theme: weisses Zeichen

Feste Vorgaben (Nutzerfreigabe 2026-11-12):
  - Zeichen freigestellt (Kreis, Glanz, Schatten des Originals entfernt)
  - 60 dp gross auf der 108-dp-Leinwand, quadratisch zentriert
  - 2 px (bei 180 px Icon-Darstellung) nach links und oben versetzt
  - weicher Schatten nach rechts/unten

Hintergrundfarben stehen in values/colors.xml (#FFFFFF) und values-night/colors.xml (#000000).

Aufruf:  python tools/make_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
SRC = RES / "drawable" / "lolo_soft_logo.png"

MOTIF_DP = 60          # Motivgroesse auf der 108dp-Leinwand
SHIFT_PX_AT_180 = 2    # Verschiebung nach links/oben, bezogen auf 180px Darstellung
SHADOW_X, SHADOW_Y = 0.035, 0.045
SHADOW_BLUR = 0.020

THEMES = [
    ("mipmap-", (0, 0, 0), (140, 140, 140)),        # helles Theme
    ("mipmap-night-", (255, 255, 255), (74, 74, 74)),  # dunkles Theme
]

DENSITIES = [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]


def load_glyph() -> Image.Image:
    img = Image.open(SRC).convert("RGB")
    bbox = ImageOps.grayscale(img).point(lambda v: 255 if v < 240 else 0).getbbox()
    x0, y0, x1, y1 = bbox
    side = max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    half = side / 2

    w = 900
    crop = img.crop((int(cx - half), int(cy - half), int(cx + half), int(cy + half)))
    crop = crop.resize((w, w), Image.LANCZOS).convert("RGBA")

    alpha = ImageOps.grayscale(crop).point(lambda v: max(0, min(255, int((v - 215) * 255 / 40))))
    # Glanzbogen des Original-Kreises ausschliessen
    inner = Image.new("L", (w, w), 0)
    r = int(w * 0.40)
    ImageDraw.Draw(inner).ellipse((w // 2 - r, w // 2 - r, w // 2 + r, w // 2 + r), fill=255)
    alpha = Image.composite(alpha, Image.new("L", (w, w), 0), inner)
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.5))

    glyph = Image.new("RGBA", (w, w), (255, 255, 255, 0))
    glyph.putalpha(alpha)
    return glyph.crop(glyph.getbbox())


def render(canvas_px: int, glyph: Image.Image, fg, shadow_color) -> Image.Image:
    motif = int(round(canvas_px * MOTIF_DP / 108))
    f = motif / max(glyph.size)
    g = glyph.resize((max(1, int(round(glyph.width * f))), max(1, int(round(glyph.height * f)))),
                     Image.LANCZOS)

    square = Image.new("RGBA", (motif, motif), (255, 255, 255, 0))
    square.alpha_composite(g, ((motif - g.width) // 2, (motif - g.height) // 2))

    tint = Image.new("RGBA", square.size, (*fg, 255))
    tint.putalpha(square.split()[3])
    shadow = Image.new("RGBA", square.size, (*shadow_color, 255))
    shadow.putalpha(square.split()[3])

    dx = int(round(motif * SHADOW_X))
    dy = int(round(motif * SHADOW_Y))
    shift = int(round(canvas_px * SHIFT_PX_AT_180 / 180.0))

    off_x = (canvas_px - motif) // 2 - shift
    off_y = (canvas_px - motif) // 2 - shift

    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    s_layer = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    s_layer.alpha_composite(shadow, (off_x + dx, off_y + dy))
    canvas.alpha_composite(s_layer.filter(ImageFilter.GaussianBlur(max(1.0, motif * SHADOW_BLUR))))
    canvas.alpha_composite(tint, (off_x, off_y))
    return canvas


def main() -> None:
    glyph = load_glyph()
    for prefix, fg, shadow in THEMES:
        for dpi, px in DENSITIES:
            out_dir = RES / f"{prefix}{dpi}"
            out_dir.mkdir(parents=True, exist_ok=True)
            render(px, glyph, fg, shadow).save(out_dir / "ic_launcher_foreground.png")
        print(f"{prefix}: {len(DENSITIES)} Groessen geschrieben")


if __name__ == "__main__":
    main()
