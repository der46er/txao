#!/usr/bin/env python3
"""
Generates the TXAO adaptive-icon family (background / foreground / monochrome vector drawables,
the adaptive-icon XML, an SVG per icon and a preview sheet).

Every icon follows the same grammar as Txao Calculator and Txao Notes:
  * night base #0B0E14 with three soft radial "lights" (lavender, rose, blue-grey)
  * one frosted-glass shape (white 30% -> 8% vertical gradient, 1u light edge)
  * white glyph strokes (95%), round caps and joins
  * ONE lavender accent disc (radius 13, #F1E8FF -> #CDB8FF) bottom-right with a dark #2E1B5C glyph

Run:  python generate_icons.py   -> ./out/<app>/res/..., ./out/svg/<app>.svg, ./out/preview.html
Android pathData and SVG path data share syntax, so each shape is written once.
"""
import os

SCALE = 0.74  # foreground group scale (Notes 0.70, Calculator 0.86)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
LAV0, LAV1, INK = "#F1E8FF", "#CDB8FF", "#2E1B5C"

LIGHTS_A = [(18, 84, 78, "B79CFF", 0x8C), (100, 92, 64, "D7A6C6", 0x66), (88, 14, 58, "8FA3D6", 0x66)]
LIGHTS_B = [(90, 90, 80, "B79CFF", 0x8C), (10, 95, 62, "D7A6C6", 0x66), (20, 12, 60, "8FA3D6", 0x66)]


def rr(x, y, w, h, r):
    return (f"M{x+r},{y}h{w-2*r}a{r},{r} 0 0 1 {r},{r}v{h-2*r}a{r},{r} 0 0 1 -{r},{r}"
            f"h-{w-2*r}a{r},{r} 0 0 1 -{r},-{r}v-{h-2*r}a{r},{r} 0 0 1 {r},-{r}z")


def circ(cx, cy, r):
    return f"M{cx-r},{cy}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 -{2*r},0z"


# glass: (path, y0, y1)   lines: (path, width)   dots: filled white shapes
# ink_fill / ink_line: glyph inside the lavender accent disc
ICONS = {
    "gallery": dict(
        lights=LIGHTS_A,
        glass=[(rr(22, 22, 64, 60, 14), 22, 82)],
        lines=[("M32,68l14,-16l11,12l7,-7l11,11", 4.2)],
        dots=[circ(40, 38, 4.5)],
        accent=(74, 76),
        ink_fill=["M74,67l2.2,6.8l6.8,2.2l-6.8,2.2l-2.2,6.8l-2.2,-6.8l-6.8,-2.2l6.8,-2.2z"], ink_line=[],
    ),
    "calendar": dict(
        lights=LIGHTS_B,
        glass=[(rr(22, 24, 64, 62, 14), 24, 86)],
        lines=[("M22,44h64", 1.6), ("M38,17v14M70,17v14", 4.5)],
        dots=[circ(38, 57, 3.6), circ(54, 57, 3.6), circ(70, 57, 3.6), circ(38, 71, 3.6), circ(54, 71, 3.6)],
        accent=(74, 78),
        ink_fill=[circ(74, 78, 4.2)], ink_line=[],
    ),
    "authenticator": dict(
        lights=LIGHTS_A,
        glass=[("M54,18L84,29v22c0,18 -13,30 -30,37c-17,-7 -30,-19 -30,-37v-22z", 18, 88)],
        lines=[("M54,52v14", 4.5)],
        dots=[circ(54, 46, 6.5)],
        accent=(76, 78),
        ink_fill=[], ink_line=[("M76,70v7l5,3", 3.4)],
    ),
    "sms": dict(
        lights=LIGHTS_B,
        glass=[("M36,22h36a14,14 0 0 1 14,14v26a14,14 0 0 1 -14,14H48L36,88V76a14,14 0 0 1 -14,-14V36a14,14 0 0 1 14,-14z", 22, 88)],
        lines=[],
        dots=[circ(38, 49, 3.8), circ(52, 49, 3.8), circ(66, 49, 3.8)],
        accent=(76, 80),
        ink_fill=["M70,77h12v9h-12z"], ink_line=[("M72,77v-3a4,4 0 0 1 8,0v3", 2.8)],
    ),
    "phone": dict(
        lights=LIGHTS_A,
        glass=[(rr(22, 22, 64, 64, 22), 22, 86)],
        lines=[("M38,38c-2,22 12,34 32,34", 10)],
        dots=[],
        accent=(76, 78),
        ink_fill=[], ink_line=[("M70,84l12,-12M72,72h10v10", 3.4)],
    ),
    "music": dict(
        lights=LIGHTS_B,
        glass=[(rr(22, 22, 64, 64, 18), 22, 86)],
        lines=[("M60,36v28M60,36c10,0 15,4 15,12", 4.5)],
        dots=[circ(51, 64, 8.5)],
        accent=(76, 78),
        ink_fill=["M72,71l10,7l-10,7z"], ink_line=[],
    ),
}


def hexa(rgb, a):
    return f"#{a:02X}{rgb}"


def background_xml(ic):
    s = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         '    xmlns:aapt="http://schemas.android.com/aapt"',
         '    android:width="108dp" android:height="108dp"',
         '    android:viewportWidth="108" android:viewportHeight="108">',
         '    <path android:fillColor="#0B0E14" android:pathData="M0,0h108v108h-108z" />']
    for cx, cy, r, rgb, a in ic["lights"]:
        s += ['    <path android:pathData="M0,0h108v108h-108z">',
              '        <aapt:attr name="android:fillColor">',
              f'            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}">',
              f'                <item android:offset="0" android:color="{hexa(rgb, a)}" />',
              f'                <item android:offset="1" android:color="{hexa(rgb, 0)}" />',
              '            </gradient>', '        </aapt:attr>', '    </path>']
    s.append('</vector>')
    return "\n".join(s) + "\n"


def grad(y0, y1, c0, c1, ind="            "):
    return [f'{ind}<aapt:attr name="android:fillColor">',
            f'{ind}    <gradient android:type="linear" android:startX="0" android:startY="{y0}" android:endX="0" android:endY="{y1}">',
            f'{ind}        <item android:offset="0" android:color="{c0}" />',
            f'{ind}        <item android:offset="1" android:color="{c1}" />',
            f'{ind}    </gradient>', f'{ind}</aapt:attr>']


def stroke_path(p, w, color):
    return (f'        <path android:pathData="{p}" android:strokeWidth="{w}" android:strokeColor="{color}" '
            'android:strokeLineCap="round" android:strokeLineJoin="round" />')


def foreground_xml(ic):
    ax, ay = ic["accent"]
    s = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         '    xmlns:aapt="http://schemas.android.com/aapt"',
         '    android:width="108dp" android:height="108dp"',
         '    android:viewportWidth="108" android:viewportHeight="108">',
         f'    <group android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:pivotX="54" android:pivotY="54">']
    for p, y0, y1 in ic["glass"]:
        s += [f'        <path android:pathData="{p}" android:strokeWidth="1" android:strokeColor="#80FFFFFF">'] + \
             grad(y0, y1, "#55FFFFFF", "#14FFFFFF") + ['        </path>']
    for p, w in ic["lines"]:
        s.append(stroke_path(p, w, "#F2FFFFFF"))
    for p in ic["dots"]:
        s.append(f'        <path android:pathData="{p}" android:fillColor="#F2FFFFFF" />')
    s += [f'        <path android:pathData="{circ(ax, ay, 13)}" android:strokeWidth="1" android:strokeColor="#99FFFFFF">'] + \
         grad(ay - 13, ay + 13, "#FFF1E8FF", "#FFCDB8FF") + ['        </path>']
    for p in ic["ink_fill"]:
        s.append(f'        <path android:pathData="{p}" android:fillColor="#FF2E1B5C" />')
    for p, w in ic["ink_line"]:
        s.append(stroke_path(p, w, "#FF2E1B5C"))
    s += ['    </group>', '</vector>']
    return "\n".join(s) + "\n"


def mono_xml(ic):
    ax, ay = ic["accent"]
    s = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         '    android:width="108dp" android:height="108dp"',
         '    android:viewportWidth="108" android:viewportHeight="108">',
         '    <!-- Themed-icon version: only alpha matters, the system tints it. -->',
         f'    <group android:scaleX="{SCALE}" android:scaleY="{SCALE}" android:pivotX="54" android:pivotY="54">']
    for p, _, _ in ic["glass"]:
        s.append(f'        <path android:pathData="{p}" android:strokeWidth="3" android:strokeColor="#FFFFFFFF" android:fillColor="#00000000" />')
    for p, w in ic["lines"]:
        s.append(stroke_path(p, w, "#FFFFFFFF"))
    for p in ic["dots"]:
        s.append(f'        <path android:pathData="{p}" android:fillColor="#FFFFFFFF" />')
    s.append(f'        <path android:pathData="{circ(ax, ay, 13)}" android:strokeWidth="3" android:strokeColor="#FFFFFFFF" android:fillColor="#00000000" />')
    for p in ic["ink_fill"]:
        s.append(f'        <path android:pathData="{p}" android:fillColor="#FFFFFFFF" />')
    for p, w in ic["ink_line"]:
        s.append(stroke_path(p, w, "#FFFFFFFF"))
    s += ['    </group>', '</vector>']
    return "\n".join(s) + "\n"


ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""


def svg(name, ic, size=512):
    ax, ay = ic["accent"]
    d = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">', '<defs>']
    for i, (cx, cy, r, rgb, a) in enumerate(ic["lights"]):
        d.append(f'<radialGradient id="{name}l{i}" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{cy}" r="{r}"><stop offset="0" stop-color="#{rgb}" stop-opacity="{a/255:.3f}"/><stop offset="1" stop-color="#{rgb}" stop-opacity="0"/></radialGradient>')
    for i, (_, y0, y1) in enumerate(ic["glass"]):
        d.append(f'<linearGradient id="{name}g{i}" gradientUnits="userSpaceOnUse" x1="0" y1="{y0}" x2="0" y2="{y1}"><stop offset="0" stop-color="#fff" stop-opacity="0.333"/><stop offset="1" stop-color="#fff" stop-opacity="0.08"/></linearGradient>')
    d.append(f'<linearGradient id="{name}a" gradientUnits="userSpaceOnUse" x1="0" y1="{ay-13}" x2="0" y2="{ay+13}"><stop offset="0" stop-color="{LAV0}"/><stop offset="1" stop-color="{LAV1}"/></linearGradient>')
    d.append(f'<clipPath id="{name}m"><rect width="108" height="108" rx="30"/></clipPath></defs>')
    d.append(f'<g clip-path="url(#{name}m)"><rect width="108" height="108" fill="#0B0E14"/>')
    for i in range(len(ic["lights"])):
        d.append(f'<rect width="108" height="108" fill="url(#{name}l{i})"/>')
    d.append(f'<g transform="translate(54 54) scale({SCALE}) translate(-54 -54)" stroke-linecap="round" stroke-linejoin="round">')
    for i, (p, _, _) in enumerate(ic["glass"]):
        d.append(f'<path d="{p}" fill="url(#{name}g{i})" stroke="#fff" stroke-opacity="0.5" stroke-width="1"/>')
    for p, w in ic["lines"]:
        d.append(f'<path d="{p}" fill="none" stroke="#fff" stroke-opacity="0.95" stroke-width="{w}"/>')
    for p in ic["dots"]:
        d.append(f'<path d="{p}" fill="#fff" fill-opacity="0.95"/>')
    d.append(f'<path d="{circ(ax, ay, 13)}" fill="url(#{name}a)" stroke="#fff" stroke-opacity="0.6" stroke-width="1"/>')
    for p in ic["ink_fill"]:
        d.append(f'<path d="{p}" fill="{INK}"/>')
    for p, w in ic["ink_line"]:
        d.append(f'<path d="{p}" fill="none" stroke="{INK}" stroke-width="{w}"/>')
    d.append('</g></g></svg>')
    return "".join(d)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


if __name__ == "__main__":
    cards = []
    for name, ic in ICONS.items():
        res = os.path.join(OUT, name, "res")
        write(os.path.join(res, "drawable", "ic_launcher_background.xml"), background_xml(ic))
        write(os.path.join(res, "drawable", "ic_launcher_foreground.xml"), foreground_xml(ic))
        write(os.path.join(res, "drawable", "ic_launcher_monochrome.xml"), mono_xml(ic))
        write(os.path.join(res, "mipmap-anydpi-v26", "ic_launcher.xml"), ADAPTIVE)
        write(os.path.join(OUT, "svg", f"{name}.svg"), svg(name, ic))
        cards.append(f"<figure style='margin:0;text-align:center'>{svg(name, ic, 200)}<figcaption>{name}</figcaption></figure>")
    write(os.path.join(OUT, "preview.html"),
          "<!doctype html><meta charset='utf-8'><title>TXAO icons</title>"
          "<body style='margin:0;background:#12151c;color:#ccd;font:14px sans-serif'>"
          "<div style='display:grid;grid-template-columns:repeat(3,200px);gap:24px;padding:24px;justify-content:center'>"
          + "".join(cards) + "</div></body>")
    print("ok:", ", ".join(ICONS))
