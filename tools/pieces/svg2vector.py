#!/usr/bin/env python3
"""Converts a piece set's SVG files into Android vector drawables.

Usage (from the repository root):

    python3 tools/pieces/svg2vector.py tools/pieces/cburnett core/ui/src/main/res/drawable

Each "<colour><Piece>.svg" (wK.svg, bN.svg, …) becomes "piece_<colour><piece>.xml". Handles what
the bundled sets use: nested <g> with presentation attributes, <path> and <circle>. Path data
is re-tokenised and written with explicit separators, because Android's parser does not accept
every compact form that SVG optimisers produce.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

INHERITED = ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-rule")
DEFAULTS = {"fill": "#000", "stroke": "none", "stroke-width": "1", "stroke-linecap": "butt",
            "stroke-linejoin": "miter", "fill-rule": "nonzero"}
ARGS = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
NUMBER = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")


def normalise_path(d: str) -> str:
    out = []
    i = 0
    command = None
    while i < len(d):
        char = d[i]
        if char.isspace() or char == ",":
            i += 1
            continue
        if char.isalpha():
            command = char
            i += 1
            if command.lower() == "z":
                out.append(command)
                continue
        elif command is None:
            raise ValueError(f"path data does not start with a command: {d[:20]}")
        elif command in "mM" and out and not out[-1][0].isalpha():
            pass  # further coordinate pairs after a moveto are implicit linetos; keep as written
        count = ARGS[command.lower()]
        values = []
        for index in range(count):
            while i < len(d) and (d[i].isspace() or d[i] == ","):
                i += 1
            if command.lower() == "a" and index in (3, 4):
                values.append(d[i])  # arc flags are single digits and may be written unseparated
                i += 1
                continue
            match = NUMBER.match(d, i)
            if not match:
                raise ValueError(f"number expected at {i} in {d}")
            values.append(match.group())
            i = match.end()
        out.append(command + ",".join(values))
        # A command letter may be followed by several argument groups.
        if command == "M":
            command = "L"
        elif command == "m":
            command = "l"
    return " ".join(out)


def colour(value: str) -> str:
    value = value.strip()
    if re.fullmatch(r"#[0-9a-fA-F]{3}", value):
        value = "#" + "".join(c * 2 for c in value[1:])
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", value):
        raise ValueError(f"unsupported colour {value}")
    return value.upper()


def convert(svg_path: Path) -> str:
    root = ET.parse(svg_path).getroot()
    view = root.get("viewBox").split()
    width, height = view[2], view[3]
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{width}dp"',
        f'    android:height="{height}dp"',
        f'    android:viewportWidth="{width}"',
        f'    android:viewportHeight="{height}">',
    ]

    def walk(element, inherited):
        tag = element.tag.split("}")[1]
        style = dict(inherited)
        for name in INHERITED:
            if element.get(name) is not None:
                style[name] = element.get(name)
        unknown = set(element.attrib) - set(INHERITED) - {"d", "cx", "cy", "r", "style", "width", "height", "viewBox"}
        if unknown:
            raise ValueError(f"{svg_path.name}: unsupported attributes {unknown} on <{tag}>")
        if tag in ("svg", "g"):
            for child in element:
                walk(child, style)
            return
        if tag == "path":
            data = normalise_path(element.get("d"))
        elif tag == "circle":
            cx, cy, r = (float(element.get(k)) for k in ("cx", "cy", "r"))
            data = f"M{cx - r:g},{cy:g} a{r:g},{r:g},0,1,0,{2 * r:g},0 a{r:g},{r:g},0,1,0,{-2 * r:g},0 z"
        else:
            raise ValueError(f"{svg_path.name}: unsupported element <{tag}>")
        lines.append("    <path")
        if style["fill"] != "none":
            lines.append(f'        android:fillColor="{colour(style["fill"])}"')
            if style["fill-rule"] == "evenodd":
                lines.append('        android:fillType="evenOdd"')
        if style["stroke"] != "none":
            lines.append(f'        android:strokeColor="{colour(style["stroke"])}"')
            lines.append(f'        android:strokeWidth="{style["stroke-width"]}"')
            if style["stroke-linecap"] != "butt":
                lines.append(f'        android:strokeLineCap="{style["stroke-linecap"]}"')
            if style["stroke-linejoin"] != "miter":
                lines.append(f'        android:strokeLineJoin="{style["stroke-linejoin"]}"')
        lines.append(f'        android:pathData="{data}" />')

    walk(root, DEFAULTS)
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def main() -> None:
    source, target = Path(sys.argv[1]), Path(sys.argv[2])
    target.mkdir(parents=True, exist_ok=True)
    for svg in sorted(source.glob("*.svg")):
        name = f"piece_{svg.stem.lower()}.xml"
        (target / name).write_text(convert(svg), encoding="utf-8")
        print(name)


if __name__ == "__main__":
    main()
