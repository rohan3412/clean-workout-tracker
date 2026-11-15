# Exercise how-to text

`build.py` writes `app/src/main/assets/exercise_info.json`: the steps for each built-in exercise,
taken from [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset)
(instruction text MIT, see `NOTICE.md`). It reads `core`'s library tables and `overrides.txt`.

```bash
curl -L -o /tmp/ds/exercises.json https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/data/exercises.json
python -I tools/exercise_info/build.py /tmp/ds/exercises.json . --review /tmp/review.txt
```

Keep the dataset (17 MB) outside the project. Only text is taken: the dataset's images and GIFs are
Gym visual's and are not covered by the MIT licence. A name is paired automatically only when it is
word for word the dataset's; `--review` lists the nearest candidates for the rest, to be read and
added to `overrides.txt` by hand (`-` records "looked at, nothing fits"). Run it again after
`Library.kt` or `LibraryMore.kt` changes: the file is keyed by exact library name.
