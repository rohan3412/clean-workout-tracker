"""Reads MuscleMap's Swift path sources (MIT, Melih Colpan) and writes Clean's BodyPaths.kt.

usage: bodygen.py <dir with the .swift files> preview|write <out>

write needs no other input: LATS (below) is which of MuscleMap's four "upper back" paths are Clean's lats.
"""
import re, sys, os

src = sys.argv[1]

def parts(name):
    """(slug, paths) per part, each path as (d, source label): MuscleMap lists a part's paths under common:, left: and right:."""
    text = open(os.path.join(src, name), encoding='utf-8').read()
    out = []
    for block in re.split(r'BodyPartPathData\(', text)[1:]:
        slug = re.search(r'slug:\s*\.(\w+)', block).group(1)
        paths = []
        for lists in re.finditer(r'(common|left|right):\s*\[(.*?)\]', block, re.S):
            paths += [(d, lists.group(1)) for d in re.findall(r'"(M[^"]+)"', lists.group(2))]
        out.append((slug, paths))
    return out

def points(d):
    """End points of every segment, enough for a bounding box and a rough outline."""
    need = {'m': 2, 'l': 2, 'c': 6, 'q': 4, 'a': 7, 'h': 1, 'v': 1, 'z': 0}
    num = re.compile(r'\s*,?\s*(-?\d*\.?\d+(?:e-?\d+)?)')
    flag = re.compile(r'\s*,?\s*([01])')
    x = y = sx = sy = 0.0; i = 0; cmd = None; pts = []
    while i < len(d):
        if d[i].isspace() or d[i] == ',': i += 1; continue
        if d[i].isalpha():
            cmd = d[i]; i += 1
            if cmd in 'Zz': x, y = sx, sy; pts.append((x, y))
            continue
        n = need[cmd.lower()]
        a = []
        for k in range(n):
            # An arc's two flags are single digits and may be written run together ("01-.19").
            m = (flag if cmd.lower() == 'a' and k in (3, 4) else num).match(d, i)
            a.append(float(m.group(1))); i = m.end()
        rel = cmd.islower(); c = cmd.lower()
        if c == 'h': x = x + a[0] if rel else a[0]
        elif c == 'v': y = y + a[0] if rel else a[0]
        else:
            ex, ey = a[-2], a[-1]
            x, y = (x + ex, y + ey) if rel else (ex, ey)
        if c == 'm': sx, sy = x, y; cmd = 'l' if rel else 'L'
        pts.append((x, y))
    return pts

front, back = parts('MaleFrontPaths.swift'), parts('MaleBackPaths.swift')

# The middle of each view's box (727 wide; the front starts at x 0, the back at x 718).
MIDLINE = {'front': 727 / 2, 'back': 718 + 727 / 2}

def side_of(label, d, view):
    """The person's own side of a path, or None for one that crosses the midline.

    MuscleMap's left: lists are on the viewer's left in both views (checked here against the geometry). The front view
    faces the viewer, so the viewer's left is the figure's RIGHT there; in the back view it is the figure's LEFT.
    """
    xs = [p[0] for p in points(d)]
    lo, hi = min(xs), max(xs)
    mid = MIDLINE[view]
    if lo < mid < hi:
        if label != 'common':
            sys.exit(f'{label} path crosses the midline in the {view} view')
        return None
    on_viewers_left = hi <= mid
    if label != 'common' and on_viewers_left != (label == 'left'):
        sys.exit(f"{label} path is on the viewer's {'left' if on_viewers_left else 'right'} in the {view} view")
    if on_viewers_left:
        return 'RIGHT' if view == 'front' else 'LEFT'
    return 'LEFT' if view == 'front' else 'RIGHT'

if sys.argv[2] == 'preview':
    import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
    fig, ax = plt.subplots(1, 2, figsize=(14, 12))
    for a, view, off in ((ax[0], front, 0), (ax[1], back, 718)):
        for slug, paths in view:
            for k, (d, _) in enumerate(paths):
                p = points(d); xs = [q[0] for q in p]; ys = [q[1] for q in p]
                a.fill(xs, ys, alpha=0.35); a.text(sum(xs) / len(xs), sum(ys) / len(ys), f'{slug}{k}', fontsize=5, ha='center')
        a.set_xlim(off, off + 727); a.set_ylim(1375, 95); a.set_aspect('equal')
    fig.savefig(sys.argv[3], dpi=130); sys.exit()

# Which of Clean's muscles each MuscleMap part shades; None is a part that is never shaded.
# MuscleMap has one "upper back" for the whole back above the waist; its paths are told apart below.
MAP = {'chest': 'CHEST', 'abs': 'ABS', 'biceps': 'BICEPS', 'triceps': 'TRICEPS', 'deltoids': 'SHOULDERS', 'obliques': 'OBLIQUES',
       'quadriceps': 'QUADS', 'calves': 'CALVES', 'tibialis': 'TIBIALIS', 'adductors': 'ADDUCTORS', 'trapezius': 'TRAPS', 'neck': 'NECK',
       'forearm': 'FOREARMS', 'lowerBack': 'LOWER_BACK', 'gluteal': 'GLUTES', 'hamstring': 'HAMSTRINGS',
       'serratus': 'SERRATUS', 'hipFlexors': 'HIP_FLEXORS', 'rotatorCuff': 'ROTATOR_CUFF',
       'hands': None, 'knees': None, 'ankles': None, 'feet': None, 'head': None, 'hair': None}
# MuscleMap's sub-groups, kept as heads: slug -> (group, head). Drawn after every group, so a head is never covered.
HEADS = {
    'front': {'upperChest': ('CHEST', 'UPPER_CHEST'), 'lowerChest': ('CHEST', 'LOWER_CHEST'), 'frontDeltoid': ('SHOULDERS', 'FRONT_DELT'),
              'upperAbs': ('ABS', 'UPPER_ABS'), 'lowerAbs': ('ABS', 'LOWER_ABS'), 'innerQuad': ('QUADS', 'VASTUS_MEDIALIS'),
              'outerQuad': ('QUADS', 'VASTUS_LATERALIS'), 'upperTrapezius': ('TRAPS', 'UPPER_TRAPS')},
    'back': {'rearDeltoid': ('SHOULDERS', 'REAR_DELT'), 'rhomboids': ('UPPER_BACK', 'RHOMBOIDS'),
             'lowerTrapezius': ('TRAPS', 'LOWER_TRAPS'), 'upperTrapezius': ('TRAPS', 'UPPER_TRAPS')},
}
# The forearm's group outline is also the outline of one head, by view.
FOREARM_HEAD = {'front': 'FOREARM_FLEXORS', 'back': 'FOREARM_EXTENSORS'}
# Indexes (in the Swift source) of the upper-back paths that are Clean's lats: the two large lower ones.
LATS = set(int(i) for i in os.environ.get('LATS', '0,3').split(',') if i)
# MuscleMap files the broad band across the groin (from the hip bone down and in to the top of the inner thigh) under
# adductors, one per side. It is the iliopsoas and pectineus region, so it is Clean's hip flexors: beside MuscleMap's own small
# hip-flexor triangle it makes the crease read as one muscle. Indexes are those of the front view's adductor paths.
HIP_BANDS = {0, 3}

def rows(view, side):
    """(muscle, head, the person's side or None, path) per outline: groups first, then heads."""
    groups, heads = [], []
    for slug, paths in view:
        if slug == 'upperBack':
            groups += [('LATS' if k in LATS else 'UPPER_BACK', None, side_of(l, d, side), d) for k, (d, l) in enumerate(paths)]
        elif slug in MAP:
            groups += [('HIP_FLEXORS' if slug == 'adductors' and side == 'front' and k in HIP_BANDS else MAP[slug], None, side_of(l, d, side), d) for k, (d, l) in enumerate(paths)]
            if slug == 'forearm': heads += [('FOREARMS', FOREARM_HEAD[side], side_of(l, d, side), d) for d, l in paths]
        elif slug in HEADS[side]:
            group, h = HEADS[side][slug]
            heads += [(group, h, side_of(l, d, side), d) for d, l in paths]
        else:
            print('unlisted slug left out:', side, slug, file=sys.stderr)
    return groups + heads

def kotlin(name, view, side):
    lines = [f'internal val {name}: List<BodyPart> = listOf(']
    for muscle, head, limb, d in rows(view, side):
        lines.append(f'    BodyPart({("Muscle." + muscle) if muscle else "null"}, {("Head." + head) if head else "null"}, {("Side." + limb) if limb else "null"}, "{d}"),')
    lines.append(')')
    return '\n'.join(lines)

open(sys.argv[3], 'w', encoding='utf-8', newline='\n').write('''package clean.app

import clean.core.Head
import clean.core.Muscle
import clean.core.Side

/*
 * The body figure's outlines: the male front and back views of MuscleMap by Melih Colpan
 * (github.com/melihcolpan/MuscleMap), used under the MIT License; the notice is in NOTICE.md and
 * is shown in the app under Settings. Taken from MuscleMap's Swift sources by a script
 * (tools/bodyfigure/bodygen.py), unchanged: SVG path data in a 727 x 1280 box per view (the front
 * starts at x 0, the back at x 718, both at y 95). Each part is paired with the muscle of Clean's
 * that it shades; null is a part that is never shaded. MuscleMap's sub-groups (upper chest, rear
 * deltoid ...) are kept as heads: a part with a head is that head's outline, drawn over its
 * group's, and the group is its muscle. Heads come after all the groups so none is covered.
 * A part that lies on one side of the body carries that side, the person's own (the front view
 * faces the viewer, so the figure's right is on the viewer's left there; in the back view the
 * viewer's left is the figure's left); null is a part that crosses the midline.
 * Generated: do not edit by hand.
 */

/** One outline: the [muscle] it belongs to, the [head] it shows when it is a head's, the [side] of the body it lies on (null across the midline), and its path data. */
internal class BodyPart(val muscle: Muscle?, val head: Head?, val side: Side?, val path: String)

''' + kotlin('BODY_FRONT', front, 'front') + '\n\n' + kotlin('BODY_BACK', back, 'back') + '\n')
from collections import Counter
for side, view in (('front', front), ('back', back)):
    # Every outline that has a side has a twin on the other: a muscle or head drawn on one limb is drawn on both.
    twins = Counter((m, h, l) for m, h, l, _ in rows(view, side))
    for (m, h, l), n in twins.items():
        other = 'LEFT' if l == 'RIGHT' else 'RIGHT'
        if l and twins[(m, h, other)] != n:
            sys.exit(f'{side} view: {m}/{h} has {n} outlines on the {l} and {twins[(m, h, other)]} on the other side')
    c = Counter((m, h) for m, h, _, _ in rows(view, side))
    print(side, len(rows(view, side)), {f'{m}/{h}' if h else m: n for (m, h), n in sorted(c.items(), key=str) if h or m in ('SERRATUS', 'HIP_FLEXORS', 'TIBIALIS', 'ROTATOR_CUFF', 'CALVES')})
