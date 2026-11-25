# openGym exercise ids

openGym's backup names a built-in exercise by id only. `build.py` writes `core/src/commonMain/kotlin/clean/core/OpenGymIds.kt`,
the table from id to the name Clean uses. It reads the names in openGym's exercise list (the open dataset
`LibraryMore.kt` was generated from; names are facts) and the library tables in `Library.kt` and `LibraryMore.kt`.

```bash
python -I tools/opengym_ids/build.py path/to/exercises-data.js . [--review]
```

Keep the dataset outside the project. The script finds each id's library name by comparing names without case, hyphens
or punctuation, after moving a leading equipment word (`band`, `dumbbell`, ...) out of the name the way the library did,
and picks the equipment the library lists for openGym's equipment word. About 90 percent (1188 of 1324) match; the rest
(mostly stretches, the `(male)` and `(female)` variants and `v. 2` duplicates the library left out) keep the dataset's own
name with each word capitalised, which Clean's alias matching can still pair at display time. `--review` lists those.
Run it again after `Library.kt` or `LibraryMore.kt` change.
