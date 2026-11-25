"""
Builds app/src/main/assets/exercise_info.json: how-to steps for Clean's built-in exercises, taken
from hasaneyldrm/exercises-dataset (instruction text is MIT; no media is read or copied).

    python -I build.py <exercises.json> <project root> [--review <file>] [--dry]

A library exercise gets text only when its name and the dataset's are the same set of words once
both are normalised (equipment included), directly or through one of Clean's aliases, or when the
pair is listed in overrides.txt. Everything else is left without text; --review writes the nearest
candidates for those to a file, to be read by a person and added to overrides.txt.
"""
import json
import re
import sys
from pathlib import Path

# Spellings that mean the same thing, applied to both sides before splitting into words.
PHRASES = [
    (r"\bv\. ?(\d)\b", r"v\1"),
    (r"\b(pull|push|sit|chin|step|press|muscle|get)[ -]?ups?\b", r"\1up"),
    (r"\b(pull|push|press)[ -]?downs?\b", r"\1down"),
    (r"\bpressdown\b", "pushdown"),
    (r"\bkick[ -]?backs?\b", "kickback"),
    (r"\bskull[ -]?crushers?\b", "skullcrusher"),
    (r"\bt[ -]bar\b", "tbar"),
    (r"\bez[ -]?(barbell|bar)\b", "ezbar"),
    (r"\btrap[ -]bar\b", "trapbar"),
    (r"\bbody ?weight\b", " "),
    (r"\bsmith machine\b", "smith"),
    (r"\bleverage machine\b", "machine"),
    (r"\bsled machine\b", "machine"),
    (r"\b(lever|sled)\b", "machine"),
    (r"\bolympic barbell\b", "barbell"),
    (r"\b(stability|exercise|swiss) ball\b", "stabilityball"),
    (r"\bmedicine ball\b", "medball"),
    (r"\bresistance band\b", "band"),
    (r"\b(one|single)[ -]arm\b", "onearm"),
    (r"\b(one|single)[ -]leg\b", "oneleg"),
    (r"\bstiff[ -]leg(ged)?\b", "stiffleg"),
    (r"\bstraight[ -]leg(ged)?\b", "straightleg"),
    (r"\bbent[ -]?over\b", "bentover"),
    (r"\bdead[ -]lift\b", "deadlift"),
    (r"\bfly(e|es)?\b|\bflies\b", "fly"),
    (r"\bcalves\b", "calf"),
    (r"\bfibre\b", "fiber"),
]
STOP = {"the", "with", "a", "an", "and", "of"}
EQUIPMENT = {
    "barbell", "dumbbell", "cable", "machine", "smith", "kettlebell", "band", "ezbar", "trapbar", "weighted",
    "assisted", "stabilityball", "medball", "suspension", "plate", "rope", "roller", "bosu", "landmine", "ring",
}


def words(text):
    """The set of normalised words in a name."""
    t = text.lower().replace("°", " degree ")
    for pattern, to in PHRASES:
        t = re.sub(pattern, to, t)
    out = set()
    for w in re.findall(r"[a-z0-9]+", t):
        if w in STOP:
            continue
        if len(w) > 3 and w.endswith("es") and w[-3] in "hx":
            w = w[:-2]
        elif len(w) > 3 and w.endswith("s") and not w.endswith("ss"):
            w = w[:-1]
        out.add(w)
    return frozenset(out)


def constants(path):
    """The triple-quoted tables of a Kotlin file, by constant name."""
    return dict(re.findall(r'const val (\w+) = """(.*?)"""', path.read_text(encoding="utf-8"), re.S))


def table_names(table):
    """Exercise names of a library table, built the way parse() in Library.kt builds them."""
    names = []
    for line in table.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        head = line.split("|")[0].strip()
        base, _, variants = head.rpartition(":")
        for variant in variants.split(","):
            variant = variant.strip()
            names.append(base.strip() if variant == "-" else "%s (%s)" % (base.strip(), variant))
    return names


def other_names(aliases, names):
    """Clean's other names for each library exercise, as OTHER_NAMES in Library.kt works them out."""
    known = set(names)
    out = {}
    for line in aliases.splitlines():
        line = line.strip()
        if not line:
            continue
        alias, _, canonical = line.partition(" = ")
        if not alias.startswith("*"):
            if canonical in known:
                out.setdefault(canonical, []).append(alias)
            continue
        other = alias[1:].strip()
        for name in names:
            if name == canonical:
                out.setdefault(name, []).append(other)
            elif name.startswith(canonical + " ("):
                out.setdefault(name, []).append(other + name[len(canonical):])
    return out


def read_overrides(path):
    """`Clean name = dataset id  # comment` lines; id `-` means "looked at, nothing fits"."""
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("  #")[0].strip()
        if not line or line.startswith("#"):
            continue
        name, _, ident = line.rpartition(" = ")
        out[name] = ident.strip()
    return out


def steps_of(entry):
    steps = (entry.get("instruction_steps") or {}).get("en") or []
    if not steps:
        steps = re.split(r"(?<=[.!?])\s+", (entry.get("instructions") or {}).get("en") or "")
    return [" ".join(s.split()) for s in steps if s and s.strip()]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dataset_path, root = Path(args[0]), Path(args[1])
    review_path = Path(sys.argv[sys.argv.index("--review") + 1]) if "--review" in sys.argv else None
    here = Path(__file__).resolve().parent

    core = root / "core/src/commonMain/kotlin/clean/core"
    library = constants(core / "Library.kt")
    hand = table_names(library["TABLE"])
    more = []
    for _, table in sorted(constants(core / "LibraryMore.kt").items()):
        more += table_names(table)
    names = list(dict.fromkeys(hand + more))
    aliases = other_names(library["ALIASES"], names)

    dataset = json.loads(dataset_path.read_text(encoding="utf-8"))
    by_id = {e["id"]: e for e in dataset}
    bags = {}  # set of words -> dataset entries
    entry_bag = {}
    for e in dataset:
        bag = words(e["name"]) | words(e["equipment"])
        entry_bag[e["id"]] = bag
        bags.setdefault(bag, []).append(e)

    overrides = read_overrides(here / "overrides.txt")
    unknown = [n for n in overrides if n not in set(names)]
    bad = [n for n, i in overrides.items() if i != "-" and i not in by_id]
    if unknown or bad:
        sys.exit("overrides.txt: not library names %r, not dataset ids %r" % (unknown, bad))

    chosen, how, left = {}, {}, []
    for name in names:
        if name in overrides:
            if overrides[name] != "-":
                chosen[name], how[name] = by_id[overrides[name]], "override"
            continue
        for kind, candidates in (("exact", [name]), ("alias", aliases.get(name, []))):
            hits = {e["id"]: e for c in candidates for e in bags.get(words(c), [])}
            if len(hits) == 1:
                chosen[name], how[name] = next(iter(hits.values())), kind
                break
            if hits:  # several dataset entries read the same: a person picks
                break
        else:
            pass
        if name not in chosen:
            left.append(name)

    if review_path:
        lines = []
        for name in left:
            mine = words(name)
            scored = []
            for e in dataset:
                theirs = entry_bag[e["id"]]
                score = len(mine & theirs) / len(mine | theirs)
                if score >= 0.34:
                    same = (mine & EQUIPMENT) == (theirs & EQUIPMENT)
                    scored.append((score + (0.2 if same else 0), same, e))
            scored.sort(key=lambda s: -s[0])
            lines.append(("H " if name in set(hand) else "M ") + name)
            for score, same, e in scored[:6]:
                lines.append("    %.2f %s %s %s [%s]" % (score, "EQ" if same else "  ", e["id"], e["name"], e["equipment"]))
        review_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    out = {}
    for name in names:
        if name in chosen:
            e = chosen[name]
            out[name] = {"s": steps_of(e), "m": e["media_id"]}
    text = json.dumps(out, ensure_ascii=False, separators=(",", ":"))
    if "--dry" not in sys.argv:
        target = root / "app/src/main/assets/exercise_info.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(text.encode("utf-8"))

    hand_set = set(hand)
    counts = {}
    for name, kind in how.items():
        counts[kind] = counts.get(kind, 0) + 1
    print("library exercises: %d (%d hand-written, %d generated)" % (len(names), len(hand_set), len(names) - len(hand_set)))
    print("with text: %d (%d hand-written, %d generated)" % (
        len(out), sum(1 for n in out if n in hand_set), sum(1 for n in out if n not in hand_set)))
    print("by", counts, "| looked at and left out:", sum(1 for v in overrides.values() if v == "-"))
    print("dataset entries used: %d of %d" % (len({e["id"] for e in chosen.values()}), len(dataset)))
    print("asset bytes:", len(text.encode("utf-8")))


if __name__ == "__main__":
    main()
