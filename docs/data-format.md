# Data format

Everything here was measured from the real export in `original/` (141 workouts, 2405 data rows, 20 Nov 2025 to 7 Oct 2026). Where something is inferred and not proven, it says so.

Other apps' files (Hevy, FitNotes, Lyfta, ...) are read and written by a separate path described in `other-formats.md`; they become rows in this format, and the exact round trip below is a promise for Strong files only.

The rule that governs this whole file: **import the Strong export, export it again with no edits, and the output is byte-identical to the input.** That is the test for "maintain the CSV format".

## Workout file

Filename pattern: `strong<digits>.csv`.

### Encoding

- UTF-8, no BOM, LF line endings, newline after the last row.
- Separator is `;`. Every field is wrapped in `"`, including empty ones (`""`).
- The sample has no embedded quotes, semicolons, newlines, or non-ASCII characters, so Strong's escaping is **unknown**. Use standard CSV escaping (double the quote) and flag it as unverified.
- Whitespace inside fields is kept exactly. Real values have trailing spaces (`"Wide grip "`, `"Mixed From Notion "`). Never trim on import.

### Columns

Header, exactly:

```
"Workout #";"Date";"Workout Name";"Duration (sec)";"Exercise Name";"Set Order";"Weight (kg)";"Reps";"RPE";"Distance (meters)";"Seconds";"Notes";"Workout Notes"
```

| Column | Format | Notes |
|---|---|---|
| Workout # | integer | 1-based, ascending through the file. |
| Date | `YYYY-MM-DD HH:MM:SS` | Workout start, local time, no timezone. Repeated on every row of the workout. Rows are in ascending date order. |
| Workout Name | text | Repeated on every row. |
| Duration (sec) | integer | Whole workout. Repeated on every row. Values as low as 18 exist. |
| Exercise Name | text | Strong's identity for an exercise. Convention is `Name (Equipment)`. |
| Set Order | see below | |
| Weight (kg) | decimal or empty | Always at least one decimal: `10.0`, `7.5`, `3.75`, `54.4311`. `0.0` and empty both occur and are different. |
| Reps | integer or empty | `0` occurs once (a failed attempt logged as an `F` set). |
| RPE | empty in all 2405 rows | Format unverified. Strong allows 6 to 10 in half steps. |
| Distance (meters) | decimal or empty | `9450.0` |
| Seconds | decimal or empty | `59.0`, `120.0` |
| Notes | text | Only ever filled on `Note` rows. |
| Workout Notes | empty in all rows of the real export | Format unverified. Clean writes the workout's one-line note here, on every row of the workout (as the other workout-level columns), in the same quoting as every field (all fields quoted, quotes doubled, so a comma, quote or newline in a note stays inside its field). Read as the first non-empty value of the workout (`Workout.notes`). A workout with no note is written exactly as before. Strong has no workout note, so it presumably ignores the field and imports every exercise. |

One row is one event inside a workout. There is no workout-level row; workout fields are denormalised onto every row.

### Set Order

| Value | Count | Meaning |
|---|---|---|
| `1`..`9` | 821 | Normal working set. |
| `F` | 647 | Set taken to failure. |
| `W` | 381 | Warm-up set. |
| `D` | 263 | Drop set. |
| `Rest Timer` | 169 | Not a set. `Seconds` holds the rest length (`120.0` in 164 rows; `300.0`, `600.0` and `30.0` also occur). Follows the set it belongs to. Only workouts 38 to 66 have any. |
| `Note` | 124 | Not a set. `Notes` holds the text (14 of them are empty). All other value columns are empty. |

Numbering rule (inferred, 820 of 821 rows fit): a numbered set's value is its position among the non-warm-up sets of that exercise block, so `F` and `D` sets use up a number without showing it. Real sequences: `W F F D`, `W 1 F D`, `W F F 3`, `Note W F 2`. The one row that breaks the rule is in workout 108: Lat Pulldown runs `W F Note 1`, where the rule expects `2`. Adding "a `Note` row restarts the count" makes all 821 rows fit, which supports reading a note that follows sets as the start of a second copy of the same exercise, added straight after the first. Strong's own history screen confirms the reading (user's screenshot, 2026-10-08): workout 140's Iso-Lateral Row, which has a note after two sets, is shown there as two cards, `2 x` and `1 x`. So **a note that follows sets starts a new card of the same exercise**, and Clean splits blocks there. The numbering restart still rests on the single row in workout 108, so **store the Set Order string as imported**; only generate numbers for sets created in Clean.

`W`, `D` and `F` are mutually exclusive in this format. A drop set taken to failure can only be one of them.

### Structure that the columns do not show

- An exercise block is a run of consecutive rows with the same Exercise Name. Three workouts contain the same exercise in two separate blocks, so block order is data and must be preserved.
- A `Note` row is positional. 114 of 124 open their block. Of the other 10, 8 are a second or third note stacked at the top of a block (five blocks open with two or three notes in a row), and only 2 come after sets and describe the sets that follow them (workouts 108 and 140). Workout 140 shows this: Iso-Lateral Row has a note `Wide grip`, two sets, a note `Neutral`, one set.
- Supersets are not represented at all.

### What Strong shows that the file does not hold

Checked against the user's screenshots of Strong's history screen (2026-10-08), for workouts 140 and 141:

- **Set counts leave out warm-ups.** `W F 2` is shown as `2 x`.
- **Volume** is weight x reps over the non-warm-up sets, and for a bodyweight exercise the lifter's **latest** logged bodyweight is added to the weight (not the bodyweight on the day). With 73.3 kg this reproduces Strong's 6700 kg and 6597 kg exactly. Clean deliberately differs (user's decision, 2026-10-08): it uses the weigh-in closest to the workout, within 7 days (`domain.md`), so workout 140 reads 6604 kg. Strong gives assisted exercises no volume or records at all; Clean does.
- **Bodyweight exercises show the weight as `+20 kg`.** Which exercises are bodyweight or assisted is a property of the exercise in Strong and is not in the CSV; in Clean it is the exercise's load type.
- **Strong's PRs** (user's screenshot of workout 141's detail screen, 2026-10-08). Strong marks a set with badges `1RM`, `Weight` and `Vol.` and its PR count is the number of badges (8 for workout 141). Its 1RM column fits `floor(weight x (1 + (reps - 1) / 30))` on every set shown, 14 of 14: 30 x 10 is 39, 90 x 14 is 128, 20 x 20 is 32. So Strong uses Epley on reps minus one, with no upper limit on reps, and shows it for warm-ups too. Clean keeps its own rule (`domain.md`: `weight x (1 + reps / 30)`, nothing above 12 reps), so its 1RM figures are slightly higher and its PRs differ: it gives workout 141 four where Strong gives eight. Clean marks the same three kinds on the set that set them and calls them PRs.
- **Templates are not exported** by Strong at all.

### Which value columns a set uses

| Weight | Reps | Seconds | Distance | Rows | Example |
|---|---|---|---|---|---|
| yes | yes | | | 2100 | Most lifts. Bodyweight lifts use `0.0`. |
| | yes | | | 6 | Reps only. |
| yes | yes | yes | | 3 | See below. |
| | | yes | yes | 2 | Cycling, Walking. |
| | | yes | | 1 | Plank. |

The three rows with weight, reps and seconds are consecutive `F` sets of one block: Incline Chest Press (Machine) in workout 66. Each has `Seconds` `30.0`. The set before them is followed by the file's only `30.0` rest timer, and the exercise's other 64 sets have no seconds. So the value looks like that 30-second timer leaking onto the later set rows, not a timed set (inferred; workout 66 is also the last workout with rest timers at all). Keep the value so the row exports unchanged, and ignore `Seconds` on a set that has weight and reps.

### Things the numbers do not say

- `Pull Up (Assisted)`: weight is the assistance, so a lower number is a harder set.
- `Pull Up`: weight is added load. `0.0` means bodyweight only.
- Dumbbell lifts are logged per dumbbell (Bicep Curl at `7.5`).
- `54.4311` is 120 lb, so at least one set was entered in pounds. Storage is always kg.

## Measurements file

Filename pattern: `strong_measurements_YYYY-MM-DD.zip`, holding 18 CSVs:

`weight`, `body_fat_percentage`, `caloric_intake`, `neck`, `shoulders`, `chest`, `left_bicep`, `right_bicep`, `left_forearm`, `right_forearm`, `upper_abs`, `waist`, `lower_abs`, `hips`, `left_thigh`, `right_thigh`, `left_calf`, `right_calf`.

Each one:

```
"Date";"Weight (kg)"
"2026-10-07 19:06:47";73.30000305175781
"2026-10-05 19:48:08";73.5
```

- Header fields are quoted, the date is quoted, **the value is not**.
- Rows run newest first, the opposite of the workout file.
- Headers are `Weight (kg)`, `Body fat percentage`, `Caloric intake`, and `<Part> (cm)` for the rest.
- Values carry float noise (`73.30000305175781`, `28.999999999999996`) and whole numbers keep a decimal (`33.0`). Keep the original text of imported values so they export unchanged; round only for display.
- A file with no entries is just the header line.
- Each file is UTF-8 without BOM, LF line endings, newline after the last row.
- The zip holds the files in the order listed above, deflated at the default level, each entry stamped with the export time, in the layout Java's `ZipOutputStream` writes. Writing the same files with that class and the same timestamp is expected to reproduce the archive byte for byte (the compressed streams were confirmed identical with zlib; the whole archive is checked by the round-trip test).

## Carrying Clean's extra data

The Strong CSV has no place for exercise grouping, profiles, height, or unit choice. The user decided that variations are separate exercises (`plan.md`, `domain.md`), which settles most of this:

1. **Each set row carries its variation's own exercise name.** A variation is an exercise, so nothing new is needed in the CSV. The suggested naming is an extra trailing parenthesis group, `Lat Pulldown (Machine) (Wide Grip)`, matching names already in the data such as `Lat Row (Single Arm) (Cable) (Kneeling)` and `Oblique Crunch (Machine) (Left)`, but the name is whatever the user typed.
2. **A card that switches variation exports as consecutive blocks**, one per run of the same exercise name. On import, consecutive blocks whose exercises belong to the same overall exercise are shown as one card. For sets created in Clean, set numbers follow the Strong rule within each exported block.
3. **Never derive grouping from names.** `Hammer Curl (Preacher Curl Machine with Rope)` and `Triceps Pushdown (Cable - Straight Bar)` show the parentheses are free text. Which exercises belong to which overall exercise is stored explicitly. A plain CSV import brings in ungrouped exercises.
4. **Everything else goes in a full backup**, a single zip with the Strong-format CSVs plus one JSON file for what they cannot hold: profiles, unit choice, height, overall exercises with their members and defaults, exercise settings. The CSVs inside it stay readable by anything that reads Strong exports.

Consequence to accept: once old sets are moved to a variation, those rows export under the variation's name and no longer match the original Strong export. Byte-identical round trip is guaranteed for untouched data only.

All weights in the CSV are kg whatever unit the profile displays.

## What Clean writes

Choices made while building the first app (2026-10-08); change them here if they change in code.

- **A workout's identity is its start time**, the `Date` string to the second. Import skips workouts whose start time is already in the profile, which is what makes importing the same file twice add nothing.
- **Workout # is regenerated** as 1, 2, 3... in date order after every import, save and delete. Strong's files already number that way, so imported rows are not touched.
- **Rows are exported in date order**, then in the order they were written. An older file imported after newer workouts were logged still exports in order.
- **Set Order for sets logged in Clean** follows the inferred Strong rule, with the count restarting after a note. Since 2026-11-02 (the user's words: "drop set should not count as a numbered set, like warm ups") a `D` set no longer uses up a number in what Clean writes (`SetType.numbered`, `setLabels` in `core/Log.kt`); `F` still does. Imported rows keep their Set Order as stored; the screens (a workout's page, the exercise's History, the logging screen) show the numbers by the new rule whatever is stored.
- **`Rest Timer` rows are written only when the user asks** (Settings, Data, "Write rest timers"; off by default, since Strong's own export stopped having them after workout 66). When on, a set that started a rest is followed by a row with `Set Order` `Rest Timer` and the planned length in `Seconds` (`120.0`). Imported ones are always kept and exported.
- **Weights typed in lb are stored as kg at four decimals** (120 lb is `54.4311`). Weights typed in kg are stored as typed, with at least one decimal.
- **New measurement values are written as 32-bit floats widened to text**, the way Strong does (73.3 becomes `73.30000305175781`), so Clean's entries look like Strong's.
- **Duration** is finish time minus start time in whole seconds.
- **Timed sets** are written with `Seconds` and, if a weight was entered, `Weight (kg)`; Reps stays empty. **Cardio sets** are written with `Distance (meters)` and `Seconds`, entered in the app as kilometres and minutes. This matches the Plank, Cycling and Walking rows in the real export.
- **Templates hold values** (changed 2026-10-12). Strong's end-of-workout prompt offers "Update values only", "Update template and values" and "Keep original template" (user's screenshot, 2026-10-08), because its templates store a weight and reps per set; the user then asked for the same flow in Clean (2026-10-12). A Clean template keeps, per planned set, an optional target of weight (kg) and reps (see the templates file below and `domain.md`, Template values). Clean's prompt offers Update values only, Update template and values (or Update template), Keep original template and, last, Create new template (the user's addition, which leaves the original alone). It appears when the exercises, their order or the sets done differ from the template, or a set's values went up.
- **Sticky notes** (a note pinned to an exercise and shown on its card in every workout) are stored on the exercise and are not written to the CSV. Strong appears to export its own pinned notes as extra `Note` rows at the top of a block, which is the likely reason some blocks open with two or three notes; Clean reads those as ordinary notes.
- RPE is written as typed on the number pad: `6` to `10` in half steps (`8`, `8.5`), empty when not given. Strong's own spelling is unverified, since the real export has none; imported values are kept exactly. Workout Notes holds the workout's note when the user wrote one (Active screen or Summary), else it is written empty.

## The left and right marker (2026-10-12)

The one addition Clean makes to the rows. A block logged as left and right opens with a `Note` row whose `Notes` text is exactly `Left, then right` (`SIDES_NOTE`), every other field as in any note row; its sets follow as ordinary rows, read two at a time as left then right (docs/left-right.md). Strong shows it as an exercise note and any reader of the format accepts it. It is written only for blocks logged as left and right, so an imported Strong file never contains it and still round-trips byte for byte; Clean hides it from the user's notes.

When the block's rounds are not all left-then-right (a round with only one half done, or one done right first, 2026-10-13) the note spells the layout out, one token per round in row order, separated by single spaces: `Left, then right: RL RL L-`. `LR` is a round whose left set row comes first and its right row second, `RL` a round whose right row comes first and left second, `L-` and `R-` a lone left or right set (one row). A reader uses the layout for every set when its rows add up to the number of the block's sets, and otherwise ignores it and reads two at a time. The plain note is written whenever every round is `LR` (with at most a lone `L-` as the last round), so those files are byte-identical to before. The earlier spelling, one letter per stored set (`Left, then right: L R R L R`), is still read as before (the old rule: a left set opens a round, a right set completes it) and is never written again; a marker whose tokens are all one letter is that spelling, all two characters the new one, a mix is an ordinary note. To the CSV the marker is only a note row's text, so a file with any marker parses and serialises byte-identical. Both forms are hidden from the user's notes (`isSidesMarker`). A finished block can be marked or unmarked afterwards: that inserts or removes this one row (`withSidesMarker`, `Repo.markSides`), and marking then unmarking gives the same file byte for byte.

## Renaming an exercise and weigh-ins edited in the app (2026-10-12)

Nothing in the formats changes. A rename or merge (`domain.md`, Renaming and merging an exercise) only changes the text in the `Exercise Name` column of the rows named exactly so, and an undo puts that text back; the file stays byte-identical for everything not renamed, and identical to before the rename after an undo. A weigh-in added with a date of the user's choosing, or edited, is written like any new entry (`Measurements.newText`, a 32-bit float widened to text, pounds converted to kg first); an entry whose value was not edited keeps its text exactly even when its date is changed, and every untouched entry is written as imported. New entries still sort newest first by date.

## The backup zip (2026-10-12)

Settings, Data, "Back up everything" writes one zip; it is Clean's own and nothing else reads it. It is not an export format: the Strong CSV stays the exact one, and a backup is not written to be read by other apps.

- `clean-backup.json`: `{"format":"clean-backup","version":1,"db":9,"created":"2026-10-12T10:30:00","app":"1.0"}`. `db` is Room's schema version (`DB_VERSION`).
- `clean.db`: the app's SQLite file as it is on the phone, copied after `PRAGMA wal_checkpoint(TRUNCATE)`. Because the file itself is the backup, every table (rows as text, measurements, settings, templates, kv with every option, program, home cards, rename log and the workout in progress) is in it unchanged, and a restore leaves every stored value byte-identical. An older backup is brought up to date by Room's migrations when the restored file is first opened.
- `files/...`: from the app's `filesDir`, only the imported font (`files/font-*`), `files/animations/` (when the dialog's switch is on), and `files/photos/` and `files/places/` (kept for the features that will use them; absent now).

Restore rules: the zip is copied to the cache and inspected without changing anything; a missing or wrong manifest, a `db` or `version` above what this app knows ("made by a newer version of Clean") or a missing or unreadable `clean.db` is refused with a sentence. After a confirm, `clean.db` and the allowed `files/` entries are extracted into a staging folder in the cache (an entry that is not the manifest, `clean.db` or one of those paths, or whose normalised path leaves the staging folder, is ignored), and only then are the database closed, its `-wal` / `-shm` files removed and the files swapped in: the phone's font, photos and places are replaced by the backup's (or removed when it has none), animations only when the backup carries them. The app then restarts in a new process. Nothing is written to Health Connect. Delete everything removes the database and those same files and clears the cache, then restarts.

## Progress photos (2026-10-12)

Not in any CSV and not in the database: one JPEG per photo under the app's private folder, `files/photos/<profileId>/<yyyyMMdd-HHmmss>_<pose>.jpg`, e.g. `20261012-093000_front.jpg`. The pose is `front`, `side` or `back`. The file name is the whole record: the moment (local time, to the second) and the pose are read from it, and changing either renames the file; when the new name is taken a second is added until it is free. A file in the folder whose name is not exactly that (an unknown pose, no pose, a stamp that is not a moment, another extension) is ignored, never listed and never deleted by the app except with the profile.

A photo comes in decoded, turned upright by its EXIF orientation, scaled so that its longer side is at most 2048 px, and written as JPEG at quality 88 with no EXIF at all (no location is kept). A photo chosen from the phone is dated by its EXIF `DateTimeOriginal` (`yyyy:MM:dd HH:mm:ss`) when it has a valid one, else now; one taken with the camera is dated now. A deleted photo is moved to `cache/photo-removed/` for ten seconds so that the Undo can bring it back, then removed.

The backup zip carries the folder as `files/photos/<profileId>/<name>.jpg`: restore accepts any path of two or more parts under `photos/` (checked against the code in `Backup.apply`: a nested `photos/<id>/…` entry is extracted into the staging folder and swapped in whole), and the phone's own photos are replaced by the backup's (or removed when it has none). Deleting a profile removes its photo folder; Delete everything removes `photos/`. Profile ids are those of the database being restored, so photos follow their profile.

## Templates file

Clean's own format, since Strong has none. One JSON file holds every template of a profile; set types are Strong's letters, with an empty string for a normal set:

```json
{"clean_templates": 1, "templates": [{"name": "Pull", "exercises": [{"exercise": "Pull Up", "sets": ["W", "", "F"], "targets": [[40.0, 8], null, [82.5, 5]]}]}]}
```

`targets` is optional, on a block: one entry per set by position, `[weightKg, reps]` with `null` for a part that is missing, and `null` for a set with no values (a shorter list means none for the rest; a block with none has no key, and older files import with none). The weight is always kilograms, whatever the unit shown. A left and right exercise has one set and one target per pair.

On import, an exercise name the profile already has is used as it is. For any other name the user chooses, per exercise, to create it or to use one of their own exercises instead. Nothing is matched by similarity.
