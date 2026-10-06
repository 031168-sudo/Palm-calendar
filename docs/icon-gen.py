"""Иконка DateBook: рисунок КПК с ежедневником на экране.
Одна геометрия -> превью PNG (Pillow) и Android VectorDrawable (108x108, adaptive foreground)."""
import sys
from PIL import Image, ImageDraw

BG = "#1E3A6E"
NAVY = "#1E3A6E"
INK = "#1B1F2A"

# Фигуры в координатах 108x108
shapes = [
    # корпус: серебристый металл, светлая окантовка, лёгкая тень снизу
    ("rrect", (33.4, 25.0, 42, 60, 8.5), "#55000000"),
    ("rrect", (33, 24, 42, 60, 8.5), "#9EA4AE"),
    ("rrect", (33.6, 24.4, 40.8, 58.6, 8), "#D5D9DF"),
    ("rrect", (34.6, 25.6, 38.8, 56.4, 7.2), "#C4C9D0"),
    # нижняя панель кнопок чуть темнее
    ("rrect", (34.6, 66.5, 38.8, 15.5, 6.5), "#B4BAC3"),
    # большой экран в тёмной рамке
    ("rrect", (36.2, 27.4, 35.6, 38, 2.2), "#272C37"),
    ("rrect", (37.6, 28.8, 32.8, 35.2, 1.0), "#F8F7F2"),
    # вкладка заголовка с косым краем и линия под ней
    ("poly", [(37.6, 28.8), (53.2, 28.8), (56.6, 34.6), (37.6, 34.6)], NAVY),
    ("rect", (37.6, 34.6, 32.8, 0.9), NAVY),
    ("line", [(39.8, 31.7), (49.5, 31.7)], 1.4, "#FFFFFF"),
    # крупное число "12"
    ("line", [(45.0, 41.6), (47.5, 39.6), (47.5, 52.0)], 2.9, NAVY),
    ("line", [(51.2, 41.9), (52.0, 40.3), (53.9, 39.6), (55.8, 40.2), (56.7, 41.8), (56.4, 43.8),
              (54.9, 45.8), (51.2, 52.0), (57.3, 52.0)], 2.9, NAVY),
    # события: звонок и задача
    ("circle", (40.4, 56.6, 1.25), "#C0392B"),
    ("line", [(42.8, 56.6), (67.2, 56.6)], 1.1, "#9AA3B5"),
    ("circle", (40.4, 60.4, 1.25), "#2E7D32"),
    ("line", [(42.8, 60.4), (62.5, 60.4)], 1.1, "#9AA3B5"),
    # 5-позиционный джойстик по центру
    ("circle", (54, 74.2, 5.0), "#8E949E"),
    ("circle", (54, 74.2, 4.2), "#DDE1E6"),
    ("circle", (54, 74.2, 1.9), "#9EA4AE"),
    # четыре круглые кнопки приложений
    ("circle", (39.8, 74.2, 2.9), "#8E949E"),
    ("circle", (39.8, 74.2, 2.3), "#E2E5EA"),
    ("circle", (46.2, 76.0, 2.9), "#8E949E"),
    ("circle", (46.2, 76.0, 2.3), "#E2E5EA"),
    ("circle", (61.8, 76.0, 2.9), "#8E949E"),
    ("circle", (61.8, 76.0, 2.3), "#E2E5EA"),
    ("circle", (68.2, 74.2, 2.9), "#8E949E"),
    ("circle", (68.2, 74.2, 2.3), "#E2E5EA"),
]


# Уменьшаем рисунок вокруг центра, чтобы углы не срезались круглой маской лаунчера
K = 0.88
def _s(v): return 54 + (v - 54) * K
def _scale(sh):
    kind = sh[0]
    if kind in ("rrect", "rect"):
        x, y, w, h, *r = sh[1]
        return (kind, (_s(x), _s(y), w * K, h * K, *[q * K for q in r]), *sh[2:])
    if kind == "circle":
        cx, cy, r = sh[1]
        return (kind, (_s(cx), _s(cy), r * K), *sh[2:])
    if kind == "poly":
        return (kind, [(_s(x), _s(y)) for x, y in sh[1]], *sh[2:])
    if kind == "line":
        return (kind, [(_s(x), _s(y)) for x, y in sh[1]], sh[2] * K, sh[3])
shapes = [_scale(s) for s in shapes]


def hex_rgba(h):
    h = h.lstrip("#")
    if len(h) == 8:  # AARRGGBB
        a, r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
    else:
        r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4)); a = 255
    return (r, g, b, a)


def render(path, size=512, round_mask=False):
    S = 8
    k = size * S / 108
    img = Image.new("RGBA", (size * S, size * S), hex_rgba(BG))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for sh in shapes:
        d = ImageDraw.Draw(layer)
        kind = sh[0]
        if kind == "rrect":
            x, y, w, h, r = sh[1]
            d.rounded_rectangle([x * k, y * k, (x + w) * k, (y + h) * k], radius=r * k, fill=hex_rgba(sh[2]))
        elif kind == "rect":
            x, y, w, h = sh[1]
            d.rectangle([x * k, y * k, (x + w) * k, (y + h) * k], fill=hex_rgba(sh[2]))
        elif kind == "circle":
            cx, cy, r = sh[1]
            d.ellipse([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], fill=hex_rgba(sh[2]))
        elif kind == "poly":
            d.polygon([(x * k, y * k) for x, y in sh[1]], fill=hex_rgba(sh[2]))
        elif kind == "line":
            pts = [(x * k, y * k) for x, y in sh[1]]
            w = sh[2] * k
            d.line(pts, fill=hex_rgba(sh[3]), width=int(w), joint="curve")
            for px, py in (pts[0], pts[-1]):  # круглые концы
                d.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=hex_rgba(sh[3]))
        img = Image.alpha_composite(img, layer)
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    if round_mask:
        m = Image.new("L", img.size, 0)
        ImageDraw.Draw(m).ellipse([18 * k, 18 * k, 90 * k, 90 * k], fill=255)  # видимая зона лаунчера
        out = Image.new("RGBA", img.size, (0, 0, 0, 0)); out.paste(img, (0, 0), m)
        img = out.crop((int(18 * k), int(18 * k), int(90 * k), int(90 * k)))
    img.resize((size, size) if not round_mask else (size * 72 // 108,) * 2, Image.LANCZOS).save(path)


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def vector():
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- DateBook: рисунок КПК с ежедневником на экране. Сгенерировано из одной геометрии с превью. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    for sh in shapes:
        kind = sh[0]
        if kind == "rrect":
            x, y, w, h, r = sh[1]
            p = (f"M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0,0 1,{f(r)},{f(r)}v{f(h - 2 * r)}"
                 f"a{f(r)},{f(r)} 0,0 1,-{f(r)},{f(r)}h-{f(w - 2 * r)}a{f(r)},{f(r)} 0,0 1,-{f(r)},-{f(r)}"
                 f"v-{f(h - 2 * r)}a{f(r)},{f(r)} 0,0 1,{f(r)},-{f(r)}z")
            out.append(f'    <path android:fillColor="{sh[2]}" android:pathData="{p}" />')
        elif kind == "rect":
            x, y, w, h = sh[1]
            out.append(f'    <path android:fillColor="{sh[2]}" android:pathData="M{f(x)},{f(y)}h{f(w)}v{f(h)}h-{f(w)}z" />')
        elif kind == "circle":
            cx, cy, r = sh[1]
            p = f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0,1 0,{f(2 * r)},0a{f(r)},{f(r)} 0,1 0,-{f(2 * r)},0z"
            out.append(f'    <path android:fillColor="{sh[2]}" android:pathData="{p}" />')
        elif kind == "poly":
            p = "M" + "L".join(f"{f(x)},{f(y)}" for x, y in sh[1]) + "Z"
            out.append(f'    <path android:fillColor="{sh[2]}" android:pathData="{p}" />')
        elif kind == "line":
            p = "M" + "L".join(f"{f(x)},{f(y)}" for x, y in sh[1])
            out.append(f'    <path android:strokeColor="{sh[3]}" android:strokeWidth="{f(sh[2])}"'
                       f' android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{p}" />')
    out.append("</vector>")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    outdir = sys.argv[1]
    render(f"{outdir}/icon_full.png", 432)
    render(f"{outdir}/icon_round.png", 432, round_mask=True)
    open(f"{outdir}/ic_launcher_foreground.xml", "w").write(vector())
