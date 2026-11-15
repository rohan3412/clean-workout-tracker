# Left and right (exercises done one side at a time)

Decided with the user on 2026-10-12. This is the one design every part of the app follows; the stage
that builds a part reads this file first. Change it here before changing it in code.

## What the user asked for

- Exercises done one side at a time (single-arm rows, one-leg work, alternating curls) are logged as
  **left and right**: each round has a left half and a right half with their own tick, and the app
  is **comprehensive and consistent** about it everywhere: history, charts, records, the body figure
  (asymmetry), templates, summaries.
- On the backend it is **ordinary sets** (a Strong CSV and a Clean CSV match fully). A round is a left
  set followed by a right set, each an ordinary row with its own weight and reps. Nothing in any
  column changes.
- A **default rest of 1 minute between the sides**.
- It must be **comparable with the same exercise done both sides at once** (closer than a variation
  is), and the same whether the user alternates after every rep or does all the left reps first.
- Charts: one point per round on the x axis, **split on the y axis** for the two sides (left 6, right
  8: two markers at the same x).
- "Perfect throughout the whole app, even the body image (for asymmetry)."

## The data (nothing new in the workout columns)

- A block (one exercise in one workout) is **paired** when its rows start with a `Note` row whose text
  is exactly the reserved `SIDES_NOTE = "Left, then right"` (Strong's own `Note` row type, which Strong
  shows as an exercise note and any reader of the format accepts). Clean writes it first in a paired
  block and hides it from the user's notes; it is the only addition to the CSV, and only for blocks
  logged as left and right. Imported Strong data never contains it, so an imported file still
  round-trips byte for byte.
- In a paired block the sets (not notes, not rest rows) are read **two at a time**: the first of each
  pair is the left set, the second the right set. Warm-up, failure and drop kinds are whatever each
  stored set has (since 2026-10-16 the logging screen lets each half of a round have its own kind: left Failure, right Normal; a new round starts with both the same). A last, lone set
  is a left set whose right half was not done (an incomplete round).
- **Rounds that are not a clean left-then-right** (a round where only one half was done, or a round
  done right first) are written with the layout **spelled out in the marker note**, one two-character
  token per round in row order: `Left, then right: LR RL L-`. `LR` is a round whose left set row comes
  first, `RL` a round whose right row comes first (the right half was ticked first; 2026-10-13: the
  order matters because the first side usually gets more reps), `L-` and `R-` a lone left or right set
  (one row). The plain marker `Left, then right` (no tokens) means the ordinary reading above, and is
  what is written whenever every round is `LR` (with at most a lone `L-` at the end); otherwise the
  tokens are always written. The older spelling, one letter per stored set (`Left, then right: L R R L R`),
  is still read exactly as before (a left set opens a round, a right set completes it) and never
  written again. A marker with all one-letter tokens is the old spelling, one with all two-character
  tokens from `LR RL L- R-` the new; a mix, or anything else, is an ordinary note. A layout whose
  sets do not add up to the block's sets falls back to the ordinary reading. So a lone right set is
  stored as a right set, with no placeholder rows.
- The order a round was done in is known from the tick: the half ticked first is written first. Ticking a
  half in a later round while the round before has one half ticked completes that other half (as ticking
  it would, undoable from the bottom bar). A finished workout that is corrected keeps each round's order.
- A set of an unpaired block has **no side** and counts for **both sides** wherever sides are
  compared. That is what makes a paired history comparable with an ordinary one: a left set is
  compared with every earlier set that was left or both-sides.
- Per exercise, "Left and right" is a remembered switch (`sides:<profileId>:<exercise>`, already
  there): it decides whether new blocks of that exercise start paired. A finished block carries its
  own marker, so turning the switch off later never changes history.
- A finished block can be marked or unmarked afterwards ("Read as left and right" on the workout page):
  that adds or removes the marker row (and nothing else). This is how imported history that was logged
  alternately becomes left/right history.

## Counting rules (so a round is one set, not two)

- **Weight of a set.** A set of a paired block counts **0.5** wherever sets are counted or summed:
  working-set counts, volume, sets per muscle, effective sets for fatigue, week totals, PR counts per
  workout. A complete round is exactly 1, the same as one ordinary set; a lone left set is 0.5. This is
  what makes a left/right exercise comparable with the same exercise done with both sides at once,
  whose single set trains both sides once.
- **Volume** of a round is therefore the **average of its two sides'** weight × reps (20 × 12 left and
  20 × 12 right is 240, as one two-dumbbell set is).
- **Muscles.** A round gives the muscle's set credit once (0.5 + 0.5); a round with only one side done
  (a lone set) gives half of it, on the figure, in sets per muscle and in fatigue alike. The left set
  credits the muscle's **left side**, the right set its **right side** (each side gets a full set from
  its own set); an ordinary set credits both sides equally. Side credits are what the body figure's
  asymmetry and the per-side fatigue read.

## Records and progress per side

- Every record (best estimated 1RM, set volume, reps, reps at a weight, longest hold) and every
  chart series is kept **per side**: the left series is the left sets plus the unpaired sets; the right
  series the right sets plus the unpaired sets. An unpaired exercise has two identical series, so
  everything behaves as before.
- **PR badges** are judged per side against that side's own history (so the weaker arm's progress is
  recognised and a strong arm cannot hide it). They are **shown once per round**: one badge row under
  the round, each kind listed once, with the side(s) that earned it ("1RM L", "Reps L R"). **Counts**
  of PRs (tiles, summary, history, personal bests) follow the same half weights as everything else: a
  kind earned by a set of a left/right block counts **0.5**, so both sides earning it count 1, one side
  counts a half (shown as 0.5, 1, 1.5 …), a lone left set earning it a half. An ordinary set's PR
  counts 1. A matched kind follows the same rules and stays out of counts.
- "Weight" (most reps at a weight lifted before): one badge per exercise per workout **per side**.
- **Progression and auto-fill** target each side from its own last session; the weight steps up when
  **both** sides reached the top of the range.
- **Personal bests**, the exercise's PRs tab, goals and "reached on" use the comparable
  (both-sides-or-better) figure: the weaker side of the exercise's best round; per-side bests are
  shown as extra rows.

## Showing it

- **Logging**: a round is one row, two halves (L and R), each with weight, reps and tick, a caption
  for last time's matching set, one rest of `rest.sides` (default 60 s) after the first tick and the
  ordinary rest after the second. The round is complete when both halves are ticked.
- **Workout page, history cards, summary, share picture, exercise History tab**: a paired block is a
  list of rounds, each "L 20 kg × 12 · R 20 kg × 10", with the imbalance visible (the lagging side
  and by how much). History's "Sets" column counts rounds; "Best set" is the best round.
- **Charts** (exercise page, full screen, the "All" chart): ONE series per measure that mixes both
  kinds of session, as the exercise's history really is (older sessions done both sides at once, newer
  ones left and right). A paired session is **one x position with two markers** (left and right)
  joined by a faint short stem, in the chart's own style; an unpaired session is **one ordinary marker**, with
  no stem and no split. There are **two lines**: a solid line through every session's left value and a
  dashed line through every session's right value, an unpaired session giving both its one value so
  the two run together there. A legend (solid line and filled dot = Left, dashed line and ring = Right)
  is shown only when a paired session is on the chart. An
  **imbalance chart** (right minus left, in %, per session) on the Charts tab, whose points are the
  paired sessions only.
- **Body figure**: a limb is shaded by **its own side's** credit. A "Sides" view beside Balance,
  Fatigue and Strength shades the left and right limbs separately and the rows read
  "L 24 · R 18 (R −25 %)"; the muscle page and head pages show left and right figures and counts.
  The orientation is the viewer's: in the front view the figure's right is on the viewer's left.
- **Templates** keep one planned set per round; a flagged exercise starts with that many rounds
  (twice as many sets) and its blocks start paired.
- Everything stays derived from the stored rows.

## Not doing

- No separate "left" and "right" exercises; no variation per side; no new CSV column; no per-side
  weights hidden anywhere other than the rows' own weight and reps.

## A kind per half (2026-10-16)

Asked for by the user: "separate failure, warmup etc, for left and right". Nothing is stored differently: every stored
set row already has its own Set Order (`W`, `F`, `D` or a number), so a round whose halves differ is two rows with two
kinds, the marker unchanged. The rules:

- A half's kind is its own set's. Records, PRs, counts, volume and fatigue read each set by its kind (a warm-up half is left
  out, its working partner counts 0.5); progression and hints already read a half against its own side.
- A round is **numbered** unless every half it has is a warm-up. If the halves differ the round shows its number (`roundKind`
  is null) and each half shows its own letter (`halfLetter`: `W`, `F`, `D`, `N` for normal); if they are the same the round shows
  the kind as before.
- A template keeps one kind per round: the kind of the half whose values it takes (the left half, the right when only that was
  done). Starting from it gives both halves that kind.
- "Warm-up sets" replaces a pair only when both its halves are warm-ups and none ticked.
