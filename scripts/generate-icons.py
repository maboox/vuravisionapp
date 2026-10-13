#!/usr/bin/env python3
"""Generate Android vector drawables from Phosphor Icons (MIT, https://phosphoricons.com).

Usage: npm pack @phosphor-icons/core@2.1.1 && tar xzf phosphor-icons-core-2.1.1.tgz
       python3 scripts/generate-icons.py package/assets

Writes res/drawable/ph_<name>.xml (Bold weight, idle state) and ph_<name>_fill.xml
(Fill weight, active state). Colors are tinted at runtime.
"""
import re, sys, xml.etree.ElementTree as ET
from pathlib import Path

ICONS = {
    "pen": "pen", "highlighter": "highlighter", "eraser": "eraser", "cursor": "cursor",
    "hand_tap": "hand-tap", "hand": "hand", "move": "arrows-out-cardinal", "shapes": "shapes",
    "triangle": "triangle", "text": "text-t", "fonts": "text-aa", "layers": "stack",
    "sparkle": "sparkle", "toolbox": "toolbox", "timer": "timer", "columns": "columns",
    "users": "users-three", "menu": "list", "plus": "plus", "rotate": "arrow-clockwise",
    "undo": "arrow-u-up-left", "redo": "arrow-u-up-right", "share": "share-network",
    "caret_left": "caret-left", "caret_right": "caret-right", "close": "x", "eye": "eye",
    "eye_off": "eye-slash", "lock": "lock-simple", "unlock": "lock-simple-open", "trash": "trash",
    "background": "paint-roller", "expand": "corners-out", "fit": "corners-in", "folder": "folder-open",
    "file": "file", "file_pdf": "file-pdf", "save": "floppy-disk", "arrow_up": "arrow-up",
    "arrow_down": "arrow-down", "convert": "arrows-clockwise", "palette": "palette",
    "bucket": "paint-bucket", "search": "magnifying-glass", "graph": "chart-line-up", "ruler": "ruler",
    "compass": "compass-tool", "mic": "microphone", "mic_off": "microphone-slash", "info": "info",
    "atom": "atom", "dice": "dice-five", "settings": "gear-six", "globe": "globe", "copy": "copy",
    "duplicate": "copy-simple", "bold": "text-b", "italic": "text-italic",
    "align_left": "text-align-left", "align_center": "text-align-center", "align_right": "text-align-right",
    "pie": "chart-pie-slice", "image": "image", "sticky": "note", "mind_map": "tree-structure",
    "paste": "clipboard-text", "crop": "crop", "trophy": "trophy", "more": "dots-three",
    "stop": "square", "swap": "arrows-left-right", "content": "squares-four", "flask": "flask",
    "game": "game-controller", "spotlight": "flashlight", "table": "table",
}
NS = "{http://www.w3.org/2000/svg}"

def vector(svg: Path) -> str:
    root = ET.parse(svg).getroot()
    paths = []
    for el in root.iter():
        tag = el.tag.replace(NS, "")
        if tag == "path":
            paths.append(el.attrib["d"])
        elif tag in ("rect", "circle", "line", "polyline", "polygon", "ellipse"):
            sys.exit(f"{svg}: unsupported <{tag}>; extend the converter")
    body = "".join(f'<path android:fillColor="#FF1F1A52" android:pathData="{re.sub(r"\s+", " ", d)}"/>' for d in paths)
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" '
            'android:height="24dp" android:viewportWidth="256" android:viewportHeight="256">' + body + "</vector>\n")

def main():
    assets = Path(sys.argv[1] if len(sys.argv) > 1 else "package/assets")
    out = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable"
    for name, source in ICONS.items():
        (out / f"ph_{name}.xml").write_text(vector(assets / "bold" / f"{source}-bold.svg"))
        (out / f"ph_{name}_fill.xml").write_text(vector(assets / "fill" / f"{source}-fill.svg"))
    print(f"{len(ICONS)} icons, {len(ICONS) * 2} drawables")

if __name__ == "__main__":
    main()
