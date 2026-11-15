# Domain: how lifting is recorded

Background a developer needs to record strength training correctly. Examples are taken from the real data in `original/`.

## Vocabulary

- **Rep**: one full movement. **Set**: reps done without rest. **Workout** (session): everything logged in one visit.
- **Working set**: a set that counts as training. **Warm-up set**: a lighter set done to prepare; it must never count toward records or volume.
- **Failure**: the set ended because another rep was not possible. This lifter trains to failure most of the time (647 `F` sets against 821 numbered ones).
- **Drop set**: straight after a set, weight is reduced and reps continue with no rest. It is done fatigued, so it says little about strength but does add volume.
- **Superset**: two exercises alternated with no rest between them.
- **RPE** (rate of perceived exertion): 1 to 10 effort score; 10 is failure. **RIR** (reps in reserve) is the same idea counted the other way: RPE 8 is about 2 RIR.
- **1RM**: the most weight liftable for one rep. Rarely tested; usually estimated.
- **Volume**: weight × reps, summed. A rough measure of work done.
- **Progressive overload**: doing slightly more over time (weight, reps, or sets). The whole reason to keep a log.
- **Split**: how training is divided across days. This data uses push / pull / legs and upper / lower.

## What makes two sets comparable

A record only means something between sets of the same thing. Four separate ideas are currently mixed together in Strong's exercise names and note rows:

| Idea | Changes the lift? | Example from the data | Belongs in |
|---|---|---|---|
| Exercise | yes | Lat Pulldown, Chest Fly | exercise |
| Equipment | yes | Lateral Raise on Dumbbell / Cable / Machine | exercise name, as Strong does |
| Variation | yes | `Wide grip`, `Neutral Grip`, `Supinated`, `One side at a time`, `Bent knees`, `Simultaneous` | **variation** (new) |
| Setup | no | `Seat at 5`, `Seat circle 4.5` | sticky setting on the exercise |
| Observation | no | `Right shoulder bottleneck`, `Not the best form`, `Leg shaking` | free-text note |

The core improvement over Strong is pulling **variation** out of the note text so records can be kept per variation.

### Kinds of variation

- **Grip orientation**: pronated (overhand), supinated (underhand), neutral (palms facing), mixed.
- **Grip width**: close, shoulder-width, wide.
- **Attachment**: rope, straight bar, V-bar, single handle. In the data this is sometimes already in the name (`Tricep Pushdown (Rope Extension)` against `Triceps Pushdown (Cable - Straight Bar)`).
- **Laterality**: both sides together, one side at a time, alternating. One-sided work also has a side (`Oblique Crunch (Machine) (Left)` and `(Right)` are separate exercises today).
- **Body position**: standing, seated, kneeling, incline, decline.
- **Range and tempo**: paused, slow lowering, partial range.

### The model the user chose

- A **variation is a full exercise**: its own name, its own history, its own records. `Lat Pulldown (Machine)` and `Lat Pulldown (Machine) (Wide Grip)` are two exercises.
- An **overall exercise** groups variations, for example "Lat Pulldown" holding the two above plus a neutral-grip one. It has no sets of its own. Exactly one member is its **default variation**.
- Variations are named freely by the user. The list of kinds above is guidance for suggestions, not a fixed schema.
- An exercise that has never been grouped behaves as an overall exercise with one variation, so nothing changes for it.

### Rules for variations

- Picking an overall exercise when building a workout adds one card with the default variation selected.
- Variation is a property of the **set**. Each set row has a dropdown to switch variation, so one card can mix them. The data needs this: it has `3rd set was neutral grip` and a block that switches from wide to neutral partway. A new set starts on the previous set's variation.
- **As built so far (2026-10-08, the user asked for the switch to live in the exercise's options menu):** "Switch variation" in a card's options lists the group's other members, makes a new one from a typed label (`Overall name (Label)`, logged like the current exercise and with its muscles until edited), or groups in an existing exercise. The sets after the last ticked one move to a new card below under that variation; with nothing ticked the card just changes exercise. So a switch shows as two cards, which is also how it is written to the CSV. Not built yet: one card mixing variations with a per-set dropdown, the default variation, picking an overall exercise in the list, and the review screen for old notes.
- "Previous performance" beside a set is the last time that variation was done, not the last time the overall exercise was.
- Records are computed per variation, with a roll-up across all variations on the overall exercise.
- Grouping can be done at any time and undone without touching set data. It suits existing exercises too: the three Lateral Raise exercises (Dumbbell, Cable, Machine), or Oblique Crunch Left and Right.
- Existing notes are not parsed automatically. `Wide grip `, `Wide gip` and `Wide grip` are the same thing typed three ways, and `Better form` is not a variation at all. Migration is a review screen: group identical note texts, the user moves the sets under each to a variation or leaves the note as a note.

### Rule-made variations (2026-10-09)

On top of the hand-written groups, the library offers variations for every built-in exercise by rule (`core/Variations.kt`). A rule reads the words in the exercise's name, the equipment the name gives and whether its main muscles are upper body, lower body or trunk, and offers labels: grip width and orientation for pulls, rows, presses, curls and raises; one arm or one leg at a time where the equipment allows it; cable attachments; stance, foot height and toe direction for leg work; seated, incline and decline; glute or lower-back focus for back extensions; weighted for bodyweight work; and pause, slow eccentric, partials and 1.5 reps for nearly everything. A rule-made variation is named `Exercise (Label)`, for example `Lat Pulldown (Machine) (Wide Grip)`. It is worked out when asked for and is not in the exercise list; it becomes a real exercise only once it is logged or edited. A label is left out where the library already has that exercise under its own name (`Lat Pulldown - Wide Grip (Cable)`, `Hammer Curl (Dumbbell)`). The library puts such a name back with the exercise it was made from, and it takes that exercise's muscles and load type, only when the name is exactly a built-in exercise plus a label the rules give that exercise; a typed name that merely looks similar is still never grouped. Labels are single: there is no `(Wide Grip) (Pause)`.

### Renaming and merging an exercise (2026-10-12)

Two names for one exercise, or a typo, are put right from the exercise's page (Edit, "Rename or merge…"), which is the second place, after `moveNoted`, where stored exercise names are rewritten, and only at the user's word. A name that is an exercise already (logged, or with settings of its own; a built-in one never used does not count) makes it a **merge**: the dialog says "This merges n sessions of “Old” into “Existing”. Their records are combined." Any other name is a **rename**: "Rename “Old” to “New” in n workouts."

- **What is rewritten** (`Repo.renameExercise`, one Room transaction): the `Exercise Name` of every row of the profile whose name is exactly the old one (byte for byte, no trimming, no case folding, so `Wide grip ` and `wide grip` are other exercises and are not touched); the exercise's `exercise_setting` row (a rename moves it, with its favourite, note, rest, goal and colour; a merge keeps the target's and drops the source's; a plain rename of an exercise with no row of its own makes one holding its automatic values, so it is logged and trains as before, unless the new name is a built-in exercise); other exercises whose overall exercise is the old name; kv `prog:<profile>:<name>` (moved, the target's kept on a merge); the exercise inside every template's blocks; the exercises each program day was planned with (`program:<profile>`, `renameInProgram`); and the workout in progress (by `AppModel`). `reward.seen` goal ids, workout numbers and every stored number are untouched, and no workout moves, so there is no `renumber`.
- **The log** is kv `draft:renames:<profile>` (a `draft:` key, so the options flow never carries it): the 20 latest entries, each with the date, both names, the ids of the rows changed, and what was there before (the settings rows, the template JSON, the progression, the program text). `Repo.undoRename` puts the old name back on exactly those rows that still carry the new one, and restores the rest in one transaction. Rows logged under the new name since were never in the entry and stay as they are; a row moved elsewhere by hand, or deleted, is skipped; a template or the program edited since is left as it is. Undo is in Settings, Data, "Renamed exercises", with a confirm that says so.
- Nothing in `docs/data-format.md` changes: a renamed export is the same format with other text in the name column, and the round trip holds for untouched data. The row-level rules (`core/Rename.kt`) are tested in plain Kotlin: rename then undo, and merge then undo, give a byte-identical file for a fixture with a trailing-space name and one that differs only by case.

### Iso-Lateral Row history fix (2026-10-16)

Since 2026-11-02 it is applied once per profile at start-up with no page (`AppModel.fixIsoRowHistoryOnce`), guarded by kv `isofix.done:<profile>`, written even when the plan is empty, so later workouts are never touched; the replaced rows stay under `draft:isofix:<profile>`.

A one-time correction of the user's history (`core/IsoRowFix.kt`, `planIsoRowFix`), all logged under the exact name `Iso-Lateral Row (Machine)` although it was three stretches: wide grip, then neutral grip (now the standard, which keeps the name), then one arm at a time with both arms' weights added together. Blocks are walked in date order and read by their opening notes (the left and right marker ignored; case does not matter).
- **Grip.** Every block before the first block whose note says "neutral" is wide grip, notes or none. From that block on it is neutral, except a block whose own note says "wide" or "regular grip", which is wide. Notes naming no grip ("Better form", "95 with grips") follow the phase.
- **One arm at a time.** From the first block whose note says "standing", "one arm", "one side" or "single arm" on, every set row is both arms added together. Each becomes a left set and a right set, left first, of half the weight, with the same reps, seconds, RPE and kind (W, F, D or numbered); the block gets the marker note `Left, then right` first, the user's notes as they were, and a rest row stays after the set it followed. Set Order is numbered as `rowsFor` numbers a paired block. "Standing" is not an exercise of its own. Before that block, sets stay ordinary both-sides sets and only the name can change.
- **Names.** Wide blocks (split or not) are named exactly `Iso-Lateral Row (Wide Grip)`; neutral ones keep `Iso-Lateral Row (Machine)`. Blocks that already carry the marker are left completely alone, name and rows. Other names, a trailing space included, are not touched. Workout-level fields and notes stay.
- **Half weights** are written as Clean writes weights (`decimalText`): 90.0 is 45.0, 95.0 is 47.5, 112.5 is 56.25; empty stays empty, a sign is kept.

The plan gives per block its action (rename, split, unchanged, nothing), the set lines (`F 90×9 → L 45×9 + R 45×9`) and totals (workouts moved to wide, workouts converted, set rows split, blocks left alone). `Repo.applyIsoRowFix` replaces the rows of each affected workout in one transaction (then `renumber`), keeps the originals under kv `draft:isofix:<profile>` and sets kv `isofix.done:<profile>`; `undoIsoRowFix` puts the rows back with their ids and clears both. Nothing else is set (no `sides:` key). The entry points (Settings, Data at the top; a notice on Home, hidden by kv `isofix.hidden:<profile>`) show only while the plan is not empty and the fix is not done. All three keys go with the profile. PRs, charts and volume are derived from rows and follow by themselves.

### Heads (2026-10-12)

A muscle group can have heads (`Head` in `core/Heads.kt`): the part of the group an exercise singles out, such as the upper chest, the soleus or the long head of the triceps. A head is a separate type that belongs to exactly one group; it is not a `Muscle`. Rules:

- A head is counted once, under its own group: a head set adds the head's share (1 main, 0.5 supporting, 0.25 minor) for each working set, warm-ups excluded. Heads never change the group numbers (`muscleSets`, breakdown, fatigue, strength, last trained), which are computed from the group sets alone.
- A head only applies where its group is main or supporting for the exercise. This is what keeps "leg raise" off Lateral Leg Raises (glutes) and "incline" off Incline Row.
- Head rows do not sum to the group: an exercise that names no head of a group still counts for the group, and `otherWork` gives those sets for showing beside the heads as "Other work".
- Which exercises name heads is a rule table in `core/Heads.kt` by the words in the name, isolation work only (calf raises by seated or not, curls by variety, pushdowns and extensions by head, shrugs, leg extensions, leg curls, crunches and leg raises, hip thrusts and abduction, lateral, front and rear raises). Compounds (squat, deadlift, row, overhead press, pull up, lunge, leg press) name none; the one exception is the angle of a chest press or fly. The table never writes anything: a user's stored heads (a fourth part of the stored muscles) win, otherwise `headsOf` applies the rules, also to exercises whose groups the user has stored. Nothing lets the user edit heads yet.

Four muscle groups were added on 2026-10-12: serratus, hip flexors, tibialis and rotator cuff. The library moved to them: tibialis raises (were calves), shoulder external and internal rotations (were shoulders; shoulders stay as supporting), scapula push ups (serratus main) and push up plus (chest and serratus), and hanging, lying, seated and captain's-chair leg and knee raises, toes to bar and knees to elbows (hip flexors supporting, abs stay main). The body figure has no outline for the four yet.

## Auto-fill sets (decided 2026-10-08)

"Auto-fill sets" replaces a card's unticked sets with the user's preferred layout (default 2 working sets and 1 drop set) and fills in what to aim for. The starting point is the best set of the last session; the goal is an estimated 1RM above the exercise's best. Reps first: the same weight with as few extra reps as it takes. When that would pass 12 reps, one weight step up (the smallest jump in the exercise's history, else 2.5 kg) at the fewest reps that still beat it. A drop set is the target weight less a fifth, rounded to the step, at the same reps. Where bodyweight is part of the load the rule is simply one more rep; timed and cardio exercises are left empty. An exercise with a progression scheme (below) takes its targets from the scheme instead.

## Progression (decided 2026-10-12)

Double progression per exercise, in `core/Progression.kt`. A scheme is a rep range and a weight step, `Progression(minReps, maxReps, step)`; the step is kg like everything stored and shown in the exercise's unit. It is kept per profile in kv `prog:<profileId>:<exercise name exactly as stored>` as `8-12@2.5` (a kv key, not a column, so no migration), and set from a card's options ("Progression") or by tapping its line on the exercise page's Info tab. Off by default; with no scheme everything behaves as before.

`nextFor(lastSession, scheme, loadType)` gives one `Target` per working set of the exercise's last session, in order. Working sets are normal and failure sets with a weight and at least one rep; warm-ups and drop sets are not targets.
- If every working set was at one weight and every one reached `maxReps` or more, all sets go to weight + step at `minReps` (the weight is rounded to four decimals).
- Otherwise each set keeps its weight and aims for one more rep. A set already at `maxReps` or more, a set to failure and a set at RPE `PROGRESSION_HARD_RPE` = 9.5 or more repeat weight and reps. Sets of different weights are therefore handled one by one, and the step up needs a single shared weight.
- Only loads where heavier is harder get targets: normal and bodyweight (added weight). Assisted (less help is the harder way, which a weight step cannot express), timed and cardio get none. Nothing in the last session, or no working set in it, gives no targets.

In a workout, a working set's empty boxes show its target as placeholder (and a tick takes it) in place of last time's values; the Previous cell keeps last time's set and adds a quiet `→ 62.5 × 8`. Targets line up with the card's working sets by position, per variation; sets beyond the last target fall back to last time's values. "Auto-fill sets" uses the targets for that exercise in place of `nextTarget`'s: the layout's working sets take the targets in order (the last repeats), warm-ups and the drop set are worked out from the first target.

## Left and right (decided 2026-10-12)

An exercise can be marked "Left and right" (a card's options, or its line on the exercise's Info tab): one side at a time, as with single-arm or single-leg work. A card's sets stay a flat list, saved as ordinary consecutive sets in the order left, right, left, right; the only thing in the rows is the reserved note `Left, then right` that opens a block logged this way (`docs/data-format.md`, The left and right marker). The whole design, including what the later stages change, is in `docs/left-right.md`. Pair k of the screen is sets 2k (left) and 2k+1 (right) of the card, notes skipped (`sidePairs` in `core/Sides.kt`). Since stage 3 the card's own `DraftBlock.paired` (stored in the draft) decides what is drawn, doubled and saved as pairs; the exercise's switch only sets what new cards start as. Saving writes each round's ticked halves left first and passes the stored order to `NewBlock(paired = true, sides = ...)`, so a round with only its right half ticked is stored as a right set and the marker spells the order out (`Left, then right: L R R L R`); loading a block back (`editWorkout`) rebuilds its `Block.rounds` and gives a missing half an empty, unticked place that is never saved. Live badges judge each half against its own side's history (`SidedBests.judge`, `prBestsBySide`) and show each kind once per round with its side or sides; hints and progression targets are per side (`Session.forSide`, `nextForSides`). A finished block is marked or unmarked from its page (`Read as left and right`), with an Undo.

Since 2026-10-16 each half of a round has its own kind (`docs/left-right.md`, A kind per half): the rows already carry one Set Order each, so storage is unchanged; counts, volume, fatigue and PRs read each set by its own kind; a round is numbered unless both its halves are warm-ups (`roundCounts`), shows its number when the halves differ (`roundKind` null) and each half its letter (`halfLetter`); a template keeps the kind of the half whose values it takes.

- **Where it lives**: kv `sides:<profileId>:<exercise name exactly as stored>` = `on`, absent when off (no column, no migration). A rename or merge moves it like `prog:` (the target's kept on a merge) and the rename log restores it; deleting a profile removes it.
- **Turning it on** pairs the card's existing sets in order and, for an odd count, appends one empty set of the last one's kind. Turning it off leaves the sets as they are.
- **A pair** shares one type (warm-up, failure, drop), one variation, is removed together, and "Add set" adds a pair (or the missing half). Auto-fill and the warm-up calculator lay out `n` pairs where they lay out `n` sets; both sides aim for the same target, which is the left set's of the last session.
- **Rest**: ticking the first side of a pair that is not done yet rests the "between sides" length (kv `rest.sides`, seconds, default 60, 0 is none; an exercise whose own rest is off has none); ticking the second side, or a set with no partner, rests as any set (`restFor`). Un-ticking never rests.
- **Templates** keep `n` planned sets per exercise: starting one lays out `n` pairs, and the "update the template?" comparison and the template it would save count pairs (`pairTypes`). Making a template from a past workout halves the sets of a left and right exercise the same way.

### Counting sets of a left and right block (2026-10-12)

Wherever sets are counted or summed, a set of a block marked left and right weighs 0.5, so a round is one set and a lone left set is half of one (`Block.weightOf`, `Block.workingCount`): working-set counts, volume (a round is the average of its sides' weight x reps), sets per muscle and head, effective sets for fatigue, week totals and Deload's weekly sums. Warm-ups are still left out and a muscle trained by any working set still counts as trained. Per side, a set of an ordinary block credits both sides in full and a left (right) set of a marked block credits only that side (`muscleSetsBySide`, `headSetsBySide`), so a complete round gives each side what one ordinary set gives it. Records, PRs and charts are per side; see Left and right: records below and `docs/left-right.md`.

### Left and right: carrying values over (2026-10-16)

"Auto-fill should work across left right and normal": last time's sets feed the grey hints, the Previous column, auto-fill, progression targets, the "Last time ... Try ..." line and a tick (which takes the hints), whichever way last time and now are logged (`carriedSets` in `core/SidesCarry.kt`; set k of the card is read against item k of the list it returns, kinds paired by position as before).

- Left and right now, left and right then: each side reads its own sets (`Session.forSide`).
- Left and right now, ordinary sets then: both halves of round k take set k.
- Ordinary now, left and right then: set k takes round k as one set: the heavier side's, meaning the side with the higher estimated 1RM (weight x (1 + reps/30), or the reps, seconds or distance where there is no weight), ties to the left; when a round has only one half, that half. A session's rounds are read by `roundsOfSession`: a left and a right set that follow each other, either way round, are a round; any other set stands alone. The kind (warm-up, working, failure, drop) is the chosen set's.
- The number pad's Same key copies from the same side of the round above, and when that side's box is empty (or there is no such side) from the other side of it (`setsAbove`); a left half in the first round has nothing above, a right half there takes its own left half.

### Left and right: which side went first (2026-10-13)

A round remembers whether its right set was done before its left (`Round.rightFirst`, from the `RL` token of the marker; false for lone sets and for everything read from the plain or old spellings). It is information only: records, counts, charts and the body figure read a round's two halves as before, and `Block.setSides` still gives each set row's side in row order. Reading: `Block.rounds`. Writing: `NewBlock(paired = true, shapes = ...)` with a `RoundShape` per round, and `sidesMarker(shapes)` for the note. The order comes from the logging screen: the half ticked first is written first, and finishing a corrected workout keeps the order it had.

### Left and right: records (2026-10-12)

The rules of `docs/left-right.md`, Records and progress per side, as built in core (stage 2).

- **Series.** A set of an ordinary block has no side and is in both series; the left series is the left sets plus the ordinary ones, the right series the right sets plus the ordinary ones (`Session.forSide`; `Session.sides` is parallel to `sets`, empty for a session of ordinary sets). An exercise with no left and right block has two equal series, so every figure is what it was.
- **Comparable figure.** `recordsOf`, `progressOf`, `goalReachedOn` and `stalled` give for each kind (and each measure of a session) the weaker side's value; a kind one side has none of has none, and a side with no set in a session leaves the other's as that session's figure. Per side: `recordsBySide` (`SideRecords(left, right, comparable)`) and `progressBySide` (`SidedProgress`, whose `left` and `right` exist only for a session with a left and right block). `imbalance` gives right minus left per measure (1RM, reps, volume), also in percent of the larger side, and the lagging side by the first of 1RM, volume, reps that differs. A goal above the rep limit needs a qualifying set on each side.
- **PRs.** `SidedBests(left, right)`; `SidedBests.judge` calls `judgePrs` (still the one rule) once per group of one exercise in one workout: left sets against the left bests, right sets against the right, ordinary sets against the stricter of the two (per kind the larger best); every group is judged against the bests from before the workout. "Weight" is therefore one badge per side. Then a left set updates the left bests only, a right set the right only, an ordinary set both. The per-set list keeps its shape (`prsOf`, one entry per set in `blocks.flatMap { sets }` order). `prBestsOf` keeps returning `PrBests`, the stricter, which is what an ordinary set tonight has to beat; `prBestsBySide` gives both.
- **Counts.** A count of PRs weighs each set as every other count does (`Block.weightOf`): `recordCount(workout, kindsPerSet)` is the sum over sets of its weight times its real (not matched) kinds, so a kind both sides of a round earned is 1, one side's 0.5 and an ordinary set's 1; `recordCounts` maps dates to it as a Double. `records()` still counts the kinds of one set. Display is by round: `sidesOf(leftKinds, rightKinds)` lists each kind once with the side(s) that earned it.
- **Progression.** `nextForSides` targets each side from its own last session (`Session.forSide`); the weight steps up only when both sides reached the top of the range at one weight each, otherwise each side adds a rep as `nextFor` does. For equal sides it is `nextFor` twice.

### Left and right: muscles, fatigue and strength by side (2026-10-12)

Stage 6 of `docs/left-right.md`: the body figure's limbs and the muscle and head pages read the sides separately.

- **Sets.** `muscleSetsBySide` and `headSetsBySide` (`SideCredit(left, right)`): an ordinary set credits both sides one set, a left set the left side one set, a right set the right side one; a complete round therefore gives each side what one ordinary set gives it, which is also the group figure (half a set from each half). `muscleSideWeeks` and `headSideWeeks` give the same by week (keyed by the Monday) for the page's chart; `hasSideWork` says whether any block of the time frame was logged left and right with a working set, which is what shows the Body tab's Sides view.
- **Gap.** `SideCredit.shortfall()` is the lagging side and its shortfall in whole percent of the larger side (null when neither has credit or the sides are even to the whole percent); `phrase()` writes it `R −25 %`, `spoken()` `right 25 percent less`. A row's order in the Sides view is the unrounded shortfall, largest first, then the total.
- **Fatigue.** `fatigueBySide` runs `fatigue`'s model once per side: a set of a left and right block counts for the side it was done with in full (weight 1, not the half it has in the group figure), a set of the other side for nothing, an ordinary set as it does for both. The decay, the normal session and the squash are the same, and the session's best is the one of the whole exercise (both sides' sets), so a side that did nothing reads 0, ready. For ordinary work both sides equal `fatigue` exactly. A muscle's page says "Ready by ... (left)" for the side that is ready later (`readyAt` of each side).
- **Strength.** `lastTrainedBySide` and `retainedStrengthBySide` read a muscle as trained on a side by that side's sets and the ordinary ones; a side never trained reads at the floor, like a muscle never trained. For ordinary work both equal the group function.
- **The figure.** In Fatigue and Strength a limb is shaded by its own side; a row keeps the group figure and adds `L fatigued · R ready` (or `L 100% · R 83%`) only when the sides read differently (a different word, a different whole percent).

## Goals

A goal is a weight and reps per exercise (`ExerciseSetting.goal`, `100.0x5`, parsed by `parseGoal`). Up to the 1RM rule's rep limit it stands for an estimated 1RM (`goalOneRm`, on the real load); a session meets it when its best estimated 1RM reaches that. Above the limit it has no estimate and is met by a set of at least that weight (at most, for an assisted lift) for at least those reps, warm-ups never counting. `goalReachedOn` gives the date of the first session that met it, each session on the weigh-in near it. It shows as a thin green line on the exercise page's single-measure chart (the Est. 1RM chart; above the rep limit the Top weight or Least help chart), as "Reached on <date>" (opening that workout) under the goal on the Info tab, as a quiet "Goal 100 × 5" under the name on a workout card while it is not reached (opening the exercise page), and once on the summary of the workout that first reached it ("Goal reached: Bench Press 100 kg × 5"; `reward.seen:<profileId>` id `goal:<exercise>:<goal text>`, so a goal already met in an earlier workout is never celebrated, and changing the goal makes a new one).

**Progress and projection (2026-10-16, `core/GoalProgress.kt`).** An exercise goal's progress is the best estimated 1RM (the user's `OneRm` rule, the weaker side for left and right) as a share of the goal's estimate, capped at 100 %, and whole once `goalReachedOn` finds a session. Above the rep limit it is the most reps at the goal's weight or more (at most, for assisted; the weaker side's) over the goal's reps, with no date. A timed or cardio exercise has none. The projected day (`projectDate`) is the straight line (`linearFit`) through the estimated 1RM of the sessions in the last 56 days, read at the goal's estimate; it needs at least 3 points spanning at least 14 days and a positive slope, a line already at the target gives today, and a day more than 730 days away (or no day) shows nothing. It reads "On track for about 12 Feb" (`projectedLine`: `d MMM`, the year added when not this year). It shows as a line under the progress bar on the Info tab's Goal section, as " · 72 %" after "Goal 100 × 5" under the name in a workout, and in the Goals card.

**Body-weight goal.** Kv `goal.weight:<profileId>` = `target|start|yyyy-MM-dd` (`WeightGoal`), both weights in kg and the day it was set; the start is the latest weigh-in then, so a start above the target is a loss and one below a gain (a target equal to the current weight is refused). Progress is the share of the distance from the start to the target covered by the latest weigh-in, 0 to 1, whole once the latest weigh-in is at or past the target (a weight that moved away from the target counts 0). The day uses `projectDate` on the weigh-ins of the last 56 days, falling for a loss. Set and changed from the weight page (a quiet "Set a goal", then the weight ruler; the target is also a goal line on the chart) and from the Goals card; removed with the profile.

**Workouts per week.** The weekly target of Consistency (`goal.week:<profileId>`) is the third goal: this week's count of the target, and the week streak. No separate storage.

## Weekly set targets (decided 2026-10-12)

A range of sets a week per muscle group, counted the way the Body tab counts them (`muscleSets`: working sets, a supporting muscle half, a minor one a quarter). `SetRange(min, max)` in `core/SetTargets.kt`; kv `target.sets:<profileId>` is the default (`10-20` when absent), `target.<MUSCLE>:<profileId>` a muscle's own (`6-9`, or `off` for no target on that muscle); `setRangeFor(own, default)` resolves them. `weeklySetState(sets, range)`: under below `min`, over above `max`, otherwise within (both ends inside).

Shown as: a shaded band and the caption "Target 10 to 20 a week" on a muscle page's "Sets per week" chart (the caption opens the muscle's own range: two numbers, "Use the default", "No target"); on the Body tab's Balance view, in the Week time frame only, a tick (the green of "ready") after a group's value when within the range and a quiet "+" when over, nothing when under; in the week report each muscle's figure is amber under its range, green within, and quiet over, with the bracketed usual figure kept (this replaced colouring by 60% of what has been usual). The default is set under Settings, Workout, "Weekly sets per muscle".

## Deload suggestion (decided 2026-10-12)

`deloadSignals` in `core/Deload.kt`, pure, derived from the log and the current fatigue. Weeks are Monday to Sunday; "complete" weeks are those before the one in progress; weekly working sets are `Workout.workingSets` summed. A signal that needs more history than there is does not hold. Four signals, each with a one-line reason:
- **Stalled** (`DELOAD_STALLED_MIN` = 3): of the exercises with a goal and the `DELOAD_TOP_EXERCISES` = 10 with the most working sets in the last `DELOAD_WINDOW_WEEKS` = 8 weeks, at least three are `stalled` (core, no new best estimated 1RM in the last five sessions).
- **Volume rise**: for each of the last `DELOAD_RISE_WEEKS` = 3 complete weeks, its working sets were at least `DELOAD_RISE` = 1.25 times the mean of the 8 weeks before that week (so the weeks before each include the other risen ones). Needs 11 complete weeks of history.
- **Fatigued**: at least `DELOAD_FATIGUED_MIN` = 4 muscle groups read "fatigued" now (`fatigueWord`).
- **No lighter week**: none of the last 8 complete weeks had at most `DELOAD_LIGHT_SHARE` = 0.7 of the mean of those same eight weeks. Needs 8 complete weeks of history and a non-zero mean.

`Deload.suggested` is at least `DELOAD_SIGNALS_MIN` = 2 signals. The app shows one quiet line, "A lighter week might help", under the reason on the Today card and at the top of the week report; it opens a sheet of the signals (a stalled exercise opens its page, the volume signals the Trends page, fatigue the Body tab's Fatigue view) and "Not now", which hides the line for 7 days (kv `deload.snooze:<profileId>`, the day it was pressed). It is never a snackbar and never on the summary; there is no "start a deload" action.

## BMI (decided 2026-10-08)

Derived, never stored: each weigh-in divided by the square of the profile's height. The chart shows the standard adult bands (under 18.5, 18.5 to 25, 25 to 30, 30 and over), which do not vary with adult age; under-20s would need growth charts by age and sex and are not covered. The page says plainly that BMI is unreliable for a heavy, low-body-fat muscular build.

## Muscle fatigue (decided 2026-10-12)

Fatigue (`fatigue()` in `core/Muscles.kt`), restored on 2026-10-16 to the model used before 2026-10-12. Each session gives a muscle a stimulus: for every working set (warm-ups never count) load x reps, times (load / the session's best estimated 1RM for that exercise)^1.5 (fixed Epley, 12 reps at most, on the real load; capped at 1), times the muscle's share (main 1, supporting 0.5, minor 0.25); a set with no load to measure counts `FATIGUE_REFERENCE` / 3. The stimulus is divided by the muscle's normal session, which starts at `FATIGUE_REFERENCE` = 2000 kg x reps and only ever moves down (a third of the way to each lighter session), and added to the running level, which fades with a 36-hour half-life; only the last 30 days are read. The level is squashed to 1 - e^-t and read as ready below 0.25, recovering to 0.5, fatigued above. A set of a left and right block counts half for the muscle and in full for its own side (`fatigueBySide`). `readyAt(level, now)` is the half-life run backwards. An estimate, and labelled as one.

Why it was restored: the effective-set model of 2026-10-12 (intensity against a 90-day best, a 1.5-set floor, six sets as normal) read much cooler. On the real export, an hour after each of the last 120 workouts it showed 369 muscles fatigued against 651 for this model, and 22 against 168 two days later; muscles that only support (lower back, glutes, hamstrings, traps) never passed the floor. The user found it "never gets red even with hard workouts". The colours were not the cause: the Fatigue view has always used the meaning colours (`CleanColors.ready` / `recovering` / `fatigued`), never the look's colour scale, and turns grey only in Mono.

## Units

- **Weight**: stored in kg, always. Each profile shows and enters either kg (default) or lbs, and an exercise can override the profile's unit. 1 lb = 0.45359237 kg. The data's `54.4311` is 120 lb rounded to four decimals, so convert lbs entries to kg at four decimals and the app matches what Strong wrote. Show lbs by converting back and rounding to the nearest 0.5.
- **Height**: stored in cm. Entry accepts cm, inches, or feet and inches (1 in = 2.54 cm). It is a profile field, not a dated measurement.
- Body measurements stay in cm as the export has them.

## Records (PRs)

Computed from working sets only. Per exercise and variation:

| Record | Definition |
|---|---|
| Weight | Heaviest weight lifted for at least one rep. |
| Reps | Most reps in one set. Shown per weight too: best reps at each weight, which is the table lifters actually consult ("last time at 40 I got 8"). |
| Estimated 1RM | Highest estimate from any single set. |
| Set volume | Highest weight × reps in one set. |
| Session volume | Highest total weight × reps for that exercise in one workout. |

Which sets count:

| Set type | Weight / reps / 1RM | Volume |
|---|---|---|
| Normal, Failure | yes | yes |
| Drop | no | yes |
| Warm-up | no | no |

A record is an event with a date, so the app can show "new PR" at the moment it happens and a history of when each record moved.

### One rule for PRs

`judgePrs` in `core/Stats.kt` is the only place that decides which kinds a set earns. A finished workout (`prsOf`) and a workout being logged (the badges on a ticked set) both call it, so a badge seen while logging is the badge saved. Per exercise and workout, the set that first reached the workout's best of a kind gets it; a later equal set gets nothing. The kinds are 1RM, set volume, reps, "weight" (the most reps at a weight lifted before, or, since 2026-11-02 at the user's word "highest weight in an exercise should be a weight PR", a working set heavier than any before: `PrBests.heaviest`, for an assisted lift the least assistance, `lightest`; warm-ups and drop sets never count, a hold with a weight does; an equal heaviest weight is judged by its reps at that weight, so a tie is at most matched; one badge per exercise per workout as before, a PR over a match, then the heaviest) and duration; an exercise's first workout earns all but "weight", and equalling the best earns the matched kind.

**Duration.** A set is a hold by its data: it has seconds and no reps (an empty Reps column, which is how Clean writes timed sets, or 0). The longest hold of the workout against the longest before gets "duration". When a weight is held (above 0), a hold longer than ever at a weight held before also gets "weight"; an unweighted hold, or a new weight, never does. Estimated 1RM, set volume and reps do not apply to a hold. Cardio has no holds. The PRs tab shows the longest hold and the longest at each weight held.

### Estimating 1RM

The formula is the user's choice (Settings, Workout, "Estimated 1RM"), with a rep limit of 10, 12, 15 or 20 above which a set gets no estimate. Every formula gives the weight itself for a single. The default is Hybrid up to 12 reps (`OneRm.DEFAULT`; the user's word, 2026-10-22: "make hybrid the default"; Epley before), for anyone who has not chosen a formula. Settings offers three, Hybrid (recommended), Brzycki and Epley (`ONE_RM_CHOICES`); a formula chosen earlier from the longer list below keeps working and stays on the list until another is picked. `estimate1Rm` in `core/Records.kt` is the one place a set becomes an estimate (`OneRmFormulaTest`).

| Formula | 1RM = |
|---|---|
| Epley | `weight × (1 + reps / 30)` |
| Brzycki | `weight × 36 / (37 − reps)` |
| Lander | `100 × weight / (101.3 − 2.67123 × reps)` |
| Lombardi | `weight × reps^0.10` |
| Mayhew | `100 × weight / (52.2 + 41.9 × e^(−0.055 × reps))` |
| O'Conner | `weight × (1 + 0.025 × reps)` |
| Wathan | `100 × weight / (48.8 + 53.8 × e^(−0.075 × reps))` |
| Average | the mean of Epley, Brzycki and Lombardi |
| Hybrid (default) | Brzycki under 10 reps, Epley from 10 up (the two give the same figure at exactly 10) |

They agree closely up to about 10 reps and drift apart above that; all become unreliable past roughly 12, which is why the default stops there. This matters here: the data has sets of 20, 25 and 50 reps. Each formula is a fixed factor times the weight, so the same table run backwards gives the weight for a number of reps (`weightForReps`): the PRs tab's "Rep maxes" (1, 2, 3, 5, 8, 10) are the best estimated 1RM divided by that factor. A goal of more reps than the limit is met by a set of at least that weight for at least those reps, not by an estimate.

**Factor in RPE** (kv `onerm.rpe` = `on`, off by default): a set logged with an RPE from 5 to 10 counts its reps in reserve as done, `reps + (10 − RPE)` (`effectiveReps`), so 100 × 8 at RPE 8 is estimated as 10 reps and a single at RPE 9 as two; RPE 10, no RPE, or one under 5 (`RPE_FLOOR`) changes nothing. The rep limit applies to the reps done, so the most Brzycki is asked is 20 + 5 reps, short of where it breaks down at 37. Targets to aim for (`nextTarget`) are judged with the chosen formula but without RPE.

The chosen rule reaches every estimate: records, PRs and live badges (`SetItem.rpe`, `PrSet.rpe`), charts, Home's 1RM tiles, a set's 1RM on a workout's page and the exercise's History tab, the L 1RM and R 1RM columns, goals, the comparison with last time (`compareWithLast(rule)`, with the rep limit opened to 30 so long sets still compare) and the plain CSV export's `est_1rm_kg` (`exportPlainCsv(rule)`); the Strong CSV has no estimate column. Changing it recomputes PRs off the main thread (`AppModel.prs` follows `oneRm`); no stored row changes. Two things keep their own fixed sum, as they are not an estimate shown to the user: the muscle fatigue figure (`fatigue()`, tuned to its own reference) and the ranking of a block's best set (`bestSetOf`).

### Personal bests (2026-10-12)

`Screen.PersonalBests`, from the Profile tab's "PRs, 30 days" tile (the PRs of the kinds `records()` counts that were set in the last 30 days). **Best lifts** lists every exercise that has an estimated 1RM (the chosen formula, the real load for bodyweight and assisted lifts), best first, with the set and the day it came from; a line opens the exercise on its PRs tab. **By month** lists the months newest first with their PR count, each opening into the PRs of that month (`prRows`, matched marks left out); a row opens its workout. Both are worked out once for the whole history off the main thread.

## Loads that are not what they look like

- **Bodyweight lifts** (Pull Up, Push Up, Squat (Bodyweight)): logged weight is added load, usually `0.0`. Real load is bodyweight plus that. With weight `0.0`, volume is zero and 1RM is meaningless, so reps is the record that matters.
- **Assisted lifts** (Pull Up (Assisted), Triceps Dip (Assisted)): logged weight is help from the machine. Less is better, so the weight record direction is reversed. Real load is bodyweight minus assistance.
- Both link to the bodyweight log in the measurements export. Each exercise knows which of the three load types it is: normal, added-to-bodyweight, or assistance.
- **Real load** (decided 2026-10-08): bodyweight plus the logged weight, or bodyweight minus the assistance. The bodyweight is the weigh-in **closest to the workout**, before or after, and only if it is **within 7 days** of it. With no weigh-in that close the real load is unknown and nothing is guessed in its place.
- What needs the real load, and so exists only for workouts with a weigh-in close enough: volume, estimated 1RM, set and session volume records. What never needs it: the weight record as logged (most added weight; least assistance for an assisted lift) and best reps at each logged weight.
- While logging, a bodyweight or assisted exercise shows a warning on its card when there is no weigh-in in the last 7 days, with a shortcut to log one.
- An assistance machine's number is not exactly a kilo off bodyweight per kilo of help, so the real load is an approximation. It is consistent on the same machine.
- Showing assisted and unassisted pull-ups as one progression is still a later feature; the real load is what makes it possible.
- **Dumbbells**: this lifter logs the weight of one dumbbell. Volume for dumbbell lifts is therefore half the true figure. Do not "correct" stored numbers; keep the convention and be consistent within an exercise.
- **Machines and cables**: the number is whatever the stack or plates say. It is not comparable between two different machines, and plate-loaded machines exclude the carriage weight. Records are meaningful only on the same machine, which is why the home / gym distinction (workout names like `Pull Gym` and `Random Pullups in my house`) may be worth recording later.
- **Timed holds** (Plank): the record is longest time. **Cardio** (Cycling, Walking): distance, time, and pace. Both can be logged (as the load types Timed and Cardio); a hold has its longest-hold record and duration PR (above), cardio records are not computed yet.

## Consistency

Decided 2026-10-12 (the user asked for "a reward for consistency"); the code is `core/Consistency.kt`. Nothing about a week's result is stored: it is worked out from the workouts and one small option each time.

- **Weekly target**: workouts per week, 1 to 7, a week being Monday to Sunday (`weekStart`). It is kept as a list of `(from-Monday, target)` (kv `goal.week:<profileId>`, `2026-10-12:3;2026-11-02:4`). A week uses the latest entry dated on or before its Monday, and a week before the first entry uses the first entry's. Changing the target adds an entry from the current week (replacing one already dated this week), so earlier weeks are never re-scored.
- **Default**: with nothing stored the target is a pace already kept: the number of workouts a week reached in at least four of every five of the last 12 complete weeks that had at least one (the median was tried first and wiped out an existing streak, since half the weeks miss it), clamped to 1..7, and 3 with no such week. It is then stored dated from the first workout's week, so history is scored against the user's own usual pace and an existing streak does not fall to zero overnight.
- **Streak**: the run of met weeks counted back from the last complete week. The week in progress adds to it once met and never breaks it. A week met is `count >= target` (the streak code takes only a met / not-met list per week, so another kind of good week can use it).
- **Shield**: found by replaying from the first week. After every 8 consecutive genuinely met weeks one shield is banked, at most one held. A missed week with a shield held is forgiven: the shield is spent, the run is not broken, and the forgiven week counts neither toward the run's length nor toward the next 8. A miss with no shield breaks the run and the count toward the next shield.
- **Badges**: a streak ladder at 4, 13, 26 and 52 weeks (`streak-4` "1 month streak", `streak-13` "3 months", `streak-26` "6 months", `streak-52` "1 year") and a total-workouts ladder at 10, 50, 100, 250 and 500 (`workouts-10`, ...). A badge is earned the first time its goal is reached and stays earned; its date is the day the week that reached it was met, or the day of the nth workout.
- **Earned by a workout** (`earnedBy`): the target hit, a new badge or a banked shield, found by comparing the log with and without that workout. The summary celebrates each once (`reward.seen:<profileId>`).
- Also kept for the Trophies page: the longest gap between two workouts, workouts per weekday, and the old streak of weeks with any workout at all.

## Program

Decided 2026-10-12 (the user asked for "a streak for maintaining a program for months, with a 10% margin of adding or removing exercises, and rest days a part of it"); the code is `core/Program.kt`. Weekly programs only.

- **The plan**: seven slots, Monday first, each a training day (a template) or Rest. It is versioned: a list of versions, each from a Monday, and a week is scored by the latest version dated on or before its Monday; weeks before the first version are not scored. A training day stores a snapshot, not a reference: the template's name and id when saved and its distinct overall exercises at that time. Scoring reads only the snapshots and the workout rows, so editing, renaming or deleting a template later never re-scores a past week. Saving appends a version from the chosen Monday (one dated the same Monday is replaced). Kept in kv `program:<profileId>` as `from|day|day|...` (seven days) per version, versions joined by `;`, a day `label,templateId,exercise,...` (rest is empty), `\` escaping `\ | ; ,` and line breaks. Like `goal.week` and `reward.seen` it belongs to a profile (key suffix `:<profileId>`; the global keys of before were moved to the active profile once, and a deleted profile's keys are removed). **First plan** (`suggestedWeek`, the editor's pre-fill when none is saved): from the last 8 complete weeks, a weekday is a training day when trained in at least half of the weeks that had any workout; its template is the one named like the workout done most often that weekday (ties to the most recent), else the template overlapping that weekday's latest workout best provided it would match, else Rest. With no training day: the Current split's templates in `nextTemplate` order on Monday, Wednesday, Friday, Tuesday, Thursday, Saturday, else all Rest.
- **A workout matches a training day** over distinct overall exercises (`overallName`: the exercise's stored overall, else the library's group, else itself; a card split into runs by variation, or two cards of one exercise, counts once; a card with no set counts for nothing). With `kept` in both, `added` in the workout only, `removed` in the day only and `n` the day's count, it matches when `max(added, removed) <= max(1, ceil(0.10 * n))` and `kept >= ceil(n / 2)`. The 10% (`PROGRAM_MARGIN`) is the user's; the floor of one exercise (`PROGRAM_MIN_CHANGES`) and the half-kept guard (`PROGRAM_MIN_KEPT`) are ours, so a short day can lose or gain one and a one-exercise day never matches a different exercise. Sets, reps, weights and order do not matter.
- **A week is on program** when every training day of its version can be matched by a different workout of that Monday-to-Sunday week, on any day, in any order. The best assignment is found by trying them all (the most days matched, then the best overlap, then the most days on their own weekday), which only matters for what is shown. Extra workouts are ignored. A rest day never breaks a week; it is reported kept or trained for display only. The week in progress is never a miss: it is met once all its training days are matched, otherwise in progress. A day shows as hit (matched by a workout on its own day), moved (matched by another day's), missed (a past day not matched), upcoming, rest kept or rest trained.
- **Streak, shield and badges** are the weekly target's code fed with the program's met weeks (`replayStreak`, `WeekMet`): the same shield rule and the same ladder at 4, 13, 26 and 52 weeks, with their own badges `program-4` "1 month on program" and so on. A month is four weeks and a year 52 when a streak is said in words (`spanText`: "3 months, 1 week"), kept in step with the ladder so that no month count is shown before its badge is earned ("25 weeks" is 5 months, 5 weeks, not 6 months). The weekly target's "about N months" uses the same rule.
- **Backdating**: `firstFittingWeek` walks back from last week and gives the earliest Monday from which every week up to last week would have been on program for the plan being saved; the editor offers it as a start. Badges a new program has already earned are marked seen when it is saved, so none is celebrated late.
- **Today** (`todayPlan`): today's slot, whether its training day is already met, and the training days still to do in the order to offer them (today's, earlier ones of this week, later ones, else next week's first). Without a program the Workout tab and the "Up next" widget use `nextTemplate`, the old rotation (the Current split folder first, falling back to every template that has been used; never done first, else done longest ago).
- **A finished workout** (`programOutcome`): the day it met, how many exercises differed (`max(added, removed)`), whether it was the last to complete the week, and the program badges it earned; the summary says so once (`reward.seen`: badge ids, `pweek:<monday>`).

## Template values (decided 2026-10-12)

A template block keeps its planned set kinds and, optionally, per set a target: a weight (always kg, whatever the display unit) and reps (seconds for a timed hold). A left and right exercise keeps one target per pair, taken from the left set (the right when only it was ticked). A set with neither is no target; a template with none is stored as it was before values existed. Cardio sets carry no target, since their boxes are kilometres and minutes.

- **Where they come from**: the finish prompt's choices, "make from past workouts", "save as template", and "Create new template" (the ticked sets of that workout). The template editor does not edit them; shortening a block drops the targets of the sets removed and keeps the rest by index.
- **Where they are used**: starting a workout from a template, the empty boxes of set k (counting every set, warm-ups included, by position) show the template's target as their hint instead of last time's value. A progression scheme's targets win, and with a scheme the template's are not used. Left and right: both halves of pair k show target k.
- **An increase** (`isIncrease`, with a tolerance of 1e-9 kg): a ticked set beats the planned set at the same position when its weight is greater, or equal and its reps are greater; a missing weight or reps counts as 0. A planned set with no weight and no reps is beaten by any set that has either. A set with neither beats nothing, and a lower or equal set never overwrites.
- **Matching** (`valueIncreases`): each exercise of the finished workout that is also in the template is matched by name, the nth block of a name in the workout to the nth in the template; sets are matched by position among the block's sets (a left and right exercise by pair) and only when their kinds agree. Sets beyond the template's are not values. N is the number of increased sets.
- **The choices**: *Update values only* writes only the increased sets' targets and leaves the exercises and sets as they are (`withValuesUpdated`). *Update template and values* / *Update template* takes the finished workout's exercises, order, set kinds and supersets and keeps the planned targets of sets that stay, replacing one only when it was beaten; a set the template did not have (a new exercise, a set beyond its count, or of another kind) takes the workout's values (`withStructureUpdated`). *Create new template* saves the finished workout as it was, its ticked sets with their values, in the original's folder. *Keep original template* changes nothing. The prompt cannot be dismissed (2026-10-16, the user's word): a tap outside or Back does nothing, so one of the four choices is always made.
- **When it is asked**: the structure differs (`sameShape`: exercises, order, set kinds, supersets) or N is at least 1. The summary counts exercises added and removed and, for exercises in both, sets added and removed (`shapeChange`).
- **A valid unticked set** (`isValidSet`), for "Finish workout?": reps typed (kilometres and minutes both for cardio, seconds for a timed hold), and a weight typed unless the load is bodyweight, assisted or timed. Boxes showing only a hint are empty. Completing ticks them with what was typed; an empty weight box stays empty rather than taking its hint.

## Notes carry real signal

The 110 notes in the data are mostly not variations. They are pain reports (`Right shoulder pain`, `Sharp pain in left antecubital fossa`), form judgments (`Bad form till now ... Corrected by Allen`), limiting factors (`Fingers were the bottle neck`), and machine settings. Free text must stay first-class, quick to enter mid-workout, and visible the next time the exercise comes up. Structured versions of these (a "poor form" flag that keeps a set out of records, a pain log by body part) are listed as future work in `plan.md`, not part of the first version.

## Compared with last time (2026-10-16)

`compareWithLast` in `core/CompareLast.kt`. For each exercise of a workout (blocks of one exercise are one session), today's session is set against the latest session of that exercise dated before the workout's date (so an old workout compares with the one before it, not the newest). Sessions with no working set are skipped on either side; none earlier is "First time", listed and not counted. The set that stands for a session: normal and bodyweight, `bestSetOf` (fixed Epley, no rep limit); assisted, the least assistance then most reps; timed, the most seconds; cardio, the longest distance then the shortest time. Improved means a higher figure: Epley of the best set (reps when neither set has a weight), seconds, distance then less time, less assistance then more reps; equal is "same"; lower is "down". The words are the differences in weight and reps (seconds, km and seconds for timed and cardio). Left and right exercises compare the weaker side's best set in each session (`Session.forSide`), so a lagging side cannot hide behind the stronger.
