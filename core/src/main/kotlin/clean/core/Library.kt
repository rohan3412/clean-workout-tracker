package clean.core

/** A built-in exercise: Clean's own definition of what it trains and how its weight is logged. */
class LibraryExercise(val name: String, val muscles: MuscleUse, val loadType: LoadType)

private val CODES = mapOf(
    "TP" to Muscle.TRAPS, "SH" to Muscle.SHOULDERS, "CH" to Muscle.CHEST, "LT" to Muscle.LATS, "UB" to Muscle.UPPER_BACK,
    "BI" to Muscle.BICEPS, "TR" to Muscle.TRICEPS, "FA" to Muscle.FOREARMS, "AB" to Muscle.ABS, "OB" to Muscle.OBLIQUES,
    "LB" to Muscle.LOWER_BACK, "GL" to Muscle.GLUTES, "QD" to Muscle.QUADS, "HM" to Muscle.HAMSTRINGS, "AD" to Muscle.ADDUCTORS,
    "CV" to Muscle.CALVES, "NK" to Muscle.NECK, "SE" to Muscle.SERRATUS, "HF" to Muscle.HIP_FLEXORS, "TI" to Muscle.TIBIALIS, "RC" to Muscle.ROTATOR_CUFF,
)

/*
 * One line per movement: `Name: variants | main muscles | helping muscles | flags`.
 * Helping muscles are supporting (they move the load: half a set each) unless marked `~`, which is
 * minor (they grip, brace or steady: a quarter of a set).
 * Each variant becomes `Name (Variant)`; `-` is the movement with no suffix. Names follow the
 * `Name (Equipment)` convention of the user's existing data so the two line up.
 * Flag `bw`: the logged weight is added to bodyweight. A variant called `Assisted` is always an
 * assisted lift and one called `Bodyweight` always a bodyweight lift.
 * Flag `time`: logged as seconds (plus a weight where one is held). Flag `cardio`: logged as distance and time.
 * Names were checked against the exercise lists Strong and Hevy show (the user's screen recordings,
 * 2026-10-08). Where they name the same exercise differently Strong's spelling is the name here, so an
 * import from Strong lines up with the library, and the other spelling is an alias further down.
 */
private const val TABLE = """
# Chest
Bench Press: Barbell, Dumbbell, Smith Machine, Cable, Band | CH | SH TR
Incline Bench Press: Barbell, Dumbbell, Smith Machine, Cable | CH | SH TR
Decline Bench Press: Barbell, Dumbbell, Smith Machine | CH | TR SH
Bench Press - Close Grip: Barbell, Dumbbell, Smith Machine | TR CH | SH
Bench Press - Wide Grip: Barbell | CH | SH TR
Bench Press - Reverse Grip: Barbell | CH TR | SH ~FA
Bench Press - Pause: Barbell | CH | SH TR
Pause Bench Press: Barbell | CH | SH TR
Floor Press: Barbell, Dumbbell | CH TR | SH
Squeeze Press: Dumbbell | CH | TR
Chest Press: Machine, Cable, Band | CH | SH TR
Incline Chest Press: Machine | CH | SH TR
Decline Chest Press: Machine | CH | TR
Iso-Lateral Chest Press: Machine | CH | SH TR
Chest Fly: -, Dumbbell, Machine, Cable, Band, Suspension | CH | SH
Incline Chest Fly: Dumbbell, Cable | CH | SH
Decline Chest Fly: Dumbbell, Cable | CH | SH
Low To High Cable Fly: - | CH | SH
High To Low Cable Fly: - | CH | SH
Cable Crossover: - | CH | SH
Pec Deck: Machine | CH | SH
Push Up: -, Weighted, Knees, Band | CH | SH TR ~AB | bw
Incline Push Up: - | CH | SH TR ~AB | bw
Decline Push Up: - | CH | SH TR ~AB | bw
Wide Push Up: - | CH | SH TR ~AB | bw
Diamond Push Up: - | TR CH | SH ~AB | bw
Archer Push Up: - | CH | SH TR ~AB | bw
Chest Dip: -, Assisted, Weighted | CH TR | SH | bw
Pullover: Dumbbell, Barbell, Machine, Cable | LT CH | TR ~FA
Landmine Press: - | SH CH | TR ~AB
# Shoulders
Overhead Press: Barbell, Dumbbell, Smith Machine, Cable, Kettlebell, Band | SH | TR TP ~AB
Strict Military Press: Barbell | SH | TR TP ~AB
Seated Overhead Press: Barbell, Dumbbell | SH | TR TP ~AB
Shoulder Press: Machine, Plate Loaded, Cable, Dumbbell | SH | TR
Arnold Press: Dumbbell | SH | TR
Push Press: -, Dumbbell | SH | TR QD ~AB
Behind The Neck Press: Barbell, Smith Machine | SH | TR TP
Z Press: Barbell, Dumbbell | SH | TR ~AB
Pike Push Up: - | SH | TR ~AB | bw
Handstand Push Up: - | SH | TR TP ~AB | bw
Lateral Raise: Dumbbell, Cable, Machine, Band | SH | ~TP
Seated Lateral Raise: Dumbbell | SH | ~TP
Lying Lateral Raise: Dumbbell | SH | ~TP
Front Raise: Dumbbell, Cable, Barbell, Plate, Band, Suspension | SH | ~TP
Y Raise: Dumbbell, Cable | SH TP | UB
Lu Raise: Dumbbell, Plate | SH | TP
Reverse Fly: Dumbbell, Cable, Machine, Band | SH UB | TP ~FA
Rear Delt Row: Barbell, Dumbbell, Cable | SH UB | BI ~FA
Face Pull: Cable, Band | SH UB | TP ~FA
Upright Row: Barbell, Dumbbell, Cable, Kettlebell, EZ Bar | SH TP | BI
Shrug: Barbell, Dumbbell, Smith Machine, Machine, Cable, Trap Bar | TP | ~FA
Shoulder External Rotation: Cable, Dumbbell, Band | RC | SH
Seated Shoulder External Rotation: Dumbbell | RC | SH
Shoulder Internal Rotation: Cable, Band | RC | SH
Cuban Press: Dumbbell | SH | TP
Around the World: -, Kettlebell | CH SH |
# Back
Pull Up: -, Assisted, Weighted, Band, Neutral Grip, Wide Grip, Close Grip | LT | BI UB ~FA | bw
Wide Pull Up: - | LT | BI UB ~FA | bw
Kipping Pull Up: - | LT | BI SH ~FA | bw
Chin Up: -, Assisted, Weighted | LT BI | UB ~FA | bw
Scapular Pull Up: - | TP LT | ~FA | bw
Muscle Up: - | LT CH | TR BI ~FA | bw
Inverted Row: Bodyweight | UB LT | BI SH ~FA
Inverted Row - Underhand: Bodyweight | LT UB BI | SH ~FA
Lat Pulldown: Machine, Cable, Band, Single Arm | LT | BI UB ~FA
Lat Pulldown - Wide Grip: Cable | LT | BI UB ~FA
Lat Pulldown - Close Grip: Cable | LT | BI UB ~FA
Lat Pulldown - Neutral Grip: Cable | LT | BI UB ~FA
Lat Pulldown - Underhand: Cable, Band | LT BI | UB ~FA
Lat Pulldown - Behind The Neck: Cable | LT | BI UB ~FA
Kneeling Pulldown: Band | LT | BI ~FA
Straight Arm Lat Pulldown: Cable, Band | LT | TR ~FA
Iso-Lateral High Row: Machine | LT UB | BI ~FA
Iso-Lateral Row: Machine | UB LT | BI SH ~FA
Iso-Lateral Low Row: Machine | LT UB | BI ~FA
Seated Row: Cable, Machine, Band | UB LT | BI SH ~FA
Seated Row - Neutral Grip: Cable | LT UB | BI SH ~FA
Seated Row - Underhand: Cable | LT UB BI | SH ~FA
Seated Wide-Grip Row: Cable | UB | LT BI SH ~FA
Single Arm Row: Cable, Machine | LT UB | BI ~FA
Lat Row (Single Arm): Cable | LT | BI UB ~FA
Bent Over Row: Barbell, Dumbbell, Smith Machine, Band | UB LT | BI ~LB ~FA ~AB
Bent Over One Arm Row: Dumbbell | LT UB | BI ~FA
Bent Over Row - Underhand: Barbell | LT UB | BI ~LB ~FA ~AB
Incline Row: Dumbbell | UB LT | BI ~FA
Pendlay Row: Barbell | UB LT | BI ~LB ~FA
T Bar Row: -, Machine | UB LT | BI ~LB ~FA
Landmine Row: - | UB LT | BI ~LB ~FA ~AB
Meadows Row: - | LT UB | BI ~FA
Chest Supported Row: Dumbbell, Barbell, Machine | UB LT | BI ~FA
Seal Row: Barbell, Dumbbell | UB LT | BI ~FA
Renegade Row: Dumbbell | UB LT | BI ~AB ~FA
Deadlift: Barbell, Dumbbell, Smith Machine, Kettlebell, Band | HM GL LB | TP QD ~FA ~AB
Trap Bar Deadlift: - | HM GL LB | TP QD ~FA ~AB
Straight Leg Deadlift: Band | HM GL | LB ~AB
Deadlift High Pull: Barbell | TP SH | GL HM ~AB
Sumo Deadlift High Pull: Barbell | TP SH | GL HM QD ~AB
Jump Shrug: Barbell | TP | GL QD ~CV
Sumo Deadlift: Barbell | GL HM | QD LB ~AD ~AB
Deficit Deadlift: Barbell | HM GL LB | TP QD ~AB
Romanian Deadlift: Barbell, Dumbbell, Smith Machine, Kettlebell, Cable | HM GL | LB ~AB
Stiff Leg Deadlift: Barbell, Dumbbell | HM GL | LB ~AB
Single Leg Romanian Deadlift: Dumbbell, Kettlebell, Barbell | HM GL | LB ~AB
Rack Pull: Barbell | LB TP | GL HM ~FA
Good Morning: Barbell, Smith Machine, Band | HM LB | GL ~AB
Back Extension: -, Weighted | LB | GL HM | bw
Back Extension: Machine | LB | GL HM
Back Extension - Glute Focus: -, Weighted | GL | HM LB | bw
Reverse Hyperextension: - | GL LB | HM | bw
Superman: - | LB | GL | bw
# Biceps and forearms
Bicep Curl: Barbell, Dumbbell, Cable, Machine, EZ Bar, Band, Kettlebell, Plate, Suspension | BI | ~FA
Bicep Curl - Wide Grip: Barbell | BI | ~FA
Bicep Curl - Close Grip: Barbell | BI | ~FA
Hammer Curl: Dumbbell, Cable, Machine, Band | BI FA |
Cross Body Hammer Curl: Dumbbell | BI FA |
Preacher Curl: Barbell, Dumbbell, Machine, EZ Bar, Cable | BI | ~FA
Incline Curl: Dumbbell | BI | ~FA
Concentration Curl: Dumbbell, Cable | BI | ~FA
Spider Curl: Barbell, Dumbbell, EZ Bar | BI | ~FA
Drag Curl: Barbell, Dumbbell | BI | ~FA
Bayesian Curl: Cable | BI | ~FA
High Cable Curl: - | BI | ~FA
Waiter Curl: Dumbbell | BI | ~FA
Zottman Curl: Dumbbell | BI FA |
Reverse Curl: Barbell, Dumbbell, EZ Bar, Cable, Band | FA BI |
Reverse Grip Concentration Curl: Dumbbell | FA BI |
Seated Palms Up Wrist Curl: Dumbbell | FA |
Wrist Curl: Barbell, Dumbbell, Cable | FA |
Reverse Wrist Curl: Barbell, Dumbbell, Cable | FA |
Wrist Roller: - | FA |
# Triceps
Triceps Pushdown: Cable - Straight Bar, Cable - Rope, Cable - V Bar, Band | TR |
Single Arm Triceps Pushdown: Cable | TR |
Triceps Pushdown - Reverse Grip: Cable | TR | ~FA
Triceps Extension: -, Dumbbell, Cable, Barbell, Machine, EZ Bar, Band, Suspension | TR |
Overhead Triceps Extension: Dumbbell, Cable, EZ Bar, Band | TR |
Skullcrusher: Barbell, Dumbbell, EZ Bar | TR |
Triceps Kickback: Dumbbell, Band | TR |
Cable Kickback: - | TR |
Triceps Dip: -, Assisted, Weighted | TR CH | SH | bw
Bench Dip: - | TR | CH SH | bw
Seated Dip: Machine | TR | CH
JM Press: Barbell, Smith Machine | TR | CH
Tate Press: Dumbbell | TR |
# Legs
Squat: Barbell, Dumbbell, Smith Machine, Machine, Bodyweight, Band, Kettlebell, Suspension | QD GL | HM ~LB ~AB
Squat - Wide Stance: Barbell | QD GL AD | HM ~LB ~AB
Squat - Narrow Stance: Barbell | QD | GL HM ~LB ~AB
Squat - Pause: Barbell | QD GL | HM ~LB ~AB
Front Squat: Barbell, Kettlebell, Smith Machine | QD GL | ~AB ~LB
Goblet Squat: Dumbbell, Kettlebell | QD GL | ~AB
Box Squat: Barbell | QD GL | HM ~LB ~AB
Pause Squat: Barbell | QD GL | HM ~LB ~AB
Zercher Squat: Barbell | QD GL | ~AB ~LB
Overhead Squat: Barbell | QD GL | SH ~AB
Hack Squat: -, Barbell | QD | GL ~AB
Pendulum Squat: Machine | QD | GL ~AB
Belt Squat: Machine | QD GL | ~AB
V Squat: Machine | QD GL | ~AB
Sissy Squat: -, Weighted | QD | ~AB | bw
Pistol Squat: -, Assisted | QD GL | HM ~AB | bw
Jump Squat: - | QD GL | ~CV ~AB | bw
Box Jump: - | QD GL | ~CV | bw
Lateral Box Jump: - | QD GL | ~CV | bw
High Knee Skips: - | QD CV |  | bw
Jumping Jack: - | CV SH |  | bw
Bulgarian Split Squat: -, Dumbbell, Barbell, Smith Machine | QD GL | HM ~AB
Split Squat: Dumbbell, Barbell, Bodyweight | QD GL | HM ~AB
Lunge: Barbell, Dumbbell, Bodyweight, Smith Machine, Kettlebell | QD GL | HM ~AB
Reverse Lunge: Barbell, Dumbbell, Bodyweight, Smith Machine | QD GL | HM ~AB
Walking Lunge: Barbell, Dumbbell, Bodyweight | QD GL | HM ~AB
Side Lunge: Dumbbell, Bodyweight | QD GL AD | ~AB
Curtsy Lunge: Dumbbell, Bodyweight | GL QD | ~AB
Step-up: -, Dumbbell, Barbell | QD GL | HM ~AB
Leg Press: -, Machine, Single Leg | QD GL | HM
Leg Press - Feet High: Machine | GL HM | QD
Leg Press - Feet Low: Machine | QD | GL
Leg Press - Wide Stance: Machine | QD GL AD | HM
Leg Press - Narrow Stance: Machine | QD | GL HM
Seated Leg Press: Machine | QD GL | HM
Horizontal Leg Press: Machine | QD GL | HM
Leg Extension: Machine, Single Leg | QD |
Lying Leg Curl: Machine | HM | ~CV
Seated Leg Curl: Machine | HM | ~CV
Standing Leg Curl: Machine, Cable | HM | ~CV
Iso Standing Leg Curl: Plate Loaded | HM | ~CV
Nordic Hamstring Curl: - | HM | GL | bw
Glute Ham Raise: - | HM GL | LB | bw
Hip Thrust: Barbell, Dumbbell, Machine, Smith Machine, Bodyweight, Band | GL | HM
Single Leg Hip Thrust: Bodyweight, Dumbbell | GL | HM
Glute Bridge: Barbell, Dumbbell, Bodyweight, Band | GL | HM
Single Leg Bridge: - | GL | HM | bw
Glute Kickback: Cable, Machine, Band | GL | HM
Cable Pull Through: - | GL HM | LB
Kettlebell Swing: - | GL HM | LB SH ~AB
Hip Abductor: Machine, Cable, Band | GL |
Hip Adductor: Machine, Cable, Band | AD |
Donkey Kick: Bodyweight, Band | GL |
Fire Hydrant: Bodyweight, Band | GL |
Clamshell: Band | GL |
Clamshell: - | GL |  | bw
Frog Pump: - | GL |  | bw
Frog Pump: Dumbbell | GL |
Standing Calf Raise: Machine, Smith Machine, Dumbbell, Barbell, Bodyweight | CV |
Seated Calf Raise: Machine, Plate Loaded, Dumbbell, Barbell | CV |
Single Leg Standing Calf Raise: Bodyweight, Barbell, Dumbbell, Machine | CV |
Donkey Calf Raise: Machine | CV |
Calf Press on Leg Press: - | CV |
Calf Press on Seated Leg Press: - | CV |
Tibialis Raise: Machine, Band | TI |
# Core
Crunch: -, Weighted, Stability Ball | AB |  | bw
Cable Crunch: - | AB |
Cross Body Crunch: - | OB AB |  | bw
Crunch: Machine | AB |
Decline Crunch: -, Weighted | AB |  | bw
Reverse Crunch: - | AB |  | bw
Bicycle Crunch: - | AB OB |  | bw
Oblique Crunch: Bodyweight, Machine | OB | AB
Oblique Crunch: - | OB | AB | bw
Sit Up: -, Weighted, Decline | AB |  | bw
Jackknife Sit Up: - | AB |  | bw
V Up: - | AB |  | bw
Hanging Knee Raise: - | AB | HF | bw
Hanging Leg Raise: - | AB | HF | bw
Knee Raise: Captain's Chair | AB | HF | bw
Leg Raise: Captain's Chair | AB | HF | bw
Flat Leg Raise: - | AB | HF | bw
Flat Knee Raise: - | AB | HF | bw
Knees to Elbows: - | AB | LT HF | bw
Toes To Bar: - | AB | LT HF | bw
Flutter Kick: - | AB |  | bw
Mountain Climber: - | AB | SH | bw
Heel Touch: - | OB | AB | bw
Dead Bug: - | AB |  | bw
Bird Dog: - | LB AB | GL | bw
Dragon Flag: - | AB |  | bw
Ab Wheel: - | AB | LT SH | bw
Ab Coaster: Machine | AB |
Russian Twist: -, Weighted | OB | AB | bw
Side Bend: Dumbbell, Cable, Band | OB |
Cable Twist: -, Down to up, Up to down | OB | AB
Wood Chop: Cable, Band, Dumbbell | OB | AB SH
Pallof Press: -, Cable, Band | OB | AB
Torso Rotation: Machine | OB |
# Olympic and whole body
Clean: Barbell, Kettlebell | QD GL HM | TP SH LB ~AB
Power Clean: - | QD GL HM | TP SH LB ~AB
Hang Clean: Barbell | GL HM | TP SH QD ~AB
Clean and Jerk: Barbell | QD GL SH | TP TR HM ~AB
Press Under: Barbell | SH QD | TR
Clean Pull: Barbell | HM GL | TP LB ~AB
Snatch: Barbell, Dumbbell, Kettlebell | QD GL SH | TP HM ~LB ~AB
Power Snatch: Barbell | QD GL SH | TP ~LB ~AB
Hang Snatch: Barbell | GL HM SH | TP QD ~AB
Snatch Pull: Barbell | HM GL | TP LB ~AB
Push Jerk: Barbell | SH QD | TR ~AB
Split Jerk: Barbell | SH QD | TR GL ~AB
Thruster: Barbell, Dumbbell, Kettlebell | QD SH | GL TR ~AB
Wall Ball: - | QD SH | GL
Devil Press: Dumbbell | SH QD CH | GL TR
Man Maker: Dumbbell | SH CH QD | UB TR
Kettlebell Turkish Get Up: - | SH AB | GL QD
Turkish Get Up: Dumbbell | SH AB | GL QD
Squat Row: Band | QD UB | GL BI ~FA ~AB
Ball Slams: - | AB SH | LT
Burpee: - | QD CH | SH ~AB | bw
# More, found in other apps' lists
21s Bicep Curl: - | BI | ~FA
Ab Scissors: - | AB |  | bw
Band Pull Apart: - | SH UB | TP ~FA
Behind the Back Wrist Curl: Barbell | FA |
Bicycle Crunch Raised Legs: - | AB OB |  | bw
Burpee Over the Bar: - | QD CH | SH ~AB | bw
Calf Extension: Machine | CV |
Calf Press: Machine | CV |
Chest Supported Reverse Fly: Dumbbell | SH UB | TP ~FA
Chest Supported Y Raise: Dumbbell | SH TP | UB
Clap Push Up: - | CH | SH TR ~AB | bw
Clean and Press: Barbell, Dumbbell, Kettlebell | SH QD GL | TP TR HM ~AB
Dragonfly: - | AB |  | bw
Elbow to Knee: - | AB OB |  | bw
Feet Up Bench Press: Barbell | CH | SH TR
Floor Triceps Dip: - | TR | SH | bw
Frog Jumps: - | QD GL | ~CV | bw
Front Lever Raise: - | LT AB | ~FA | bw
Gorilla Row: Kettlebell | UB LT | BI ~FA
Halo: Kettlebell | SH | ~AB
High Pull: Kettlebell | TP SH | GL HM
Hollow Rock: - | AB |  | bw
Jack Knife: Suspension | AB | SH
Jumping Lunge: - | QD GL | ~CV ~AB | bw
Landmine 180: - | OB AB | SH
Landmine Squat and Press: - | QD SH | GL TR ~AB
Lateral Band Walks: - | GL |
Lateral Leg Raises: - | GL |  | bw
Lateral Squat: - | QD AD | GL ~AB | bw
Low Row: Suspension | UB LT | BI ~FA
Lying Neck Curls: -, Weighted | NK |  | bw
Lying Neck Extension: -, Weighted | NK | TP | bw
Negative Pull Up: - | LT | BI UB ~FA | bw
One Arm Push Up: - | CH | SH TR ~AB | bw
Overhead Lunge: Dumbbell | QD GL | SH ~AB
Overhead Plate Raise: - | SH | TP
Partial Glute Bridge: Barbell | GL | HM
Plank Push Up: - | CH AB | TR SH | bw
Plate Press: - | CH | SH TR
Svend Press: - | CH | SH
Push Up - Close Grip: - | TR CH | SH ~AB | bw
Push Up - Wide Grip: - | CH | SH TR ~AB | bw
Reverse Fly Single Arm: Cable | SH UB | ~FA
Ring Dip: - | CH TR | SH | bw
Ring Pull Up: - | LT | BI UB ~FA | bw
Ring Push Up: - | CH | SH TR ~AB | bw
Seated Row - Bar Grip: Cable | UB LT | BI ~FA
Seated Triceps Press: - | TR |
Shoulder Taps: - | SH AB |  | bw
Single Arm Cable Crossover: - | CH | SH
Single Arm Curl: Cable | BI | ~FA
Single Arm Triceps Extension: Dumbbell | TR |
Spiderman: - | AB OB |  | bw
Sternum Pull Up: - | LT UB | BI ~FA | bw
Sumo Squat: -, Barbell, Dumbbell, Kettlebell | QD GL AD | ~AB
Toe Touch: - | AB |  | bw
# Timed: logged as seconds, with a weight where one is held
Plank: -, Weighted | AB | OB | time
Side Plank: - | OB | AB | time
Reverse Plank: - | LB GL | ~AB | time
Hollow Hold: - | AB |  | time
L Sit: - | AB | TR | time
Wall Sit: - | QD | GL | time
Dead Hang: - | FA | LT | time
Farmer's Walk: - | FA TP | ~AB | time
Front Lever Hold: - | LT AB | ~FA | time
Handstand Hold: - | SH | TR | time
Downward Dog: - | SH | HM | time
# Cardio: logged as distance and time
Running: -, Treadmill |  |  | cardio
Walking: -, Treadmill |  |  | cardio
Hiking: - |  |  | cardio
Cycling: -, Indoor |  |  | cardio
Elliptical Machine: - |  |  | cardio
Rowing: Machine |  |  | cardio
Stair Climber: - |  |  | cardio
Air Bike: - |  |  | cardio
Boxing: - |  |  | cardio
HIIT: - |  |  | cardio
Pilates: - |  |  | cardio
Sprints: - |  |  | cardio
Warm Up: - |  |  | cardio
Sled Push: - |  |  | cardio
Jump Rope: - |  |  | cardio
Battle Ropes: - |  |  | cardio
Swimming: - |  |  | cardio
Aerobics: - |  |  | cardio
Climbing: - |  |  | cardio
Skating: - |  |  | cardio
Skiing: - |  |  | cardio
Snowboarding: - |  |  | cardio
Stretching: - |  |  | cardio
Yoga: - |  |  | cardio
"""

private fun muscles(codes: String): Set<Muscle> = codes.split(' ').filter { it.isNotEmpty() }.mapTo(LinkedHashSet()) { CODES.getValue(it) }

/** A table line's exercises, each with the movement its line is about: the part of the line's name before any ` - `. */
private fun parse(table: String): List<Pair<LibraryExercise, String>> =
    table.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.flatMap { line ->
        val parts = line.split('|').map { it.trim() }
        val base = parts[0].substringBeforeLast(':').trim()
        val helpers = parts.getOrElse(2) { "" }.split(' ').filter { it.isNotEmpty() }
        val use = MuscleUse(
            muscles(parts[1]),
            helpers.filter { !it.startsWith("~") }.mapTo(LinkedHashSet()) { CODES.getValue(it) },
            helpers.filter { it.startsWith("~") }.mapTo(LinkedHashSet()) { CODES.getValue(it.drop(1)) },
        )
        val flag = parts.getOrElse(3) { "" }
        parts[0].substringAfterLast(':').split(',').map { it.trim() }.map { variant ->
            val type = when {
                variant == "Assisted" -> LoadType.ASSISTED
                flag == "time" -> LoadType.TIMED
                flag == "cardio" -> LoadType.CARDIO
                flag == "bw" || variant == "Bodyweight" -> LoadType.BODYWEIGHT
                else -> LoadType.NORMAL
            }
            LibraryExercise(if (variant == "-") base else "$base ($variant)", use, type) to base.substringBefore(" - ").trim()
        }
    }.toList()

private val HAND_WRITTEN: List<Pair<LibraryExercise, String>> by lazy { parse(TABLE) }

/** Every built-in exercise: the hand-written table first, then the generated one (LibraryMore.kt). */
val LIBRARY: List<LibraryExercise> by lazy { HAND_WRITTEN.map { it.first } + parse(MORE_TABLE).map { it.first } }

/*
 * Built-in variations. In the hand-written table a movement's lines (`Lat Pulldown: ...`,
 * `Lat Pulldown - Wide Grip: ...`) and the equipment on each line are that movement's variations:
 * the table says so by how it is written, nothing is read out of a name the user typed. A few
 * movements with a name of their own count as a variation of another and are listed here. This is
 * only the default; the user's own grouping, stored with the exercise, wins.
 */
private val SAME_MOVEMENT = mapOf(
    "Chin Up" to "Pull Up",
    "Diamond Push Up" to "Push Up",
    "Sumo Deadlift" to "Deadlift",
    "Sumo Squat" to "Squat",
    "Feet Up Bench Press" to "Bench Press",
    "Chest Dip" to "Dip",
    "Triceps Dip" to "Dip",
    "Hammer Curl" to "Bicep Curl",
    "Reverse Curl" to "Bicep Curl",
    "Lying Leg Curl" to "Leg Curl",
    "Seated Leg Curl" to "Leg Curl",
    "Seated Wide-Grip Row" to "Seated Row",
)

internal val OVERALL: Map<String, String> by lazy {
    val of = HAND_WRITTEN.associate { (exercise, movement) -> exercise.name to (SAME_MOVEMENT[movement] ?: movement) }
    val sizes = of.values.groupingBy { it }.eachCount()
    of.filterValues { sizes.getValue(it) > 1 }
}

internal val VARIATIONS: Map<String, List<String>> by lazy { OVERALL.entries.groupBy({ it.value }, { it.key }) }

/**
 * The overall exercise a built-in exercise (by its name or another name for it) is a variation of,
 * when it has siblings. A rule-made variation (Variations.kt) belongs with the exercise it was made
 * from: to that exercise's group, or to the exercise itself when it has none.
 */
fun libraryOverall(name: String): String? =
    OVERALL[name] ?: canonicalName(name)?.let { OVERALL[it] } ?: variationBase(name)?.let { libraryOverall(it) ?: it }

/**
 * The built-in variations of an overall exercise: the hand-written ones first, then the rule-made
 * ones of each. An exercise that is in no group has only its own rule-made variations. Asking with
 * a variation's name gives its whole group.
 */
fun libraryVariations(overall: String): List<String> {
    val base = variationBase(overall) ?: overall
    val group = VARIATIONS[base] ?: libraryOverall(base)?.let { VARIATIONS[it] } ?: return generatedVariations(base)
    return (group + group.flatMap(::generatedVariations)).distinct()
}

private val BY_NAME: Map<String, LibraryExercise> by lazy { LIBRARY.associateBy { it.name } }

/** The built-in exercise with exactly this name, if there is one. */
fun libraryExercise(name: String): LibraryExercise? = BY_NAME[name]

/*
 * Other names for library exercises: `other name = library name`, the other name being what another
 * app or common gym talk calls it. A line starting with `*` renames the movement itself and so covers
 * every equipment variant: `* Skull Crusher = Skullcrusher` makes `Skull Crusher (Barbell)` an alias
 * of `Skullcrusher (Barbell)`. Letter case and hyphens never matter.
 */
private const val ALIASES = """
* Skull Crusher = Skullcrusher
* Lying Triceps Extension = Skullcrusher
* Biceps Curl = Bicep Curl
* Tricep Pushdown = Triceps Pushdown
* Tricep Extension = Triceps Extension
* Tricep Kickback = Triceps Kickback
* Tricep Dip = Triceps Dip
* Overhead Tricep Extension = Overhead Triceps Extension
* French Press = Overhead Triceps Extension
* Military Press = Overhead Press
* OHP = Overhead Press
* RDL = Romanian Deadlift
* Stiff Legged Deadlift = Stiff Leg Deadlift
* Hyperextension = Back Extension
* Pulldown = Lat Pulldown
* Lat Pull Down = Lat Pulldown
* Pullup = Pull Up
* Chinup = Chin Up
* Pushup = Push Up
* Press Up = Push Up
* Situp = Sit Up
* Step Up = Step-up
* Rear Delt Reverse Fly = Reverse Fly
* Rear Delt Fly = Reverse Fly
* Reverse Pec Deck = Reverse Fly
* Pec Fly = Chest Fly
* Fly = Chest Fly
* Calf Raise = Standing Calf Raise
* Leg Curl = Lying Leg Curl
* Hamstring Curl = Lying Leg Curl
* Hip Abduction = Hip Abductor
* Hip Adduction = Hip Adductor
* Side Raise = Lateral Raise
* Lateral Raises = Lateral Raise
* Shrugs = Shrug
* Lunges = Lunge
* Squats = Squat
* Deadlifts = Deadlift
* Wrist Extension = Reverse Wrist Curl
* Good Mornings = Good Morning
* Face Pulls = Face Pull
* Hip Thrusts = Hip Thrust
* Single Leg Calf Raise = Single Leg Standing Calf Raise
* Rear Delt Reverse Fly Single Arm = Reverse Fly Single Arm
Back Squat = Squat (Barbell)
Full Squat = Squat (Barbell)
Barbell Row = Bent Over Row (Barbell)
Dumbbell Row = Bent Over One Arm Row (Dumbbell)
Flat Bench Press = Bench Press (Barbell)
Close Grip Bench Press = Bench Press - Close Grip (Barbell)
Close Grip Bench Press (Barbell) = Bench Press - Close Grip (Barbell)
Wide Grip Bench Press (Barbell) = Bench Press - Wide Grip (Barbell)
Assault Bike = Air Bike
Around The World (Dumbbell) = Around the World
Kettlebell Around the World = Around the World (Kettlebell)
Assisted Pistol Squats = Pistol Squat (Assisted)
Back Extension (Hyperextension) = Back Extension
Back Extension (Weighted Hyperextension) = Back Extension (Weighted)
Band Pullaparts = Band Pull Apart
Behind the Back Bicep Wrist Curl (Barbell) = Behind the Back Wrist Curl (Barbell)
Behind the Back Curl (Cable) = Bayesian Curl (Cable)
Butterfly (Pec Deck) = Pec Deck (Machine)
Cable Core Palloff Press = Pallof Press (Cable)
Cable Fly Crossovers = Cable Crossover
Low Cable Fly Crossovers = Low To High Cable Fly
Chest Supported Incline Row (Dumbbell) = Incline Row (Dumbbell)
Clap Push Ups = Clap Push Up
Clean = Clean (Barbell)
Clean and Jerk = Clean and Jerk (Barbell)
Clean and Press = Clean and Press (Barbell)
Clean Pull = Clean Pull (Barbell)
Kettlebell Clean = Clean (Kettlebell)
Concentration Curl = Concentration Curl (Dumbbell)
Cross Body Hammer Curl = Cross Body Hammer Curl (Dumbbell)
Pinwheel Curl (Dumbbell) = Cross Body Hammer Curl (Dumbbell)
Deadlift (Trap bar) = Trap Bar Deadlift
Hex Bar Deadlift = Trap Bar Deadlift
Deadlift High Pull = Deadlift High Pull (Barbell)
Decline Bench Press (Machine) = Decline Chest Press (Machine)
Drag Curl = Drag Curl (Barbell)
Dumbbell Snatch = Snatch (Dumbbell)
Kettlebell Snatch = Snatch (Kettlebell)
Dumbbell Squeeze Press = Squeeze Press (Dumbbell)
Hex Press (Dumbbell) = Squeeze Press (Dumbbell)
Dumbbell Step Up = Step-up (Dumbbell)
Elliptical Trainer = Elliptical Machine
EZ Bar Biceps Curl = Bicep Curl (EZ Bar)
Kettlebell Curl = Bicep Curl (Kettlebell)
Plate Curl = Bicep Curl (Plate)
Rope Cable Curl = Hammer Curl (Cable)
Face Pull = Face Pull (Cable)
Farmers Walk = Farmer's Walk
Farmers Carry = Farmer's Walk
Fire Hydrants = Fire Hydrant (Bodyweight)
Flutter Kicks = Flutter Kick
Frog Pumps (Dumbbell) = Frog Pump (Dumbbell)
Front Squat = Front Squat (Barbell)
Glute Bridge = Glute Bridge (Bodyweight)
Single Leg Glute Bridge = Single Leg Bridge
Glute Kickback on Floor = Donkey Kick (Bodyweight)
Standing Cable Glute Kickbacks = Glute Kickback (Cable)
Rear Kick (Machine) = Glute Kickback (Machine)
Goblet Squat = Goblet Squat (Dumbbell)
Kettlebell Goblet Squat = Goblet Squat (Kettlebell)
Kettlebell Halo = Halo (Kettlebell)
Kettlebell High Pull = High Pull (Kettlebell)
Kettlebell Shoulder Press = Overhead Press (Kettlebell)
Hack Squat (Machine) = Hack Squat
Hang Clean = Hang Clean (Barbell)
Hang Snatch = Hang Snatch (Barbell)
Heel Taps = Heel Touch
High Knees = High Knee Skips
Hip Thrust = Hip Thrust (Bodyweight)
Single Leg Hip Thrust = Single Leg Hip Thrust (Bodyweight)
Incline Push Ups = Incline Push Up
Inverted Row = Inverted Row (Bodyweight)
Iso-Lateral Low Row = Iso-Lateral Low Row (Machine)
Jump Shrug = Jump Shrug (Barbell)
Knee Raise Parallel Bars = Knee Raise (Captain's Chair)
Leg Raise Parallel Bars = Leg Raise (Captain's Chair)
Kneeling Push Up = Push Up (Knees)
L-Sit Hold = L Sit
Lateral Lunge = Side Lunge (Bodyweight)
Leg Press Horizontal (Machine) = Horizontal Leg Press (Machine)
Single Leg Press (Machine) = Leg Press (Single Leg)
Single Leg Extensions = Leg Extension (Single Leg)
Lunge = Lunge (Bodyweight)
Reverse Lunge = Reverse Lunge (Bodyweight)
Walking Lunge = Walking Lunge (Bodyweight)
Overhead Dumbbell Lunge = Overhead Lunge (Dumbbell)
Lying Knee Raise = Flat Knee Raise
Lying Leg Raise = Flat Leg Raise
Meadows Rows (Barbell) = Meadows Row
Nordic Hamstrings Curls = Nordic Hamstring Curl
Nordic Curl = Nordic Hamstring Curl
Overhead Curl (Cable) = High Cable Curl
Overhead Squat = Overhead Squat (Barbell)
Pike Pushup = Pike Push Up
Plank Pushup = Plank Push Up
Plate Front Raise = Front Raise (Plate)
Plate Squeeze (Svend Press) = Svend Press
Power Snatch = Power Snatch (Barbell)
Power Clean (Barbell) = Power Clean
Press Under = Press Under (Barbell)
Push Press (Barbell) = Push Press
Rack Pull = Rack Pull (Barbell)
Reverse Grip Concentration Curl = Reverse Grip Concentration Curl (Dumbbell)
Reverse Grip Lat Pulldown (Cable) = Lat Pulldown - Underhand (Cable)
Wide Grip Lat Pulldown (Cable) = Lat Pulldown - Wide Grip (Cable)
Single Arm Lat Pulldown = Lat Pulldown (Single Arm)
Vertical Traction (Machine) = Lat Pulldown (Machine)
Rope Straight Arm Pulldown = Straight Arm Lat Pulldown (Cable)
Rowing Machine = Rowing (Machine)
Russian Twist (Bodyweight) = Russian Twist
Scapular Pull Ups = Scapular Pull Up
Seated Cable Row - Bar Grip = Seated Row - Bar Grip (Cable)
Seated Cable Row - Bar Wide Grip = Seated Wide-Grip Row (Cable)
Seated Cable Row - V Grip (Cable) = Seated Row (Cable)
Seated Cable Row = Seated Row (Cable)
Single Arm Cable Row = Single Arm Row (Cable)
Seated Calf Raise = Seated Calf Raise (Machine)
Standing Calf Raise = Standing Calf Raise (Bodyweight)
Standing Calf Raise (Smith) = Standing Calf Raise (Smith Machine)
Seated Chest Flys (Cable) = Chest Fly (Cable)
Seated Dip Machine = Seated Dip (Machine)
Seated Incline Curl (Dumbbell) = Incline Curl (Dumbbell)
Seated Palms Up Wrist Curl = Seated Palms Up Wrist Curl (Dumbbell)
Seated Shoulder Press (Machine) = Shoulder Press (Machine)
Shoulder Press (Machine Plates) = Shoulder Press (Plate Loaded)
Seated Wrist Extension (Barbell) = Reverse Wrist Curl (Barbell)
Side Bend = Side Bend (Dumbbell)
Single Arm Landmine Press (Barbell) = Landmine Press
Single Arm Lateral Raise (Cable) = Lateral Raise (Cable)
Single Arm Tricep Extension (Dumbbell) = Single Arm Triceps Extension (Dumbbell)
Snatch = Snatch (Barbell)
Spinning = Cycling (Indoor)
Split Jerk = Split Jerk (Barbell)
Squat Row = Squat Row (Band)
Stair Machine (Floors) = Stair Climber
Stair Machine (Steps) = Stair Climber
Stair Machine = Stair Climber
Standing Leg Curls = Standing Leg Curl (Machine)
Standing Military Press (Barbell) = Strict Military Press (Barbell)
Sternum Pull up (Gironda) = Sternum Pull Up
Straight Leg Deadlift = Stiff Leg Deadlift (Barbell)
Sumo Deadlift = Sumo Deadlift (Barbell)
Torso Rotation = Torso Rotation (Machine)
Treadmill = Running (Treadmill)
Triceps Kickback (Cable) = Cable Kickback
Triceps Pressdown = Triceps Pushdown (Cable - Straight Bar)
Triceps Pushdown = Triceps Pushdown (Cable - Straight Bar)
Triceps Rope Pushdown = Triceps Pushdown (Cable - Rope)
Wide-Elbow Triceps Press (Dumbbell) = Tate Press (Dumbbell)
Zercher Squat = Zercher Squat (Barbell)
Pause Squat = Pause Squat (Barbell)
Pendlay Row = Pendlay Row (Barbell)
Good Morning = Good Morning (Barbell)
Arnold Press = Arnold Press (Dumbbell)
Cable Crunch (Kneeling) = Cable Crunch
Calf Press (Leg Press) = Calf Press on Leg Press
Ab Wheel Rollout = Ab Wheel
Ab Rollout = Ab Wheel
Jumping Jacks = Jumping Jack
Mountain Climbers = Mountain Climber
Burpees = Burpee
Box Jumps = Box Jump
Toes to Bar = Toes To Bar
T-Bar Row = T Bar Row
"""

// Shorthand inside the brackets.
private val EQUIPMENT_ALIASES = mapOf(
    "db" to "dumbbell", "bb" to "barbell", "kb" to "kettlebell", "smith" to "smith machine", "ez" to "ez bar",
    "ez-bar" to "ez bar", "trap bar" to "trap bar", "machine plates" to "plate loaded", "plate-loaded" to "plate loaded",
    "resistance band" to "band", "bands" to "band", "cables" to "cable", "dumbbells" to "dumbbell", "bw" to "bodyweight",
)

// Equipment written in front of the movement, longest first so "smith machine" wins over "smith".
private val LEADING_EQUIPMENT = listOf(
    "smith machine" to "smith machine", "ez bar" to "ez bar", "trap bar" to "trap bar", "barbell" to "barbell", "dumbbell" to "dumbbell",
    "cable" to "cable", "machine" to "machine", "kettlebell" to "kettlebell", "band" to "band", "smith" to "smith machine",
    "db" to "dumbbell", "bb" to "barbell", "kb" to "kettlebell",
)

private val SPACES = Regex("\\s+")

private fun norm(name: String): String = name.lowercase().replace('-', ' ').replace(SPACES, " ").trim()

/** Answers already worked out, by the name asked; an empty text stands for "none". The same few hundred names are asked for constantly. */
private val CANONICAL = java.util.concurrent.ConcurrentHashMap<String, String>()

private val BY_NORM: Map<String, LibraryExercise> by lazy { LIBRARY.associateBy { norm(it.name) } }

private val ALIAS_LINES: List<Pair<String, String>> by lazy {
    ALIASES.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringBefore(" = ") to it.substringAfter(" = ") }.toList()
}
private val EXACT_ALIASES: Map<String, String> by lazy { ALIAS_LINES.filter { !it.first.startsWith("*") }.associate { norm(it.first) to it.second } }
private val MOVEMENT_ALIASES: Map<String, String> by lazy {
    ALIAS_LINES.filter { it.first.startsWith("*") }.associate { norm(it.first.removePrefix("*")) to it.second }
}

/**
 * The library exercise that [name] is another name for, or null when [name] is itself a library
 * name or is not recognised. Used to find exercises in search and to offer a match when
 * something is imported; the user's own stored names are never rewritten by it.
 */
fun canonicalName(name: String): String? = CANONICAL.getOrPut(name) { findCanonical(name) ?: "" }.ifEmpty { null }

private fun findCanonical(name: String): String? {
    if (BY_NAME.containsKey(name)) return null
    val n = norm(name)
    BY_NORM[n]?.let { return it.name }
    EXACT_ALIASES[n]?.let { if (BY_NAME.containsKey(it)) return it }
    // "Movement (Equipment)": try the movement's other name and the equipment's shorthand.
    val open = name.indexOf('(')
    val movement = norm(if (open < 0) name else name.take(open))
    val tail = if (open < 0) "" else name.drop(open).trim()
    val base = MOVEMENT_ALIASES[movement] ?: movement
    val bracket = if (tail.startsWith("(") && tail.endsWith(")") && tail.count { it == '(' } == 1) {
        val inner = norm(tail.substring(1, tail.length - 1))
        " (" + (EQUIPMENT_ALIASES[inner] ?: inner) + ")"
    } else if (tail.isEmpty()) "" else " " + norm(tail)
    BY_NORM[norm(base + bracket)]?.let { return it.name }
    // "Barbell Bench Press" is another way of writing "Bench Press (Barbell)".
    if (open < 0) {
        for ((prefix, equipment) in LEADING_EQUIPMENT) {
            if (!n.startsWith("$prefix ")) continue
            val rest = n.removePrefix("$prefix ")
            BY_NORM[norm((MOVEMENT_ALIASES[rest] ?: rest) + " ($equipment)")]?.let { return it.name }
        }
    }
    return null
}

/** Every other name each library exercise is known by, for search. */
private val OTHER_NAMES: Map<String, List<String>> by lazy {
    val names = HashMap<String, MutableList<String>>()
    for ((alias, canonical) in ALIAS_LINES) {
        if (!alias.startsWith("*")) {
            if (BY_NAME.containsKey(canonical) && norm(alias) != norm(canonical)) names.getOrPut(canonical) { mutableListOf() } += alias
            continue
        }
        // A movement's other name applies to the bare movement and to each of its equipment variants.
        val other = alias.removePrefix("*").trim()
        for (exercise in LIBRARY) {
            if (exercise.name == canonical) names.getOrPut(exercise.name) { mutableListOf() } += other
            else if (exercise.name.startsWith("$canonical (")) names.getOrPut(exercise.name) { mutableListOf() } += other + exercise.name.removePrefix(canonical)
        }
    }
    names
}

fun otherNames(name: String): List<String> = OTHER_NAMES[name].orEmpty()
