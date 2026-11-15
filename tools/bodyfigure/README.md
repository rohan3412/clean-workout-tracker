# Body figure generator

`bodygen.py` turns MuscleMap's Swift path sources (`musclemap/`, by Melih Colpan, MIT; the licence is
in `musclemap/MM_LICENSE` and the notice in `NOTICE.md`) into `app/src/main/java/clean/app/BodyPaths.kt`,
the outlines the body figure draws and hit-tests. Each outline is a muscle of Clean's, and where
MuscleMap draws a sub-group (upper chest, rear deltoid ...) also a head of that muscle.

```
python tools/bodyfigure/bodygen.py tools/bodyfigure/musclemap write app/src/main/java/clean/app/BodyPaths.kt
python tools/bodyfigure/bodygen.py tools/bodyfigure/musclemap preview preview.png   # needs matplotlib
```

MuscleMap has one "upper back"; which of its paths are Clean's lats is `LATS` in the script
(indexes 0 and 3, the two large lower outlines; override with the `LATS` environment variable).
`BodyPaths.kt` is generated: change the script, not the file.

Sides. MuscleMap lists a part's paths under `common:`, `left:` and `right:`; `BodyPart.side` is the
person's own side of each (null across the midline: the neck's centre outline, head and hair). The
`left:` lists are on the viewer's left in both views (the script checks every path against the
midline and stops if one is not), and the front view faces the viewer, so there the figure's RIGHT is
on the viewer's left; in the back view the viewer's left is the figure's left. After writing, the
script stops if a muscle or head has more outlines on one side than on the other, and
`BodyPathsTest` (app unit tests) checks the same on the generated file.
