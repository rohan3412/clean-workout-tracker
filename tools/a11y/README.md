# a11y check

Open the screen to check on the phone or emulator, then run `python tools/a11y/check.py` (set `ADB` to the adb path if it is not on PATH, e.g. `/d/Android/Sdk/platform-tools/adb.exe`).
It lists clickable nodes with no text or content description, or smaller than 48 dp, with their bounds and the nearest words, and exits 1 if there are any; it looks at one screen at a time.
