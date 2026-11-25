"""Writes the iPhone app's icons: one 1024 x 1024 opaque PNG for the app and one for each of the eight themes.

Run from the code folder:  python iosApp/tools/make_icons.py

The artwork is the Android launcher icon's (app/src/main/res/drawable/ic_launcher_background.xml and ic_launcher_foreground_<theme>.xml): the
ring (three quarters of a circle, open to the right), the bar and the plates, drawn here with the same numbers on the same 108 unit canvas, the
background filling the whole square (iOS rounds the corners itself). Each theme's ring colour is read from its foreground drawable.
Needs Pillow. Output: iosApp/Clean/Assets.xcassets/<AppIcon or AppIcon-Theme>.appiconset/ with the PNG and its Contents.json.
"""
import json
import pathlib
import re

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "app/src/main/res/drawable"
CATALOG = ROOT / "iosApp/Clean/Assets.xcassets"
THEMES = ["Graphite", "Ember", "Crimson", "Moss", "Ocean", "Plum", "Ice", "Rose"]
SIZE = 1024
SUPER = 2  # drawn at twice the size and shrunk, for smooth edges


def colour(text):
    return tuple(int(text[i:i + 2], 16) for i in (1, 3, 5))


def strokes(theme):
    """The ring's colour and the plates' colour, in the order the drawable lists them."""
    xml = (DRAWABLE / f"ic_launcher_foreground_{theme.lower()}.xml").read_text(encoding="utf-8")
    found = re.findall(r'strokeColor="(#[0-9A-Fa-f]{6})"', xml)
    return colour(found[0]), colour(found[1])


def background():
    xml = (DRAWABLE / "ic_launcher_background.xml").read_text(encoding="utf-8")
    return colour(re.search(r'fillColor="(#[0-9A-Fa-f]{6})"', xml).group(1))


def render(theme):
    ring, ink = strokes(theme)
    side = SIZE * SUPER
    k = side / 108.0
    image = Image.new("RGB", (side, side), background())
    draw = ImageDraw.Draw(image)

    def line(x1, y1, x2, y2, width, rgb):
        draw.line([(x1 * k, y1 * k), (x2 * k, y2 * k)], fill=rgb, width=round(width * k))
        for x, y in ((x1, y1), (x2, y2)):
            r = width * k / 2
            draw.ellipse([x * k - r, y * k - r, x * k + r, y * k + r], fill=rgb)

    # The C: the arc from (73.1, 34.9) to (73.1, 73.1), radius 27 about (54, 54), the long way round through the left; in the picture's
    # clockwise angles that is 45 degrees (lower right) to 315 (upper right).
    width = 8
    r = 27 + width / 2  # PIL grows an arc's width inward from the box, so the box is the stroke's outer edge
    box = [(54 - r) * k, (54 - r) * k, (54 + r) * k, (54 + r) * k]
    draw.arc(box, 45, 315, fill=ring, width=round(width * k))
    for x, y in ((73.1, 34.9), (73.1, 73.1)):
        cap = width * k / 2
        draw.ellipse([x * k - cap, y * k - cap, x * k + cap, y * k + cap], fill=ring)
    line(38, 54, 70, 54, 4, ink)          # the bar
    line(45, 45, 45, 63, 5.5, ink)        # the inner plates
    line(63, 45, 63, 63, 5.5, ink)
    line(39, 49, 39, 59, 4.5, ink)        # the outer plates
    line(69, 49, 69, 59, 4.5, ink)
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    for theme in THEMES:
        name = "AppIcon" if theme == "Graphite" else f"AppIcon-{theme}"
        folder = CATALOG / f"{name}.appiconset"
        folder.mkdir(parents=True, exist_ok=True)
        render(theme).save(folder / f"{name}.png", "PNG")
        contents = {"images": [{"filename": f"{name}.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}], "info": {"author": "xcode", "version": 1}}
        (folder / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n", encoding="utf-8")
    (CATALOG / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
