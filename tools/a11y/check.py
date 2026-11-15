#!/usr/bin/env python3
"""Reports clickable nodes on the phone's current screen that a screen reader or a finger cannot use well.

Dumps the screen with uiautomator over adb, then lists every clickable node that
  (a) has no text and no content description on itself or any descendant, or
  (b) is smaller than 48 dp in either direction.
Exits 1 if there is any, 0 if not. The adb to use is in the ADB environment variable (default: adb).
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=True).stdout


def density():
    # "Physical density: 420"; an override, if there is one, is the one in force.
    out = adb("shell", "wm", "density")
    found = re.findall(r"density:\s*(\d+)", out)
    return int(found[-1]) if found else 160


def bounds(node):
    m = BOUNDS.match(node.get("bounds", ""))
    return tuple(int(v) for v in m.groups()) if m else None


def texts(node):
    """Every text and content description in the node and below it."""
    found = [node.get("text", ""), node.get("content-desc", "")]
    for child in node:
        found += texts(child)
    return [t for t in found if t.strip()]


def walk(node, parents=()):
    yield node, parents
    for child in node:
        yield from walk(child, parents + (node,))


def nearest_text(node, parents):
    """The node's own words, else those of the closest ancestor that has any."""
    own = texts(node)
    if own:
        return own[0]
    for parent in reversed(parents):
        around = texts(parent)
        if around:
            return around[0] + " (nearby)"
    return ""


def main():
    xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
    # The dump is followed by a line saying where it was written.
    xml = xml[xml.index("<?xml"): xml.rindex("</hierarchy>") + len("</hierarchy>")]
    root = ET.fromstring(xml)
    dp = density() / 160.0
    problems = 0
    for node, parents in walk(root):
        if node.get("clickable") != "true":
            continue
        box = bounds(node)
        if box is None:
            continue
        width, height = (box[2] - box[0]) / dp, (box[3] - box[1]) / dp
        reasons = []
        if not texts(node):
            reasons.append("no text or content description")
        if width < 48 or height < 48:
            reasons.append("%.0f x %.0f dp, under 48" % (width, height))
        if reasons:
            problems += 1
            print("[%d,%d][%d,%d] %s: %s | near: %r" % (*box, node.get("class", "?"), "; ".join(reasons), nearest_text(node, parents)))
    print("%d clickable node(s) to look at" % problems)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
