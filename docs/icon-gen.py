"""Иконка DateBook: рисунок КПК с ежедневником на экране.
Одна геометрия -> превью PNG (Pillow) и Android VectorDrawable (108x108, adaptive foreground)."""
import sys
from PIL import Image, ImageDraw

BG = "#E6ECF5"
NAVY = "#1E3A6E"
INK = "#1B1F2A"

# Фигуры в координатах 108x108
shapes = [
    # стилус, торчит из-за корпуса справа сверху
    ("line", [(69.5, 19.5), (69.5, 30)], 2.2, "#AEB6C6"),
    ("line", [(69.5, 19.5), (69.5, 21)], 2.2, "#7E879A"),
    # тень корпуса
    ("rrect", (36.5, 29, 37, 55, 7.5), "#33" + INK[1:]),
    # корпус
    ("rrect", (35.5, 27, 37, 55, 7.5), "#4A5366"),
    ("rrect", (36.6, 28.1, 34.8, 52.8, 6.5), "#556076"),
    # рамка экрана и экран
    ("rrect", (39, 31, 30, 34.5, 2.6), "#262C3A"),
    ("rrect", (40.4, 32.4, 27.2, 31.7, 1.4), "#F8F7F2"),
    # вкладка с датой (косой край) и линия под ней
    ("poly", [(40.4, 32.4), (53.5, 32.4), (56.6, 37.4), (40.4, 37.4)], NAVY),
    ("rect", (40.4, 37.4, 27.2, 0.8), NAVY),
    ("line", [(42.4, 34.9), (50.3, 34.9)], 1.3, "#FFFFFF"),
    # большое число "12"
    ("line", [(46.2, 43.4), (48.3, 41.7), (48.3, 52.3)], 2.5, NAVY),
    ("line", [(51.4, 43.7), (52.1, 42.3), (53.7, 41.7), (55.3, 42.2), (56.1, 43.6), (55.8, 45.3),
              (54.5, 47.0), (51.4, 52.3), (56.6, 52.3)], 2.5, NAVY),
    # события: звонок (красный) и задача (зелёный)
    ("circle", (43.0, 56.7, 1.15), "#C0392B"),
    ("line", [(45.2, 56.7), (64.5, 56.7)], 1.0, "#9AA3B5"),
    ("circle", (43.0, 60.3, 1.15), "#2E7D32"),
    ("line", [(45.2, 60.3), (60.5, 60.3)], 1.0, "#9AA3B5"),
    # кнопки
    ("circle", (41.6, 73.6, 2.7), "#2E3443"),
    ("circle", (48.3, 73.6, 2.7), "#2E3443"),
    ("circle", (59.7, 73.6, 2.7), "#2E3443"),
    ("circle", (66.4, 73.6, 2.7), "#2E3443"),
    ("rrect", (51.6, 70.0, 4.8, 7.2, 2.4), "#2E3443"),
    ("circle", (41.6, 73.6, 0.8), "#C0392B"),
    ("circle", (48.3, 73.6, 0.8), "#8C95A8"),
    ("circle", (59.7, 73.6, 0.8), "#8C95A8"),
    ("circle", (66.4, 73.6, 0.8), "#8C95A8"),
]


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
