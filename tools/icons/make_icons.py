"""Writes Clean's launcher icons: one adaptive icon and one manifest `activity-alias` per tile and accent.

Run from the project folder:  python tools/icons/make_icons.py

Reads  app/src/main/res/drawable/ic_launcher_foreground.xml (the mark; its ring is #8AB4FF, its dumbbell #F2F3F5)
Writes app/src/main/res/drawable/ic_launcher_bg_<tile>.xml and ic_launcher_fg_<dark|light>_<accent>.xml,
       app/src/main/res/mipmap-anydpi/ic_launcher_<tile>_<accent>.xml,
       and the aliases between the icons:begin / icons:end markers in app/src/main/AndroidManifest.xml.
black + blue is today's icon and keeps using @mipmap/ic_launcher; it is the one alias the manifest enables.
Keep TILES and ACCENTS in step with ACCENTS in ui/theme/Theme.kt and IconAliases.kt.
"""
import pathlib, re

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

# name -> (shade on a dark tile, shade on a light tile)
ACCENTS = {
    "Blue": ("8AB4FF", "2456B8"), "Teal": ("5FD4D0", "00746F"), "Green": ("6FD39A", "1E7A46"), "Orange": ("FFB068", "B25A00"),
    "Red": ("FF8A80", "B3261E"), "Pink": ("FF9EC4", "B2306B"), "Purple": ("C4A5FF", "6A3FC0"), "Ink": ("F2F3F5", "16181D"),
}
# tile -> (fill, is dark)
TILES = {"black": ("0E0F12", True), "dark": ("1C1E24", True), "white": ("FFFFFF", False), "paper": ("FFFFFF", False), "linen": ("F1EFEA", False)}
INK = "16181D"
LIGHT_MARK = "F2F3F5"  # the dumbbell on a dark tile
DEFAULT = ("black", "Blue")

HEAD = '<?xml version="1.0" encoding="utf-8"?>\n'
VECTOR = '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n'


def background(tile):
    fill, _ = TILES[tile]
    body = f'    <path\n        android:fillColor="#{fill}"\n        android:pathData="M0,0h108v108h-108z" />\n'
    if tile == "paper":  # a thin ink frame inside the part the launcher's mask leaves visible
        body += f'    <path\n        android:pathData="M34,20 L74,20 A14,14 0 0,1 88,34 L88,74 A14,14 0 0,1 74,88 L34,88 A14,14 0 0,1 20,74 L20,34 A14,14 0 0,1 34,20 Z"\n        android:strokeColor="#{INK}"\n        android:strokeWidth="1.5" />\n'
    return HEAD + VECTOR + body + "</vector>\n"


def foreground(mark, ring, dark):
    return mark.replace("#8AB4FF", "#" + ring).replace("#F2F3F5", "#" + (LIGHT_MARK if dark else INK))


def adaptive(tile, dark, accent):
    bg = "@drawable/ic_launcher_background" if tile == "black" else f"@drawable/ic_launcher_bg_{tile}"
    fg = f"@drawable/ic_launcher_fg_{'dark' if dark else 'light'}_{accent.lower()}"
    return (HEAD + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="{bg}" />\n    <foreground android:drawable="{fg}" />\n    <monochrome android:drawable="{fg}" />\n</adaptive-icon>\n')


def alias(tile, accent):
    name = f"Icon{tile.capitalize()}{accent}"
    icon = "@mipmap/ic_launcher" if (tile, accent) == DEFAULT else f"@mipmap/ic_launcher_{tile}_{accent.lower()}"
    enabled = "true" if (tile, accent) == DEFAULT else "false"
    return f'''        <activity-alias
            android:name=".{name}"
            android:enabled="{enabled}"
            android:exported="true"
            android:icon="{icon}"
            android:roundIcon="{icon}"
            android:label="@string/app_name"
            android:targetActivity=".Launch">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
'''


def main():
    mark = (RES / "drawable/ic_launcher_foreground.xml").read_text(encoding="utf-8")
    drawable, mipmap = RES / "drawable", RES / "mipmap-anydpi"
    aliases = []
    written = set()
    for tile, (_, dark) in TILES.items():
        if tile != "black":
            (drawable / f"ic_launcher_bg_{tile}.xml").write_text(background(tile), encoding="utf-8", newline="\n")
        for accent, (on_dark, on_light) in ACCENTS.items():
            ring = on_dark if dark else on_light
            fg = drawable / f"ic_launcher_fg_{'dark' if dark else 'light'}_{accent.lower()}.xml"
            if fg not in written:
                fg.write_text(foreground(mark, ring, dark), encoding="utf-8", newline="\n")
                written.add(fg)
            if (tile, accent) != DEFAULT:
                (mipmap / f"ic_launcher_{tile}_{accent.lower()}.xml").write_text(adaptive(tile, dark, accent), encoding="utf-8", newline="\n")
            aliases.append(alias(tile, accent))
    text = MANIFEST.read_text(encoding="utf-8")
    block = "        <!-- icons:begin (written by tools/icons/make_icons.py; exactly one alias is enabled, see IconAliases.kt) -->\n" + "".join(aliases) + "        <!-- icons:end -->"
    text, n = re.subn(r"        <!-- icons:begin.*?<!-- icons:end -->", lambda m: block, text, flags=re.S)
    if n != 1:
        raise SystemExit("the markers <!-- icons:begin --> and <!-- icons:end --> are missing from AndroidManifest.xml")
    MANIFEST.write_text(text, encoding="utf-8", newline="\n")
    print(f"{len(aliases)} aliases, {len(written)} marks")


main()
