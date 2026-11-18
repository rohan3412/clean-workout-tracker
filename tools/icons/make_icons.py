"""Writes Clean's themed monochrome launcher layer (Android 13+ themed icons).

Run from the project folder:  python tools/icons/make_icons.py

Reads  app/src/main/res/drawable/ic_launcher_foreground.xml (the mark: a blue ring and a pale dumbbell)
Writes app/src/main/res/drawable/ic_launcher_monochrome.xml, the same strokes in one colour, which is all a themed icon uses (its
       alpha); the launcher tints it. The adaptive icons in mipmap-anydpi point at it as their <monochrome> layer.
Since 1.1.0 there is one launcher icon (the app's own); the 40 icon aliases and the script that wrote them are gone.
"""
import pathlib, re

ROOT = pathlib.Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "app/src/main/res/drawable"


def main():
    mark = (DRAWABLE / "ic_launcher_foreground.xml").read_text(encoding="utf-8")
    mono = re.sub(r'android:strokeColor="#[0-9A-Fa-f]{6,8}"', 'android:strokeColor="#FF000000"', mark)
    mono = mono.replace("Clean's own mark: a dumbbell seen from the side, framed by an open ring that reads as a C.", "The mark in one colour, for Android's themed icons (only its shape counts).")
    (DRAWABLE / "ic_launcher_monochrome.xml").write_text(mono, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
