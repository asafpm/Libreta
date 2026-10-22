#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Draws the store icon (fastlane/.../images/icon.png, 512 x 512) from the launcher icon's
vector layers: the background, then the foreground, cut to the 72 dp an adaptive icon shows.
Needs rsvg-convert."""

import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
REPO = Path(__file__).resolve().parent.parent
DRAWABLES = REPO / "android/app/src/main/res/drawable"
OUT = REPO / "fastlane/metadata/android/en-US/images/icon.png"
ATTRIBUTES = {
    "pathData": "d",
    "fillColor": "fill",
    "strokeColor": "stroke",
    "strokeWidth": "stroke-width",
    "strokeLineCap": "stroke-linecap",
}


def paths(drawable: str) -> str:
    out = []
    for path in ET.parse(DRAWABLES / f"{drawable}.xml").getroot().iter("path"):
        attributes = {"fill": "none"}
        for name, svg in ATTRIBUTES.items():
            value = path.get(ANDROID + name)
            if value is not None:
                attributes[svg] = value
        out.append("<path " + " ".join(f'{k}="{v}"' for k, v in attributes.items()) + "/>")
    return "\n".join(out)


def main() -> None:
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72">\n'
        + paths("ic_launcher_background")
        + "\n"
        + paths("ic_launcher_foreground")
        + "\n</svg>\n"
    )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", "-o", str(OUT)], input=svg.encode(), check=True)
    print(OUT, file=sys.stderr)


if __name__ == "__main__":
    main()
