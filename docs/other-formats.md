# Other apps' formats

Facts about the files other apps write, for Clean's importers and exporters. Gathered on 2026-10-12
from how GymMane (github.com/InlitX/GymMane, GPL-3.0) recognises and reads them, and from the apps'
own exports as publicly described. Column names, set-type words and date shapes are facts and may be
used; **no code is taken from GymMane or any other app** (the same rule as for Strong). None of these
has been checked against a real export file yet: where a sample turns up, test against it and correct
this page.

Everything imported becomes rows in Clean's own format (`data-format.md`): the byte-exact round
trip is a promise for Strong's files only. An import never trims or rewrites what it stores beyond
the conversion itself, shows a summary first, writes nothing until confirmed, and adds nothing when
the same file is imported twice.

## Recognising a file (header names lower-cased and trimmed; `,` or `;` separated; quotes doubled)

| Source | Recognised by |
|---|---|
| Strong | `exercise name` and `set order` (already handled exactly; not through this path) |
| Hevy | `exercise_title` and `start_time` |
| Lyfta | `exercise`, `set type`, `title`, and `rir/rpe` or a column starting `recordlevel` |
| Fitbod | `exercise`, `reps`, `iswarmup` |
| GymMane | `weight_kg` and (`est_1rm_kg` or `volume_kg`) |
| FitNotes | `exercise`, `category` and a weight column |
| openGym | not CSV: a JSON object with `workouts` and `routines` arrays |
| Any other CSV | a date column, an exercise column and a reps column (names below) |

## Columns

**Hevy** (`workouts.csv`): `title`, `start_time`, `end_time`, `description`, `exercise_title`,
`superset_id`, `exercise_notes`, `set_index`, `set_type`, `weight_kg` (or `weight_lbs`), `reps`,
`distance_km` (or `distance_miles`), `duration_seconds`, `rpe`. Dates read `12 Oct 2026, 18:00`
(day, three-letter English month, year, 24-hour time). `set_type` is `normal`, `warmup`, `dropset` or
`failure`. One row per set; a workout is the rows sharing `title` and `start_time`; its length is
`end_time` minus `start_time`.

**Lyfta**: `Title`, `Date`, `Duration`, `Exercise`, `Weight`, `Reps`, `Set Type`, and effort in `RIR/RPE`.
The weight column carries no unit, so the user is asked. Duration is text (`1:05:00`, `65:00`, or
`1h 5m`).

**Fitbod**: `Date`, `Exercise`, `Reps`, `Weight(kg)` (or `Weight(lbs)`), `isWarmup` (`true` / `false`).

**FitNotes** (CSV export): `Date`, `Exercise`, `Category`, `Weight (kg)` (or `Weight (lbs)`, or a
plain `Weight` with no unit), `Reps`, and where present `Distance`, `Distance Unit`, `Time`, `Comment`.
One row per set, no workout name, no duration: a day's rows are one workout. FitNotes' own backup is
an SQLite file (tables `training_log` with `exercise_id`, `metric_weight`, `reps`, `date`; `exercise`;
a settings row for the unit); not read by Clean.

**GymMane** (CSV export): `date`, `exercise`, `muscle`, `set`, `reps`, `weight_kg`, `volume_kg`,
`est_1rm_kg`, `distance_km`, `duration_s`, `rpe`.

**openGym** (JSON backup): `unit` (`kg` or `lb`), `customEx` (`[{id, n}]`: the user's own exercises
and their names), `workouts` (`[{d, start, end, entries}]`: `d` a date, `start` and `end` epoch
milliseconds), each entry `{id, sets}` with sets `{r, w, done, phase, warmup}` (`r` reps, `w` weight
in `unit`, only `done` sets count, `phase == "warmup"` or `warmup == true` is a warm-up), and
`bodyweight` (`[{d, w}]`). Built-in exercises are named by id only; the names come from openGym's own
exercise list (the dataset Clean's `LibraryMore.kt` was generated from).

**Any other CSV**: date in a column named `date`, `fecha`, `datum`, `data`, `day`, `dia`, `timestamp`,
`start_time` or `time`; exercise in `exercise`, `exercise name`, `exercise_name`, `ejercicio`,
`übung`, `esercizio`, `exercice` or `name`; reps in `reps`, `rep`, `repetitions`, …; weight in
`weight_kg` / `weight (kg)` / `weight kg`, `weight_lbs` / `weight (lbs)` / `weight (lb)`, or a plain
`weight` / `peso` / `gewicht` / `poids` (unit asked); a workout name in `workout`, `routine`, `plan`
or `training` when present.

## Reading rules common to all

- Weight: a kg column wins, then a lb column (÷ 2.2046226218), then the plain column in the unit the
  user chose. Decimal commas are accepted.
- Dates: ISO (`2026-10-12 18:00:00`, `2026-10-12T18:00:00`) or `12 Oct 2026, 18:00`.
- Effort: `rpe` 1 to 10 as it is; `rir` 0 to 9 becomes RPE `10 − rir`; a mixed `rir/rpe` column is
  read as RPE when 6 or more, else as RIR. Clean stores RPE 6 to 10 in half steps and drops the rest.
- Set kinds map to Strong's: warm-up `W`, failure `F`, drop set `D`, everything else a numbered set.
- Rows with no reps and no time or distance are skipped.

## Writing

- **Hevy CSV**, **FitNotes CSV**, **GymMane-style plain CSV**: the columns above, one row per set,
  written from Clean's rows.
- **JSON**: Clean's own, every field of every row, for people who want to script against it.
- **`.fit`** (one workout, what Strava's upload page takes): the public FIT file format: a 14-byte
  header, definition and data messages, CRC-16 at the end. Messages needed for a strength workout:
  `file_id` (global 0: type 4 = activity, manufacturer, product, time created), `event` (21: timer
  start and stop), `record` or none, `lap` (19), `session` (18: sport 10 = training, sub-sport 20 =
  strength training, start time, elapsed and timer time), `activity` (34). FIT timestamps are seconds
  since 1989-12-31 00:00 UTC. Strava's app cannot import files; the upload is done in a browser.
- **Text**: one workout as lines a person can read and paste.

## What Clean does (built 2026-10-12)

Every source below was **built from description and is untested against a real file**: Hevy, Lyfta,
Fitbod, FitNotes, GymMane, openGym, the generic CSV, and all the exports. The tests use small
hand-written files. Nothing found in this page's descriptions was wrong; where a shape was not
given, the plainest guess is listed here so it can be corrected when a real file turns up.

**Reading** (`core/Import.kt`, `readImportFile` first for zips):
- A zip is accepted when exactly one file in it is recognised; Strong's measurements zip is tried first
  and read as before. Detection goes in the order of the table at the top; a header read stops at the
  first record, so a big file is not read twice.
- Grouping: Hevy by `title` + `start_time`; every other CSV by day (+ the workout column when there
  is one). A group's start is the earliest clock time any of its rows has, else 00:00; a start that
  is already taken gets +1 minute until it is unique. A workout without a name column is called
  "Workout". Duration: Hevy `end_time` minus `start_time`, Lyfta/other `Duration` column, else 0.
- Notes: Hevy `exercise_notes`, FitNotes `Comment` and a generic `notes` column become a note row
  opening the block (repeated text in one block is kept once). Hevy `description` and `superset_id`
  are dropped.
- Left out, with the reason counted for the summary: unreadable date, no exercise name, no reps and
  no time and no distance (a reps of 0 counts as none). openGym: sets not ticked, exercises whose id is
  unknown, workouts without a date.
- Weights: a kg column, else a lb column (x 0.45359237, four decimals), else the plain column in the
  unit the user chose; a missing weight stays missing (not 0.0). Distance: `distance_km`,
  `distance_miles`, or FitNotes `Distance` with `Distance Unit` (km unless it says miles or metres).
  Time: seconds, `H:MM:SS`, `MM:SS` or `1h 5m`.
- RPE is kept in half steps from 6 to 10; anything else is dropped. RIR becomes `10 - RIR`.
- openGym: `d` as a date, `start`/`end` as epoch milliseconds in the phone's zone, `w` in `unit`.
  The id table (`OpenGymIds.kt`) comes from `tools/opengym_ids`: it matches each id's dataset name
  to the Clean library name that `LibraryMore.kt` gives it (about 90 percent); the rest keep the
  dataset's own name, capitalised. Weigh-ins are dated 00:00 on their day.
- Duplicates: a workout whose start date-time is already stored is skipped whole (the existing rule for
  a Strong file); a weigh-in is skipped when its day already has one.

**Writing** (`core/Export.kt`, `core/Fit.kt`) and what each cannot carry:
- Hevy CSV: all fields quoted, `d MMM yyyy, HH:mm` dates (so seconds of the start are lost),
  `set_index` from 0, no `superset_id` or `description`. Left and right sets come out as ordinary sets.
- FitNotes CSV: one row per set, `Category` empty, time as `HH:MM:SS`, distance in km. No set kinds,
  RPE, workout names or durations, so warm-ups arrive as ordinary sets; a day's workouts merge.
- Plain CSV: GymMane's columns plus `workout` at the end; `set` is the position in the exercise,
  `est_1rm_kg` is Clean's Epley (up to 12 reps); set kinds are not carried.
- JSON: `{"format": "clean-rows", "version": 1, "rows": [...]}`, every row field as the stored text.
- FIT: `file_id`, two `event`s (timer start and stop_all), `lap`, `session` (sport 10, sub-sport 20),
  `activity`; manufacturer 255 (development); no per-set data, no name. The profile version in the
  header is 21.32.
- Text: name, date and length, then each exercise with its notes and numbered sets (`W`, `F`, `D` for
  the other kinds).
