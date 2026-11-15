"""Writes core/src/main/kotlin/clean/core/OpenGymIds.kt: openGym's built-in exercise ids and the name Clean
gives each exercise. Only names are read from the dataset (facts); see README.md for how to run it."""
import collections, json, re, sys

dataset, root = sys.argv[1], sys.argv[2]
text = open(dataset, encoding="utf-8").read()
exercises = json.loads(text[text.index("["):text.rindex("]") + 1])


def table_names(path):
    """Library names the way Library.kt's parse() makes them: `Base: A, B` is `Base (A)` and `Base (B)`, `-` is `Base`."""
    out = []
    for line in open(path, encoding="utf-8").read().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "|" not in line or ":" not in line:
            continue
        head = line.split("|")[0]
        base, variants = head.rsplit(":", 1)
        for v in variants.split(","):
            v = v.strip()
            out.append(base.strip() if v == "-" else f"{base.strip()} ({v})")
    return out


def key(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


names = table_names(root + "/core/src/main/kotlin/clean/core/Library.kt") + table_names(root + "/core/src/main/kotlin/clean/core/LibraryMore.kt")
# base key -> [(equipment key, full name)]; a name without equipment has the equipment key "".
by_base = collections.defaultdict(list)
for full in names:
    m = re.match(r"^(.*) \(([^()]*)\)$", full)
    by_base[key(m.group(1) if m else full)].append((key(m.group(2)) if m else "", full))
    if m:
        by_base[key(full)].append(("", full))

EQUIPMENT = {  # openGym's equipment word -> the words Clean's tables use for it (keys)
    "body weight": [""], "dumbbell": ["dumbbell"], "barbell": ["barbell"], "cable": ["cable"], "band": ["band"],
    "resistance band": ["band"], "kettlebell": ["kettlebell"], "smith machine": ["smithmachine"],
    "weighted": ["weighted"], "stability ball": ["stabilityball"], "ez barbell": ["ezbar"],
    "assisted": ["assisted", "machine"], "leverage machine": ["machine"], "sled machine": ["machine", "sled"],
    "medicine ball": ["medicineball"], "rope": ["rope", "cable"], "olympic barbell": ["barbell"], "trap bar": ["trapbar"],
    "bosu ball": ["bosuball"], "roller": ["roller"], "wheel roller": ["wheel"],
}
LEADING = ["ez barbell", "olympic barbell", "barbell", "dumbbell", "cable", "band", "resistance band", "kettlebell", "lever", "sled", "smith", "weighted", "assisted", "stability ball", "medicine ball", "bosu ball", "rope", "body weight", "bodyweight", "exercise ball"]


def capital(name):
    return " ".join(w[:1].upper() + w[1:] for w in name.replace("-", " ").split(" "))


def lookup(base, equipment):
    wanted = EQUIPMENT.get(equipment, [key(equipment)])
    found = by_base.get(key(base), [])
    for w in wanted:
        for eq, full in found:
            if eq == w:
                return full
    return found[0][1] if len(found) == 1 else None


mapping, fallback = {}, []
for e in exercises:
    name = re.sub(r"\s+v\. \d+$", "", re.sub(r"\s*\((male|female)\)\s*$", "", e["n"]))
    tries = [name]
    for lead in LEADING:
        if name.startswith(lead + " "):
            tries.append(name[len(lead) + 1:])
    full = next((f for f in (lookup(t, e["eq"]) for t in tries) if f), None)
    if full is None:
        full = capital(e["n"])
        fallback.append(e["n"])
    mapping[e["id"]] = full

sys.stderr.write(f"{len(mapping)} ids, {len(mapping) - len(fallback)} matched to a library name, {len(fallback)} kept as the dataset's own name\n")
if "--review" in sys.argv:
    sys.stderr.write("\n".join(fallback) + "\n")

def kotlin(s):
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\$")

with open(root + "/core/src/main/kotlin/clean/core/OpenGymIds.kt", "w", encoding="utf-8", newline="\n") as out:
    out.write("package clean.core\n\n/*\n * openGym names its built-in exercises by id only. This table (id to the name Clean uses) was made by\n * tools/opengym_ids/build.py from the names in the open dataset openGym ships; a name that matches no library\n * exercise is the dataset's own, capitalised. Names are facts; nothing else is taken.\n */\n")
    out.write("internal val OPENGYM_IDS: Map<String, String> = mapOf(\n")
    for id_, full in sorted(mapping.items()):
        out.write(f"    \"{id_}\" to \"{kotlin(full)}\",\n")
    out.write(")\n")
