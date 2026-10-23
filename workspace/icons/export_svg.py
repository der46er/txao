#!/usr/bin/env python3
"""Renders each app's real adaptive launcher icon (background + foreground vectors) to workspace/icons/<id>.svg.

Run from anywhere:  python3 workspace/icons/export_svg.py
Reads  "Txao <Name>/app/src/main/res/drawable/ic_launcher_{background,foreground}.xml" for every app that has them
(Calculator, Notes and Files are not in this repo: their SVGs are committed and re-exported on the author's PC by
passing --src <path to the Android Apps folder>).
"""
import argparse
import pathlib
import re
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
APT = "{http://schemas.android.com/aapt}"

APPS = {  # id -> folder name
    "calculator": "Txao Calculator", "notes": "Txao Notes", "files": "Txao File Manager",
    "gallery": "Txao Gallery", "calendar": "Txao Calendar", "authenticator": "Txao Authenticator",
    "sms": "Txao SMS", "phone": "Txao Phone", "music": "Txao Music Player",
}


def color(c):
    """#AARRGGBB or #RRGGBB -> (hex rgb, opacity)."""
    c = c.lstrip("#")
    if len(c) == 8:
        return "#" + c[2:], int(c[:2], 16) / 255
    return "#" + c, 1.0


class Svg:
    def __init__(self, uid):
        self.uid, self.n, self.defs, self.body = uid, 0, [], []

    def gradient(self, g):
        self.n += 1
        gid = f"{self.uid}{self.n}"
        typ = g.get(A + "type", "linear")
        stops = "".join(
            f'<stop offset="{it.get(A + "offset")}" stop-color="{color(it.get(A + "color"))[0]}" stop-opacity="{color(it.get(A + "color"))[1]:.3f}"/>'
            for it in g.findall("item"))
        if typ == "radial":
            self.defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{g.get(A + "centerX")}" cy="{g.get(A + "centerY")}" r="{g.get(A + "gradientRadius")}">{stops}</radialGradient>')
        else:
            self.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A + "startX", 0)}" y1="{g.get(A + "startY", 0)}" x2="{g.get(A + "endX", 0)}" y2="{g.get(A + "endY", 0)}">{stops}</linearGradient>')
        return f"url(#{gid})"

    def paint(self, el, attr):
        v = el.get(A + attr)
        if v:
            return color(v) if v.startswith("#") else None
        for aapt in el.findall(APT + "attr"):
            if aapt.get("name") == "android:" + attr:
                return self.gradient(aapt.find("gradient")), 1.0
        return None

    def walk(self, el):
        for ch in el:
            if ch.tag == "group":
                g = lambda k, d: float(ch.get(A + k, d))
                px, py, tx, ty, sx, sy = g("pivotX", 0), g("pivotY", 0), g("translateX", 0), g("translateY", 0), g("scaleX", 1), g("scaleY", 1)
                self.body.append(f'<g transform="translate({px + tx} {py + ty}) scale({sx} {sy}) translate({-px} {-py})">')
                self.walk(ch)
                self.body.append("</g>")
            elif ch.tag == "path":
                at = [f'd="{ch.get(A + "pathData")}"']
                fill = self.paint(ch, "fillColor")
                if fill:
                    at.append(f'fill="{fill[0]}"' + (f' fill-opacity="{fill[1]:.3f}"' if fill[1] < 1 else ""))
                else:
                    at.append('fill="none"')
                st = self.paint(ch, "strokeColor")
                if st:
                    at.append(f'stroke="{st[0]}"' + (f' stroke-opacity="{st[1]:.3f}"' if st[1] < 1 else "") + f' stroke-width="{ch.get(A + "strokeWidth", 1)}"')
                    at.append(f'stroke-linecap="{ch.get(A + "strokeLineCap", "butt")}" stroke-linejoin="{ch.get(A + "strokeLineJoin", "miter")}"')
                if ch.get(A + "fillType") == "evenOdd":
                    at.append('fill-rule="evenodd"')
                self.body.append("<path " + " ".join(at) + "/>")


def render(res: pathlib.Path, uid: str):
    s = Svg(uid)
    for kind, short in (("background", "bg"), ("foreground", "fg")):
        f = next(p for p in (res / "drawable" / f"ic_launcher_{kind}.xml", res / "drawable" / f"ic_launcher_{short}.xml") if p.exists())
        s.walk(ET.parse(f).getroot())
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512"><defs>{"".join(s.defs)}'
            f'<clipPath id="{uid}m"><rect width="108" height="108" rx="30"/></clipPath></defs>'
            f'<g clip-path="url(#{uid}m)">{"".join(s.body)}</g></svg>\n')


if __name__ == "__main__":
    here = pathlib.Path(__file__).resolve().parent
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=str(here.parent.parent), help="folder containing the 'Txao <Name>' app folders")
    src = pathlib.Path(ap.parse_args().src)
    for app_id, folder in APPS.items():
        res = src / folder / "app" / "src" / "main" / "res"
        if any((res / "drawable" / n).exists() for n in ("ic_launcher_foreground.xml", "ic_launcher_fg.xml")):
            (here / f"{app_id}.svg").write_text(render(res, app_id), encoding="utf-8")
            print("wrote", app_id)
        else:
            print("skip", app_id, "(not in this checkout)")
