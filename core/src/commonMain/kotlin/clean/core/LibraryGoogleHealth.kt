package clean.core

/*
 * More built-in exercises, from the exercise picker of Google's Google Health app (the redesigned
 * Fitbit app), read off a screen recording of its list on 2026-11-23. Only facts were taken: the
 * names and which muscles an exercise trains. Nothing else of that app is here, and the list of
 * names itself is in tools/googlehealth/names.txt.
 * Same line format as the table in Library.kt. Stretches, poses and holds are logged as seconds
 * (`time`), drills and runs with a distance as `cardio`, the other bodyweight work as `bw`.
 * An exercise done one side at a time is one entry (docs/left-right.md), so "Neck rotation (right)"
 * is an alias of `Neck Rotation`, not a second exercise.
 * Google's other spelling of a name, and of an exercise the library already had, is in the alias
 * tables below: `their name = library name`, read like the alias table in Library.kt.
 */
private const val GOOGLE_TABLE_0 = """
45° Hip Extension: - | GL | HM LB | bw
90/90 Stretch: - | GL | AD | time
A-Skip/B-Skip: - | QD | HF CV | bw
A-Skip: - | QD | HF CV | bw
Adductor Roll: - | AD |  | time
Alternating Chest Press: Cable | CH | SH TR
Alternating Curtsy Lunge: - | GL QD | AD | bw
Alternating Bench Press: Dumbbell | CH | SH TR
Alternating Bent Over Row: Dumbbell | UB LT | BI ~FA
Alternating Renegade Row: Dumbbell | UB LT | BI ~AB ~FA
Alternating Incline Bench Press: Dumbbell | CH | SH TR
Alternating Leg Raise: - | AB | HF | bw
Alternating Sandbag Shouldering: - | QD GL | SH TP ~AB
American Swing: Kettlebell | GL HM | LB SH ~AB
Ankle Hop: - | CV | TI | bw
Ankle Roll: - | CV | TI | time
Anticlockwise Leg Circle: - | HF | GL AB | bw
Arm Circles: - | SH |  | bw
Atlas Stone Load: - | GL HM LB | QD TP ~FA
Atlas Stone to Shoulder: - | GL HM QD | LB TP BI ~FA
Australian Pull Up/Chin Up: - | UB LT | BI SH ~FA | bw
Deadlift: Axle Bar | HM GL LB | TP QD ~FA ~AB
Axle Clean and Press: - | QD GL SH | TP TR HM ~AB
B-Stance Romanian Deadlift: Barbell, Dumbbell | HM GL | LB ~AB
B-Skip: - | QD | HF CV | bw
Squat: Safety Bar | QD GL | HM ~LB ~AB
Backwards Roll: - | AB LB |  | bw
Scarecrow: Band | SH RC | UB TP
Wood Chop - Horizontal: Band | OB | AB SH
Neck Rotation: Band | NK | 
Romanian Deadlift: Band | HM GL | LB ~AB
Spanish Squat: Band | QD | GL
Turkish Get Up: Band | SH AB | GL QD
Zottman Curl: Band | BI FA | 
Finger Extension: Band | FA | 
Goblet Squat: Band, Plate | QD GL | ~AB
Incline Bench Press: Band | CH | SH TR
Isometric Sleeper Stretch: Band | RC SH |  | time
Jump Squat: Band, Medicine Ball | QD GL | CV ~AB
No-Money Curl: Band | RC | SH
Partner Sprint: Band |  |  | cardio
Pulse Squat: Band, Dumbbell, Kettlebell, Plate | QD GL | ~AB
Reverse Wood Chop: Band, Kettlebell | OB | AB SH
Shoulder Dislocate: Band | SH RC | 
Terminal Knee Extension: Band | QD | 
Anderson Squat: Barbell | QD GL | ~AB ~LB
B-Stance Hip Thrust: Barbell | GL | HM
French Press: Barbell | TR | 
Sots Press: Barbell | SH | TR TP QD
Bench Press - Spoto: Barbell | CH | SH TR
Yates Row: Barbell | UB LT | BI ~LB ~FA
Zercher Carry: Barbell | FA TP BI | QD GL ~AB | time
Behind the Back Shrug: Barbell | TP | ~FA
Block Clean: Barbell | GL HM QD | TP SH LB ~AB
Block Snatch: Barbell | GL QD SH | TP HM ~LB ~AB
Bench Press - Board: Barbell | CH TR | SH
Burpee: Barbell, Dumbbell, Kettlebell | QD CH | SH GL ~AB
Clean Deadlift: Barbell | HM GL LB | QD TP ~FA ~AB
Clean High Pull: Barbell | TP SH | GL HM QD ~AB
Cluster: Barbell | QD GL SH | TP TR HM ~AB
Deadrow: Barbell | UB LT | BI HM ~LB ~FA
Floor to Overhead: Barbell | QD GL SH | TP TR HM ~AB
Front Rack Carry: Barbell | FA TP | QD SH ~AB | time
Hang Power Clean: Barbell | GL HM | TP SH QD ~AB
Hang Pull: Barbell, Dumbbell | HM GL | TP LB ~AB
Inverted Row: Barbell, Smith Machine | UB LT | BI SH ~FA
Landmine Romanian Deadlift: Barbell | HM GL | LB ~AB
Landmine Squat: Barbell | QD GL | ~AB
Muscle Clean: Barbell | TP SH | GL HM TR ~AB
Muscle Snatch: Barbell | TP SH | GL TR ~AB
Overhead Carry: Barbell | SH FA TP | TR ~AB | time
Bench Press - Pin: Barbell | CH TR | SH
Rollout: Barbell | AB | LT SH
Skullcrusher to Close Grip Press: Barbell | TR | CH
Snatch Balance: Barbell | QD GL SH | TP ~AB
Snatch High Pull: Barbell | TP SH | GL HM ~AB
Squat with Straight Arms: Barbell | QD GL | SH ~AB
Tall Kneeling Press: Barbell | SH | TR ~AB
Triceps Floor Press: Barbell | TR CH | SH
Battle Rope Figure of Eight: - |  |  | cardio
Battle Rope Slams: - |  |  | cardio
Battle Rope Alternating Waves: - |  |  | cardio
Behind The Neck Push Press: Barbell | SH | TR QD TP
Bench Couch Stretch: - | HF | QD | time
Bent Knee Kickback: Cable | GL | HM
Bent Knee Control Balance: - | AB | HF | time
Bent Knee Roll Over: - | AB | LB | bw
Bent Knee Roll Up: - | AB | HF | bw
Bent Leg Raise: - | AB | HF | bw
Bent Leg Saw: - | OB | AB LB | bw
Bicycle/Hip Lift: - | AB OB | HF | bw
cardio: - | QD GL | CV
Boat Pose: - | AB | HF | time
Boomerang: - | AB | LB | bw
Bounding for Distance: - | QD GL | CV HM | bw
Box Drill: - | QD CV | GL | bw
Box Hamstring Stretch: - | HM | GL | time
Box Jump and Step-Down: - | QD GL | CV | bw
Box Jump Burpee: - | QD CH | GL SH ~AB | bw
Box Jump Over: - | QD GL | CV | bw
Box Tuck Jump: - | QD GL | CV AB | bw
Broad Jump: - | QD GL | HM CV | bw
Broad Jump Over Hurdle: - | QD GL | HM CV | bw
Bulgarian Split Squat Jump: - | QD GL | HM CV | bw
Burpee Broad Jump: - | QD CH | SH GL | bw
Burpee Over Step: - | QD CH | SH ~AB | bw
Burpee/Mountain Climber: - | QD CH | SH AB | bw
Burpee/Tuck Jump: - | QD CH | SH AB | bw
Burpee/Press Up: - | QD CH | SH TR | bw
Butterfly Stretch: - | AD | GL | time
Alternating Pulldown - Bottom: Cable | LT | BI UB ~FA
Alternating Pulldown - Top: Cable | LT | BI UB ~FA
Rotational Lift: Cable | OB | AB SH
Rotational Press: Cable | OB | AB CH SH
Rotational Pull: Cable | OB UB | AB BI
Rotational Push: Cable | OB CH | AB TR SH
Seated Alternating Row: Cable | UB LT | BI SH ~FA
Stability Pull: Cable | OB | AB LT
Calf Roll: - | CV |  | time
Calf Stretch: - | CV |  | time
Good Morning: Cambered Bar, Safety Bar | HM LB | GL ~AB
Camel Pose: - | HF QD | CH | time
Carioca: - | QD GL AD | CV | bw
Cat and Cow: - | LB AB |  | time
Chair Incline Plank: - | AB | SH ~OB | time
Chair Pistol Squat Assist: - | QD GL | HM | bw
Child's Pose: - | LB | LT GL | time
Child's Pose Side Stretch: - | LT | OB | time
Chin Up Hold: - | LT BI | UB ~FA | time
Clapping/Plyometric Pull Up: - | LT | BI UB SH | bw
Clockwise Leg Circle: - | HF | GL AB | bw
Cobra: - | LB | AB GL | time
Cocoon Crunch: - | AB | HF | bw
Commando Pull Up: - | LT | BI OB ~FA | bw
Control Balance: - | AB | HF | time
Copenhagen Plank - Ankle: - | AD | OB AB | time
Copenhagen Plank - Knee: - | AD | OB AB | time
Corkscrew: - | AB OB | HF | bw
Cossack Squat: - | QD GL AD | HM | bw
Counter Movement Jump: - | QD GL | CV | bw
Cow Face Arms: - | SH | TR | time
Crab: - | AB TR | SH GL | time
Cross Jumps: - | QD CV | GL | bw
Cross-Body Mountain Climber: - | AB OB | SH QD | bw
Cross Country Ski: Ski Erg |  |  | cardio
Front Squat - Cross Grip: Barbell | QD GL | ~AB ~LB
Push Up - Crossed Leg: - | CH | SH TR ~AB | bw
Crow Pose: - | SH AB | TR FA | time
Curtsy Squat: - | GL QD | AD | bw
Deep Squat Hold: - | QD GL | AD | time
Deep Squat/Toe Reach: - | QD GL | HM | bw
Defensive Slide: - | QD AD | GL CV | bw
Depth Push Up: - | CH | SH TR | bw
Doorway Pec Stretch: - | CH | SH | time
Double Dead Bug: - | AB |  | bw
Double Rack Carry: Kettlebell | FA TP | QD SH ~AB | time
Double Leg Curl: - | HM | GL CV | bw
Battle Rope Double Waves: - |  |  | cardio
Double-Leg Kick: - | LB GL | HM | bw
Double-Leg Stretch: - | AB | HF SH | bw
Dowel Romanian Deadlift: - | HM GL | LB | bw
Dowel Shoulder Pass-Through: - | SH RC |  | bw
Downward Dog/Cobra: - | SH LB | HM | time
Dragon Flag Hold: - | AB |  | time
Drop Jump to Box: - | QD GL | CV | bw
Drop the Buckets: - | SH | TR | bw
Duck Walk: - | QD GL | CV | bw
ATG Split Squat: Dumbbell, Smith Machine | QD GL | HM ~AB
FFE Goblet Split Squat: Dumbbell | QD GL | HM ~AB
FFE Split Squat: Dumbbell | QD GL | HM ~AB
FFE Split Squat with Riser: Dumbbell, Kettlebell | QD GL | HM ~AB
GHD Row: Dumbbell | UB LT | BI ~LB ~FA
Kroc Row: Dumbbell, - | LT UB | BI ~LB ~FA
Larsen Bench Press: Dumbbell | CH | SH TR
PJR Pullover: Dumbbell | LT CH | TR ~FA
RFE Front-Rack Split Squat: Dumbbell | QD GL | HM ~AB
RFE Goblet Split Squat with Riser: Dumbbell | QD GL | HM ~AB
RFE Split Squat: Dumbbell | QD GL | HM ~AB
Romanian Deadlift to Row: Dumbbell | HM GL | UB LB BI ~AB ~FA
Crunch: Dumbbell | AB | 
Deficit Lunge: Dumbbell | QD GL | HM ~AB
Front Squat: Dumbbell | QD GL | ~AB ~LB
Front-Rack Split Squat with Riser: Dumbbell | QD GL | HM ~AB
Goblet Lunge: Dumbbell, Kettlebell, Plate | QD GL | HM ~AB
Goblet Split Squat with Riser: Dumbbell | QD GL | HM ~AB
Halo: Dumbbell, Plate | SH | ~AB
Heavy Partial Lateral Raise: Dumbbell | SH | ~TP
High Row: Dumbbell | UB LT | BI ~FA
Jump Squat: Dumbbell, Kettlebell, Plate | QD GL | ~CV ~AB
Kickstand Deadlift: Dumbbell, - | HM GL | LB ~AB
Kneeling Bench Hammer Curl: Dumbbell | BI FA | 
Overhead Squat: Dumbbell | QD GL | SH ~AB
Partial Lateral Raise: Dumbbell | SH | ~TP
Pistol Squat: Dumbbell | QD GL | HM ~AB
Pivot Lunge: Dumbbell, Kettlebell | QD GL | HM ~AB
Radial/Ulnar Deviation: Dumbbell | FA | 
Reverse Woodchop: Dumbbell | OB | AB SH
Split-Stance Woodchop: Dumbbell | OB | AB SH
Squat Clean: Dumbbell | QD GL HM | TP SH LB ~AB
Sumo Deadlift: Dumbbell, Kettlebell | GL HM | QD LB ~AD ~AB
Thruster/Jump: Dumbbell | QD SH | GL TR ~AB
Dynamic Chest Stretch Wall or Post: - | CH | SH | time
Eccentric Nordic Curl: - | HM | GL | bw
Eccentric Chin Up: - | LT BI | UB ~FA | bw
Elephant Walk: - | HM | CV GL | bw
Russian Twist: Stability Ball | OB | AB | bw
Dead Bug: Stability Ball | AB |  | bw
Hamstring Curl: Stability Ball | HM | GL CV | bw
Jackknife: Stability Ball | AB | HF SH | bw
Pass: Stability Ball | AB | HF | bw
Pike: Stability Ball | AB | SH HF | bw
Wall Squat: Stability Ball | QD GL |  | bw
Explosive Wall Throw: Medicine Ball | SH CH | AB TR
Extended-Arm Scissor Kicks: - | AB | HF | bw
FFE Goblet Split Squat with Riser: Dumbbell, Kettlebell | QD GL | HM ~AB
FFE Split Squat with Riser: - | QD GL | HM ~AB | bw
Figure of Four Stretch: - | GL | HF | time
Flat-Back Spine Extension: - | LB | GL | bw
IT Band: Foam Roller | GL AD |  | time
Glutes: Foam Roller | GL |  | time
Pecs: Foam Roller | CH |  | time
T-Spine Extension: Foam Roller | UB LB |  | time
Forearm Stretch: - | FA |  | time
Chest Dip - Forward Leaning: - | CH TR | SH | bw
Forwards/Backwards Leg Swing: - | HF GL | HM | bw
Frankenstein Walk: - | HF HM | QD | bw
Frog Stand: - | SH | TR AB FA | time
Frog Stretch: - | AD | GL | time
Froggers: - | QD CH | SH GL | bw
Pallof Press: GHD | OB | AB
Sorenson Hold: GHD | LB | GL HM | time
Anterior Chain and Core: GHD | AB | HF | bw
Bent-Knee Hip Extension: GHD | GL | HM LB | bw
Hip Extension: GHD | GL HM | LB | bw
Hip Flexor Stretch: GHD | HF | QD | time
Hip and Back Extension: GHD | LB GL | HM | bw
Muscle Snatch: GHD | SH TP | GL LB
Pigeon Pose: GHD, - | GL | HF AD | time
Posterior Chain: GHD | LB GL HM |  | bw
Razor Curl: GHD | HM | GL LB | bw
Reverse Back Extension: GHD | GL LB | HM | bw
Row: GHD | UB LT | BI ~LB | bw
Sit Up to Parallel: GHD | AB | HF | bw
Spinal Decompression: GHD | LB |  | time
German Hang: - | SH | LT ~FA | time
Half-Kneeling Alternating Overhead Press: Dumbbell, Kettlebell | SH | TR ~AB
Half-Kneeling Landmine Press - Opposite Side: Barbell | SH | TR CH ~AB
Half-Kneeling Landmine Press - Same Side: Barbell | SH | TR CH ~AB
Half-Kneeling Chop: Cable | OB | AB SH
Half-Kneeling Lift: Cable | OB | AB SH
Half-Kneeling Rotational Lift: Cable | OB | AB SH
Half-Kneeling Rotational Press: Cable | OB | AB CH SH
Half-Kneeling Rotational Pull: Cable | OB UB | AB BI
Half-Kneeling Rotational Push: Cable | OB CH | AB TR SH
Half-Kneeling Stability Pull: Cable | OB | AB LT
Half-Kneeling Stability Push: Cable | OB | AB CH
Half-Kneeling Overhead Press: Dumbbell | SH | TR ~AB
Half Kneeling Rotational Chop: Cable | OB | AB SH
Half-Kneeling Single-Arm Overhead Press: Dumbbell | SH | TR ~AB
Hamstring Roll: - | HM |  | time
Handstand Walk: - | SH | TR AB | bw
Hanging L Sit: - | AB | HF | time
Hanging Oblique Leg Raise: - | OB AB | HF | bw
Hanging Scapular Retraction: - | TP UB | SH | bw
Hanging Windshield Wiper: - | AB OB | HF LT | bw
Heel Flicks: - | HM CV | GL | bw
Heel Flicks with Arm Drive: - | HM CV | GL | bw
Heel Flicks with Shuffle: - | HM CV | GL | bw
High Knees with Arm Drive: - | QD HF | CV | bw
High Knees with Skipping Rope: - | QD HF | CV | bw
High Knees/Forward Shuffle: - | QD HF | CV | bw
High Leg Pull Facing Up: - | AB | HF | bw
High Leg Pulls Facing Up: - | AB | HF | bw
High Plank: - | AB | SH OB | time
High Plank Toe Touches: - | AB OB | SH | bw
High Scissor Kicks: - | AB | HF | bw
Hip CARs: - | HF GL |  | bw
Hip Flow: - | HF GL AD |  | time
Hip Rockers: - | GL AD |  | bw
Hip Stretch: - | GL HF |  | time
Hip Twist: - | OB GL |  | bw
Hundred: - | AB | HF | time
Hundred Arm Reaches: - | AB | SH | time
Hundred/Hug a Tree: - | AB | HF | time
Hundred/Leg Splits: - | AB | HF | time
Hurdle Hop: - | QD GL | CV | bw
Hurdle Step-Over: - | HF GL | CV | bw
Husafell Carry: - | FA TP SH | QD ~AB | time
Ickey Shuffle with Cones: - | QD CV | GL | bw
Incline Skull Crusher: Barbell | TR | 
Incline Squeeze Press with Bench: Dumbbell | CH | SH TR
Isometric Neck Press: - | NK |  | time
Jog on the Spot: - |  |  | cardio
Jump Rope Side Swing: - |  |  | cardio
Jump Squat/180° Turn: - | QD GL | CV ~AB | bw
Jumping Bulgarian Split Squat: - | QD GL | HM CV | bw
Jumping Pull Up: - | LT | BI ~FA | bw
Jefferson Curl: Kettlebell | LB HM | GL ~AB
RFE Front-Rack Split Squat with Riser: Kettlebell | QD GL | HM ~AB
Bent Over Row: Kettlebell | UB LT | BI ~LB ~FA
Bottom Up Curl: Kettlebell | BI FA | 
Burpee Swing: Kettlebell | QD GL HM | CH SH ~AB
Cross-Body Carry: Kettlebell | FA OB | TP ~AB | time
Curtsy Lunge: Kettlebell | GL QD | AD ~AB
Floor Press: Kettlebell | CH TR | SH
Front Raise: Kettlebell | SH | ~TP
Glute Bridge: Kettlebell | GL | HM
Side Lunge: Kettlebell | QD GL AD | ~AB
Push Press: Kettlebell | SH | TR QD ~AB
Renegade Row: Kettlebell | UB LT | BI ~AB ~FA
Reverse Lunge: Kettlebell | QD GL | HM ~AB
Waiter Carry: Kettlebell | SH FA | TP ~AB | time
Walking Lunge: Kettlebell | QD GL | HM ~AB
Kicks: - | QD HF | AB | bw
Kneel to Stand: - | QD GL | HF | bw
Kneeling Alternating Overhead Press: Dumbbell, Kettlebell | SH | TR ~AB
Kneeling Landmine Press: Barbell | SH | TR CH ~AB
Kneeling Overhead: Dumbbell | SH | TR ~AB
Kneeling Overhead Press: Kettlebell | SH | TR ~AB
Kneeling Side Kick: - | GL AD | OB | bw
Ladder Hopscotch: - | QD CV | GL | bw
Ladder in and Out: - | QD CV | GL | bw
Lat Roll: - | LT |  | time
Lateral Bear Crawl: - | SH AB | QD OB | bw
Lateral Bound: - | GL AD | QD CV | bw
Lateral Triceps Extension: Cable | TR | 
Lateral Ladder Two-Step: - | QD CV | GL | bw
Lateral Leg Swing: - | AD GL | HF | bw
Lateral Reaches: - | OB | QD GL | bw
Lateral Shuffle with Hip Circle: Band | GL AD | QD CV
Lateral Shuffles: - | QD AD | GL CV | bw
Leaning Lateral Raise: Cable | SH | ~TP
Leg Stretch: - | HM | GL | time
Log Clean and Press: - | QD GL SH | TP TR HM ~AB
Log Press: - | SH | TR TP ~AB
Hip Abductor - Low: Cable | GL | 
Low Leg Pull Facing Up: - | AB | HF | bw
Low-Leg Hundred/Leg Splits: - | AB | HF | time
Clean and Press: Medicine Ball | SH QD GL | TP TR ~AB
Granny Toss: Medicine Ball | GL QD | SH ~AB
Kneeling Overhead Throw: Medicine Ball | SH TR | AB LT
Lunge: Medicine Ball | QD GL | HM ~AB
Partner Russian Twist: Medicine Ball | OB | AB
Rotational Throw: Medicine Ball | OB | AB SH
Scoop Toss: Medicine Ball | GL QD | SH ~AB
Shotput Throw: Medicine Ball | SH CH | TR QD ~AB
Side Slam: Medicine Ball | OB | AB SH LT
Snatch: Medicine Ball | QD GL SH | TP ~AB
Squat: Medicine Ball | QD GL | ~AB
Mountain Climber with Twist: - | AB OB | SH QD | bw
Neck Harness Extension: - | NK | 
Neck Pull: - | NK | 
Neck Pull with Bent Knees: - | NK | 
Neck Rotation: - | NK |  | bw
Neck Side Stretch: - | NK |  | time
Neck-Harness Lateral Flexion: - | NK | 
Negative Push Up: - | CH | SH TR ~AB | bw
Oblique V Up: - | OB AB | HF | bw
Offset Farmer's Walk: Dumbbell, Kettlebell | FA OB | TP ~AB | time
One-Leg Pallof Lift: Cable | OB | AB
One-Leg Pallof Press: Cable | OB | AB
Open Book Stretch: - | UB CH | OB | time
Open-Leg Bent-Knee Rocker: - | AB | HF | bw
Open-Leg Rocker: - | AB | HF HM | bw
Overhead Shoulder Stretch: - | SH TR |  | time
Pancake Stretch: - | AD HM |  | time
Partner Wheelbarrow Push Up: - | CH | SH TR AB | bw
Paused Dip: - | CH TR | SH | bw
Paused Air Squat: - | QD GL | HM | bw
Paused Pull Up: - | LT | BI UB ~FA | bw
Paused Push Up: - | CH | SH TR ~AB | bw
Petersen Step-up: - | QD | GL | bw
Pivot Lunge: - | QD GL | HM ~AB | bw
Plank Alternating Leg Lift: - | AB GL | LB OB | bw
Plank Body Saw: - | AB | SH | bw
Plank Jacks: - | AB | SH QD CV | bw
Plank Jacks with Hip Dips: - | AB OB | SH | bw
Plank Jacks with Shoulder Taps: - | AB | SH OB | bw
Plank Leg Lift: - | AB GL | LB | bw
Plank Low Leg Lift: - | AB GL | LB | bw
Plank Reach: - | AB | SH | bw
Plank with Arm Lifts: - | AB | SH | bw
Plank Hip Dips: - | OB AB | SH | bw
Around the World: Plate | CH SH | 
Standing Calf Raise: Plate | CV | 
Neck Curl: Plate | NK | 
Neck Extension: Plate | NK | TP
Torture Twist: Plate | OB | AB
Plyometric Dip: - | CH TR | SH | bw
Pogo Jumps: - | CV | QD | bw
Poliquin Step-up: - | QD | GL | bw
Power Jacks: - | QD CV | SH | bw
Prone I/T/W/Y: - | UB TP | SH RC | bw
Prone I/Y/T: - | UB TP | SH | bw
Prone Neck Extension: - | NK | TP | bw
Pseudo Planche Push Up: - | CH SH | TR AB | bw
Pull Up Hold: - | LT BI | UB ~FA | time
Pulse Squat: - | QD GL | ~AB | bw
Quad Pull: - | QD | HF | time
Quad Roll: - | QD |  | time
Quad Stretch: - | QD | HF | time
Quadruped T Spine Rotation: - | UB OB |  | bw
Quick Feet: - | QD CV | GL | bw
RFE Split Squat with Riser: Kettlebell, Dumbbell | QD GL | HM ~AB
RFE Split Squat with Riser: - | QD GL | HM ~AB | bw
Radial Deviation: Dumbbell | FA | 
Reverse Nordic Curl: - | QD | HF | bw
Reverse Nordic Curl ALT: - | QD | HF | bw
Reverse Wood Chop - Split Stance: Kettlebell, Dumbbell | OB | AB SH
Reverse Lunge with Hop: - | QD GL | HM CV | bw
Reverse Plank Low Leg Raise: - | GL LB | HM | bw
Support Hold: Rings | TR CH | SH | time
Rocking: - | LB AB |  | bw
Rocking Prep: - | LB AB |  | bw
Roll Over: - | AB | LB | bw
Roll Up: - | AB | HF | bw
Rolling Back: - | AB LB |  | bw
Roman Chair Sit Up: - | AB | HF | bw
Rotational Chop: Cable | OB | AB SH
Rounded Spine Stretch: - | LB |  | time
Sandbag Bear Hug Carry: - | FA TP | QD ~AB | time
Sandbag Load to Platform: - | GL HM QD | LB TP ~FA
Sandbag to Shoulder: - | GL HM QD | LB TP SH ~FA
Saw: - | OB | AB | bw
Scapular Push Up: - | SE | SH TR | bw
Scissor Crossover Kick: - | AB | HF | bw
Scissor Kicks: - | AB | HF | bw
Scorpion Kick: - | GL LB | HM | bw
Scorpion Pose: - | HF QD | CH LB | time
Seal: - | AB | HF | bw
Seal Jumps: - | QD CV | SH | bw
Seated Knee Tuck: - | AB | HF | bw
Seated Rope Pull: - | UB LT | BI ~FA
Seated Toe Raise: - | TI | 
Serratus Wall Slide: - | SE | SH | bw
Shin Box: - | GL AD | HF | time
Shotgun Row: - | UB LT | BI ~FA
Shoulder CARs: - | SH RC |  | bw
Shoulder Bridge with Leg Circle: - | GL | HM AB | bw
Shoulder Bridge with Leg Extensions: - | GL | HM | bw
Shoulder Stretch: - | SH |  | time
Shrimp Squat: - | QD GL | HM | bw
Shuttle Runs: - |  |  | cardio
Side Kick: - | GL AD | OB | bw
Side Kneeling Leg Lift: - | GL AD |  | bw
Side Leg Lift: - | GL AD |  | bw
Side Plank Thread the Needle: - | OB | AB SH | bw
Side Stretch: - | OB LT |  | time
Side Lying Hip Raise: - | GL AD | OB | bw
Side Lying Leg Kick: - | GL AD |  | bw
Single Run with Ladder: - | QD CV | GL | bw
Single Arm Chest Press: Cable | CH | SH TR
Single Arm Cross Body Triceps Extension: Cable | TR | 
Single Arm Bench Press: Dumbbell | CH | SH TR ~AB
Single Arm Thruster: Dumbbell | QD SH | GL TR ~AB
Single Arm Incline Bench Press: Dumbbell | CH | SH TR ~AB
Single Arm Farmer's Walk: Kettlebell | FA OB | TP ~AB | time
Single Arm Swing: Kettlebell | GL HM | LB SH ~AB
Lat Pulldown - Underhand: Single Arm | LT BI | UB ~FA
Single Arm Suitcase Carry: - | FA OB | TP ~AB | time
Single Leg Box Jump: - | QD GL | CV | bw
Single Leg Chop: Cable | OB | AB GL
Single Leg Lift: Cable | OB | AB GL
Single Leg Stability Pull: Cable | OB | AB GL
Single Leg Stability Push: Cable | OB | AB GL CH
Single Leg Glute Bridge and Squeeze: - | GL | HM | bw
Single Leg Hop: - | QD GL | CV | bw
Single Leg Deadlift: Kettlebell | HM GL | LB ~AB
Single Leg Push Up: - | CH | SH TR ~AB | bw
Sled Drag/Pull: - |  |  | cardio
Sled Rope Row: - | UB LT | BI ~FA
Slider Mountain Climber: - | AB | SH QD | bw
Sliding Eccentric Leg Curl: - | HM | GL CV | bw
Single Leg Hip Thrust: Smith Machine | GL | HM
Split Squat: Smith Machine | QD GL | HM ~AB
Snatch Grip Deadlift: Barbell | HM GL LB | TP QD ~FA ~AB
Soleus Press: - | CV | 
Speed Skater: - | GL AD | QD | bw
Speed Skater with Touchdown: - | GL AD | QD HM | bw
Spiderman Mountain Climber: - | AB OB | SH QD | bw
Spiderman Plank Crunch: - | AB OB | SH | bw
Spiderman Push Up: - | CH | SH TR ~AB OB | bw
Spine Twist/Leg Lift: - | OB | AB | bw
Spine Twist/Pulse: - | OB | AB | bw
Split Jacks: - | QD GL | CV SH | bw
Split Squat Jump: - | QD GL | HM CV | bw
Split Stance Alternating Overhead Press: Kettlebell | SH | TR ~AB
Split Stance Rotational Lift: Cable | OB | AB SH
Split Stance Rotational Pull: Cable | OB UB | AB BI
Split Stance Rotational Push: Cable | OB CH | AB TR SH
Split Stance Rotational Wood Chop: Cable | OB | AB SH
Split Stance Overhead Press: Kettlebell | SH | TR ~AB
Split Stance Wood Chop: Kettlebell | OB | AB SH
Squat Jacks with Reach: - | QD GL | SH CV | bw
Stability Push: Cable | OB | AB CH
Staggered Shotgun Row: - | UB LT | BI ~FA
Standing Calf Stretch: - | CV |  | time
Standing Chest Stretch: - | CH | SH | time
Standing Hamstring Stretch: - | HM |  | time
Standing Alternating Row: Cable | UB LT | BI ~FA
Standing Single Arm Rotational Press: Cable | OB | AB CH SH
Standing Single Arm Rotational Lift: - | OB | AB SH
Star Jumps: - | QD GL | SH CV | bw
Star Plank: - | OB AB | SH | time
Stir the Pot: - | AB | SH | bw
Chest Dip - Straight Bar: - | CH TR | SH | bw
Straight Leg Kickback: Cable | GL | HM
Suitcase Carry: - | FA OB | TP ~AB | time
Superman/Flutter Kick: - | LB GL | HM | bw
Swan Dive: - | LB | GL | bw
Swim: - | LB GL | SH | bw
Swimming on the Mat: - | LB GL | SH | bw
T/Y/W Deltoid Fly: Suspension | SH UB | TP ~FA | bw
Ab Rollout/Fallout: Suspension | AB | LT SH | bw
Body Saw: Suspension | AB | SH | bw
Hamstring Runner/Curl: Suspension | HM | GL | bw
Incline Push Up: Suspension | CH | SH TR ~AB | bw
Pike: Suspension | AB | SH | bw
Pistol Squat: Suspension | QD GL | HM | bw
Plank: Suspension | AB | SH | time
Power Pull: Suspension | UB LT | BI ~FA | bw
Pull Up: Suspension | LT | BI UB ~FA | bw
Tabletop Hip Twist with Bent Knees: - | OB | AB | bw
Tabletop Stretch: - | LB | SH | time
Teaser: - | AB | HF | bw
Teaser/Jackknife: - | AB | HF | bw
Toe Reaches: - | HM | LB | bw
Toe Elevated Romanian Deadlift: Barbell | HM GL | LB ~AB
Torso Twist: - | OB | AB | bw
Towel Hang: - | FA | LT | time
Towel Pull Up: - | LT | BI FA | bw
Towel Pull Up/Hang: - | LT | BI FA | bw
Triceps Stretch: - | TR |  | time
Tuck Jump: - | QD GL | CV AB | bw
Tuck Jump with 180° Twist: - | QD GL | CV AB | bw
Tuck Planche: - | SH CH | AB TR | time
Typewriter Pull Up: - | LT | BI UB OB ~FA | bw
Tyre Flip: - | GL HM QD | LB TP SH ~FA
Ulnar Deviation: Dumbbell | FA | 
V-Bar Lat Row: - | UB LT | BI ~FA
W Drill with Cones: - | QD CV | GL | bw
Walking Knee Hug: - | HF GL | HM | bw
Wall Angel: - | SH UB |  | bw
Wall Chest Stretch: - | CH | SH | time
Wall Lat Stretch: - | LT |  | time
Wall Tap Push Up: - | CH | SH TR ~AB | bw
Lat Pulldown - Wide Neutral Grip: Cable | LT | BI UB ~FA
Windmill: - | OB | AB | bw
Windshield Wipers: - | AB OB | HF | bw
World's Greatest Stretch: - | HF GL | HM SH | time
X Drill with Cones: - | QD CV | GL | bw
Yoga Thread the Needle: - | UB | SH OB | time
Yoke Walk: - | FA TP | QD GL ~AB | time
Farmer's Walk: Kettlebell | FA TP | ~AB | time
"""

internal val GOOGLE_TABLE: String get() = GOOGLE_TABLE_0

private const val GOOGLE_ALIASES_0 = """
Active hang with pull-up bar = Dead Hang
Alternating cable chest press = Alternating Chest Press (Cable)
Alternating curtsy lunges = Alternating Curtsy Lunge
Alternating dumbbell bench press = Alternating Bench Press (Dumbbell)
Alternating dumbbell bent-over row = Alternating Bent Over Row (Dumbbell)
Alternating dumbbell renegade row = Alternating Renegade Row (Dumbbell)
Alternating incline dumbbell bench press = Alternating Incline Bench Press (Dumbbell)
Alternating leg raises = Alternating Leg Raise
American kettlebell swing = American Swing (Kettlebell)
Archer press-up = Archer Push Up
Archer pull-up pull-up bar = Archer Pull Up
ASkip = A-Skip
Assisted chin-up = Chin Up (Assisted)
Assisted pull-up = Pull Up (Assisted)
Axle bar deadlift = Deadlift (Axle Bar)
B-stance barbell Romanian deadlift = B-Stance Romanian Deadlift (Barbell)
Back lever hold = Back Lever
Back squat with safety bar = Squat (Safety Bar)
Band woodchop horizontal = Wood Chop - Horizontal (Band)
Band-assisted chin-up with squat rack = Chin Up (Assisted)
Band-assisted neck rotation = Neck Rotation (Band)
Banded bent-over row = Bent Over Row (Band)
Banded bicep curl = Bicep Curl (Band)
Banded chest fly = Chest Fly (Band)
Banded chest press = Chest Press (Band)
Banded clamshell = Clamshell (Band)
Banded donkey kick = Donkey Kick (Band)
Banded face pull = Face Pull (Band)
Banded finger extension = Finger Extension (Band)
Banded front raise = Front Raise (Band)
Banded glute bridge = Glute Bridge (Band)
Banded goblet squat = Goblet Squat (Band)
Banded hip thrust with bench = Hip Thrust (Band)
Banded incline bench press = Incline Bench Press (Band)
Banded isometric sleeper stretch = Isometric Sleeper Stretch (Band)
Banded jump squat = Jump Squat (Band)
Banded lateral raise = Lateral Raise (Band)
Banded lateral walk = Lateral Band Walks
Banded monster walk = Monster Walk
Banded no-money curl = No-Money Curl (Band)
Banded Nordic hamstring curl = Nordic Hamstring Curl
Banded partner sprint = Partner Sprint (Band)
Banded press-up = Push Up (Band)
Banded pulse squat = Pulse Squat (Band)
Banded reverse fly = Reverse Fly (Band)
Banded reverse woodchops = Reverse Wood Chop (Band)
Banded Romanian deadlift = Romanian Deadlift (Band)
Banded seated hip abduction = Seated Hip Abduction (Band)
Banded shoulder dislocate = Shoulder Dislocate (Band)
Banded shoulder press = Shoulder Press (Band)
Banded Spanish squat = Spanish Squat (Band)
Banded terminal knee extension = Terminal Knee Extension (Band)
Banded tricep extension = Triceps Extension (Band)
Banded Turkish get-up = Turkish Get Up (Band)
Banded woodchops = Wood Chop (Band)
Banded Zottman curl = Zottman Curl (Band)
Bar muscle-up = Muscle Up
Barbell 21s = 21s Bicep Curl
Barbell arm-blaster curl = Biceps Curl (with Arm Blaster) (Barbell)
Barbell back squat = Squat (Barbell)
Barbell board press = Bench Press - Board (Barbell)
Barbell Bradford press = Standing Bradford Press (Barbell)
Barbell French press = French Press (Barbell)
Barbell guillotine press (to upper chest) = Guillotine Bench Press (Barbell)
Barbell hip thrust with bench = Hip Thrust (Barbell)
Barbell landmine row = Landmine Row
Barbell landmine shoulder press = Landmine Press
Barbell landmine T-bar row = T Bar Row
Barbell lat pullover = Pullover (Barbell)
Barbell Meadows landmine row = Meadows Row
Barbell pin press (bench height) = Bench Press - Pin (Barbell)
Barbell preacher curl with adjustable bench = Preacher Curl (Barbell)
Barbell push press = Push Press
Barbell reverse-grip row = Bent Over Row - Underhand (Barbell)
Barbell shoulder press = Overhead Press (Barbell)
Barbell skull crusher/close-grip press = Skullcrusher to Close Grip Press (Barbell)
Barbell Spoto bench press = Bench Press - Spoto (Barbell)
Barbell trap-bar shrug = Shrug (Trap Bar)
Barbell tricep floor press = Triceps Floor Press (Barbell)
Battle ropes alternating waves = Battle Rope Alternating Waves
Behind-the-neck barbell push press = Behind The Neck Push Press (Barbell)
Behind-the-neck Smith machine press = Behind The Neck Press (Smith Machine)
Belt squat = Belt Squat (Machine)
Bent-knee cable kickback = Bent Knee Kickback (Cable)
Bent-over row with dumbbells = Bent Over Row (Dumbbell)
Bicep curl machine = Bicep Curl (Machine)
Bike = Cycling
Bike intervals standing = cardio
Box step-ups = Step-up
BSkip = B-Skip
Bulgarian dumbbell split squat with bench = Bulgarian Split Squat (Dumbbell)
Bulgarian split squat jumps (right) with bench = Bulgarian Split Squat Jump
Cable alternating pulldowns (bottom) with bench = Alternating Pulldown - Bottom (Cable)
Cable alternating pulldowns (top) with bench = Alternating Pulldown - Top (Cable)
Cable chop = Wood Chop (Cable)
Cable crossover fly = Cable Crossover
Cable crunch kneeling = Cable Crunch
Cable external rotation = Shoulder External Rotation (Cable)
Cable face pull with external rotation = Face Pull (Cable)
Cable hip extension = Standing Hip Extension (Cable)
Cable internal rotation = Shoulder Internal Rotation (Cable)
Cable lift = Standing Lift (Cable)
Cable rope hammer curl = Hammer Curl (with Rope) (Cable)
Cable rope overhead extension = Overhead Triceps Extension (Cable)
Cable seated alternating rows = Seated Alternating Row (Cable)
Cable straight arm pulldown rope = Straight Arm Pulldown (with Rope) (Cable)
Cable V-bar pulldown = Lat Pulldown - Neutral Grip (Cable)
Cable V-bar tricep pushdown = Triceps Pushdown (Cable - V Bar)
Cable woodchop = Wood Chop (Cable)
Calf press = Calf Press (Machine)
Cambered-bar good morning = Good Morning (Cambered Bar)
Chest fly machine = Chest Fly
Chest press machine = Chest Press (Machine)
Chest-supported barbell incline row = Incline Row (Barbell)
Chest-supported barbell row = Chest Supported Row (Barbell)
Chest-supported dumbbell row with adjustable bench = Chest Supported Row (Dumbbell)
Close-grip cable lat pulldown with bench = Lat Pulldown - Close Grip (Cable)
Close-grip cable seated row = Seated Row - Neutral Grip (Cable)
Copenhagen plank (ankle) = Copenhagen Plank - Ankle
Copenhagen plank (knee) = Copenhagen Plank - Knee
Counter movement jump (CMJ) = Counter Movement Jump
Cross-country ski with ski erg = Cross Country Ski (Ski Erg)
Cross-grip barbell front squat with squat rack = Front Squat - Cross Grip (Barbell)
Crossed-leg press-up = Push Up - Crossed Leg
Crunches = Crunch
Cuban dumbbell rotation/press = Cuban Press (Dumbbell)
Curtsy lunge = Curtsy Lunge (Bodyweight)
Deadbug = Dead Bug
Decline press-up with bench = Decline Push Up
Decline press-up with box = Decline Push Up
Decline sit-up = Sit Up (Decline)
Depth press-up = Depth Push Up
Diamond press-up = Diamond Push Up
Double kettlebell rack carry = Double Rack Carry (Kettlebell)
Double waves with battle ropes = Battle Rope Double Waves
Downward-facing dog = Downward Dog
Dumbbell around the world = Around the World
Dumbbell concentration curl with bench = Concentration Curl (Dumbbell)
Dumbbell farmer's walk = Farmers Walk (Dumbbell)
Dumbbell fly with bench = Chest Fly (Dumbbell)
Dumbbell forward lunge = Lunge (Dumbbell)
Dumbbell forward lunge biceps curl = Lunge with Bicep Curl (Dumbbell)
Dumbbell forward lunge to bicep curl = Lunge with Bicep Curl (Dumbbell)
Dumbbell hip thrust with bench = Hip Thrust (Dumbbell)
Dumbbell incline hammer curl with adjustable bench = Incline Hammer Curl (Dumbbell)
Dumbbell lat pullover = Pullover (Dumbbell)
Dumbbell lateral lunge = Side Lunge (Dumbbell)
Dumbbell pinwheel curl = Cross Body Hammer Curl (Dumbbell)
Dumbbell pullover with bench = Pullover (Dumbbell)
Dumbbell rear delt flyes = Reverse Fly (Dumbbell)
Dumbbell Russian twist = Russian Twist (Weighted)
Dumbbell seated calf raise with bench = Seated Calf Raise (Dumbbell)
Dumbbell skull crusher with bench = Skullcrusher (Dumbbell)
Dumbbell squeeze press with bench = Squeeze Press (Dumbbell)
Dumbbell walking lunge with bicep curl = Lunge with Bicep Curl (Dumbbell)
Elbow-to-knee crunch = Elbow to Knee
Elliptical cross trainer = Elliptical Machine
Exercise ball back extension = Back Extension on Exercise Ball (Stability Ball)
Exercise ball crunches = Crunch (Stability Ball)
Exercise ball dead bug = Dead Bug (Stability Ball)
Exercise ball dumbbell chest press = Press on Exercise Ball (Dumbbell)
Exercise ball hamstring curl = Hamstring Curl (Stability Ball)
Exercise ball jackknife = Jackknife (Stability Ball)
Exercise ball pass = Pass (Stability Ball)
Exercise ball pike = Pike (Stability Ball)
Exercise ball press-up = Push Up (on Stability Ball) (Stability Ball)
Exercise ball Russian twist = Russian Twist (Stability Ball)
Exercise ball wall squat = Wall Squat (Stability Ball)
Exercise bike = Cycling (Indoor)
Explosive medicine ball chest pass = Chest Pass (Medicine Ball)
Explosive medicine ball wall throw = Explosive Wall Throw (Medicine Ball)
Face pull with cable machine = Face Pull (Cable)
Farmer's walk with kettlebell = Farmer's Walk (Kettlebell)
FFE dumbbell goblet split squat with riser = FFE Goblet Split Squat with Riser (Dumbbell)
FFE dumbbell split squat with riser = FFE Split Squat with Riser (Dumbbell)
FFE kettlebell goblet split squat with riser = FFE Goblet Split Squat with Riser (Kettlebell)
FFE kettlebell split squat with riser = FFE Split Squat with Riser (Kettlebell)
Fire hydrant = Fire Hydrant (Bodyweight)
Foam roll glutes = Glutes (Foam Roller)
Foam roll IT band = IT Band (Foam Roller)
Foam roll pecs = Pecs (Foam Roller)
Foam roller T-spine extension = T-Spine Extension (Foam Roller)
Forearm plank = Plank
Forward lunge = Lunge (Bodyweight)
Forward-leaning dips with dip bar = Chest Dip - Forward Leaning
Frog jump = Frog Jumps
GHD anterior chain and core = Anterior Chain and Core (GHD)
GHD back extension = Back Extension
GHD bent-knee hip extension = Bent-Knee Hip Extension (GHD)
GHD glute-ham raise = Glute Ham Raise
GHD hip extension = Hip Extension (GHD)
GHD hip flexor stretch = Hip Flexor Stretch (GHD)
GHD hip-and-back extension = Hip and Back Extension (GHD)
GHD muscle snatch = Muscle Snatch (GHD)
GHD oblique crunch = Oblique Crunch
GHD Pallof press = Pallof Press (GHD)
GHD pigeon pose = Pigeon Pose (GHD)
GHD posterior chain = Posterior Chain (GHD)
GHD razor curl = Razor Curl (GHD)
GHD reverse back extension = Reverse Back Extension (GHD)
GHD row = Row (GHD)
GHD Russian twist = Russian Twist
GHD sit-up = Sit Up
GHD sit-up to parallel = Sit Up to Parallel (GHD)
GHD Sorenson hold = Sorenson Hold (GHD)
GHD spinal decompression = Spinal Decompression (GHD)
Glute kickback = Glute Kickback (Machine)
Good mornings with barbell = Good Morning (Barbell)
Guillotine press with barbell = Guillotine Bench Press (Barbell)
Hack squat calf raise = Hack Calf Raise (Machine)
Half-kneeling alternating dumbbell overhead press = Half-Kneeling Alternating Overhead Press (Dumbbell)
Half-kneeling alternating kettlebell overhead press = Half-Kneeling Alternating Overhead Press (Kettlebell)
Half-kneeling barbell landmine press (opposite side) = Half-Kneeling Landmine Press - Opposite Side (Barbell)
Half-kneeling barbell landmine press (same side) = Half-Kneeling Landmine Press - Same Side (Barbell)
Half-kneeling cable chop = Half-Kneeling Chop (Cable)
Half-kneeling cable lift = Half-Kneeling Lift (Cable)
Half-kneeling cable Pallof press = Pallof Press (Cable)
Half-kneeling cable rotational lift = Half-Kneeling Rotational Lift (Cable)
Half-kneeling cable rotational press = Half-Kneeling Rotational Press (Cable)
Half-kneeling cable rotational pull = Half-Kneeling Rotational Pull (Cable)
Half-kneeling cable rotational push = Half-Kneeling Rotational Push (Cable)
Half-kneeling cable stability pull = Half-Kneeling Stability Pull (Cable)
Half-kneeling cable stability push = Half-Kneeling Stability Push (Cable)
Half-kneeling dumbbell overhead press = Half-Kneeling Overhead Press (Dumbbell)
Half-kneeling rotational chop with cable machine = Half Kneeling Rotational Chop (Cable)
Half-kneeling single-arm dumbbell overhead press = Half-Kneeling Single-Arm Overhead Press (Dumbbell)
Hand gripper = Gripper Hands (Machine)
Handstand press-up = Handstand Push Up
Hanging knee raise with pull-up bar = Hanging Knee Raise
Hanging oblique knee raise with pull-up bar = Hanging Oblique Knee Raise
Hanging oblique leg raise with pull-up bar = Hanging Oblique Leg Raise
Hanging pull-up bar leg raise = Hanging Leg Raise
Hanging windscreen wiper = Hanging Windshield Wiper
High row = High Row (Machine)
High side plank = Side Plank
Hip abduction = Hip Abductor (Machine)
Hollow body hold = Hollow Hold
"""

private const val GOOGLE_ALIASES_1 = """
Hollow body rocks = Hollow Rock
Inchworms = Inchworm
Incline barbell bench press = Incline Bench Press (Barbell)
Incline barbell skull crusher = Incline Skull Crusher (Barbell)
Incline bench press-up = Incline Push Up
Incline box press-up = Incline Push Up (on Box)
Incline chest press = Incline Chest Press (Machine)
Incline dumbbell bench press = Incline Bench Press (Dumbbell)
Incline dumbbell curl = Incline Curl (Dumbbell)
Incline dumbbell squeeze press with bench = Incline Squeeze Press with Bench (Dumbbell)
Incline leg press = Leg Press (Machine)
Iso-lateral machine row = Iso-Lateral Row (Machine)
Jack knives = Jackknife Sit Up
Jump lunges = Lunge with Jump
Jumping Bulgarian split squat (left) with bench = Jumping Bulgarian Split Squat
Jumping pull-up with squat rack = Jumping Pull Up
Kettlebell forward lunge = Lunge (Kettlebell)
Kettlebell lateral lunge = Side Lunge (Kettlebell)
Kettlebell reverse woodchops = Reverse Wood Chop (Kettlebell)
Kettlebell single-arm clean = Clean (Kettlebell)
Kipping handstand press-up = Handstand Push Up
Kipping/Butterfly pull-up = Kipping Pull Up
Knee press-up = Push Up (Knees)
Kneeling alternating dumbbell overhead press = Kneeling Alternating Overhead Press (Dumbbell)
Kneeling alternating kettlebell overhead press = Kneeling Alternating Overhead Press (Kettlebell)
Kneeling barbell landmine press = Kneeling Landmine Press (Barbell)
Kneeling dumbbell overhead = Kneeling Overhead (Dumbbell)
Kneeling kettlebell overhead press = Kneeling Overhead Press (Kettlebell)
L-sit pull-up = L Pull Up
Landmine barbell bent-over row = Landmine Row
Lateral cable tricep extension = Lateral Triceps Extension (Cable)
Lateral shuffle with hip circle band = Lateral Shuffle with Hip Circle (Band)
Leaning cable lateral raise = Leaning Lateral Raise (Cable)
Leg abductor = Hip Abductor (Machine)
Leg adductor = Hip Adductor (Machine)
Leg extension machine = Leg Extension (Machine)
Leg press horizontal = Horizontal Leg Press (Machine)
Leg press narrow stance = Leg Press - Narrow Stance (Machine)
Leg raises = Flat Leg Raise
Low cable hip abduction = Hip Abductor - Low (Cable)
Low side plank = Side Plank
Lying leg curl machine = Lying Leg Curl (Machine)
Man maker = Man Maker (Dumbbell)
Medicine ball clean and press = Clean and Press (Medicine Ball)
Medicine ball granny toss = Granny Toss (Medicine Ball)
Medicine ball jump squat = Jump Squat (Medicine Ball)
Medicine ball kneeling overhead throw = Kneeling Overhead Throw (Medicine Ball)
Medicine ball lunge = Lunge (Medicine Ball)
Medicine ball overhead throw = Catch and Overhead Throw (Medicine Ball)
Medicine ball partner chest pass = Chest Pass (Medicine Ball)
Medicine ball partner Russian twist = Partner Russian Twist (Medicine Ball)
Medicine ball rotational throw = Rotational Throw (Medicine Ball)
Medicine ball scoop toss = Scoop Toss (Medicine Ball)
Medicine ball shotput throw = Shotput Throw (Medicine Ball)
Medicine ball side slam = Side Slam (Medicine Ball)
Medicine ball slam = Overhead Slam (Medicine Ball)
Medicine ball snatch = Snatch (Medicine Ball)
Medicine ball squat = Squat (Medicine Ball)
Medicine ball toe touch = Toe Touch
Mini band squat = Squat (Band)
Neck rotation (right) = Neck Rotation
Neck rotations = Neck Rotation
Negative press-up = Negative Push Up
Offset farmer's walk with dumbbells' = Offset Farmer's Walk (Dumbbell)
Offset farmer's walk with kettlebell = Offset Farmer's Walk (Kettlebell)
One-leg pallof lift with cable machine = One-Leg Pallof Lift (Cable)
One-leg pallof press with cable machine = One-Leg Pallof Press (Cable)
Pallof press with cable machine = Pallof Press (Cable)
Pallof press/lift with cable machine = Pallof Press (Cable)
Parallel bar leg raise = Vertical Leg Raise (on Parallel Bars)
Partner floor glute-ham raise = Glute Ham Raise
Partner wheelbarrow press-up = Partner Wheelbarrow Push Up
Paused barbell bench press = Pause Bench Press (Barbell)
Pendulum squat = Pendulum Squat (Machine)
Pike press-up = Pike Push Up
Planche lean = Lean Planche
Plank alternating leg lifts = Plank Alternating Leg Lift
Plank/Hip dips = Plank Hip Dips
Plank/leg lifts = Plank Leg Lift
Plate around the world = Around the World (Plate)
Plate calf raise = Standing Calf Raise (Plate)
Plate chin-up = Chin Up (Weighted)
Plate goblet lunge = Goblet Lunge (Plate)
Plate goblet squat = Goblet Squat (Plate)
Plate jump squat = Jump Squat (Plate)
Plate neck curl = Neck Curl (Plate)
Plate neck extension = Neck Extension (Plate)
Plate plank = Plank (Weighted)
Plate pulse squat = Pulse Squat (Plate)
Plate Svend press = Svend Press
Plate torture twist = Torture Twist (Plate)
Plate-loaded chest press = Chest Press (Machine)
Plate-loaded high row = High Row (Machine)
Plyo press-up = Plyo Push Up
Plyo press-up/clap = Clap Push Up
Preacher curl = Preacher Curl (Machine)
Pseudo-planche press-up = Pseudo Planche Push Up
Pullover machine = Pullover (Machine)
Radial deviation with dumbbell = Radial Deviation (Dumbbell)
Reverse back extension = Reverse Hyperextension
Reverse barbell curl = Reverse Curl (Barbell)
Reverse fly = Reverse Fly (Machine)
Reverse kettlebell woodchops/split stance = Reverse Wood Chop - Split Stance (Kettlebell)
Reverse plank low leg raises = Reverse Plank Low Leg Raise
Reverse woodchops/dumbbell split stance = Reverse Wood Chop - Split Stance (Dumbbell)
Reverse-grip barbell bench press = Bench Press - Reverse Grip (Barbell)
Reverse-grip cable pulldown with bench = Lat Pulldown - Underhand (Cable)
Reverse-grip cable pushdown = Reverse Grip Pushdown (Cable)
RFE kettlebell split squat with riser = RFE Split Squat with Riser (Kettlebell)
RFE split squat with dumbbell and riser = RFE Split Squat with Riser (Dumbbell)
Ring muscle-up = Muscle Up
Ring support hold = Support Hold (Rings)
Romanian deadlift = Romanian Deadlift (Barbell)
Romanian deadlift with kettlebell = Romanian Deadlift (Kettlebell)
Romanian deadlift with kettlebells = Romanian Deadlift (Kettlebell)
Rope climb = Rope Climb (Rope)
Rotational cable chop = Rotational Chop (Cable)
Safety bar squat = Squat (Safety Bar)
Safety squat bar good morning = Good Morning (Safety Bar)
Scapular press-up = Scapular Push Up
Seated band row = Seated Row (Band)
Seated banded knee abduction = Seated Hip Abduction (Band)
Seated barbell good morning = Seated Good Morning (Barbell)
Seated cable chest press = Seated Chest Press (Cable)
Seated cable shoulder press = Shoulder Press (Cable)
Seated dumbbell overhead press = Seated Overhead Press (Dumbbell)
Seated leg curl = Seated Leg Curl (Machine)
Seated Smith machine behind the neck press = Behind The Neck Press (Smith Machine)
Seated Smith machine shoulder press = Seated Shoulder Press (Smith Machine)
Shoulder bridge single-leg raise = Single Leg Bridge
Shoulder press = Shoulder Press (Machine)
Shoulder stretch (right) = Shoulder Stretch
Shrug = Shrug (Machine)
Side lunge = Side Lunge (Bodyweight)
Side-lying dumbbell external rotation = Lying External Shoulder Rotation (Dumbbell)
Side-lying dumbbell lateral raise = Lying Lateral Raise (Dumbbell)
Single leg curl = Standing Single Leg Curl
Single leg extension = Leg Extension (Single Leg)
Single leg press = Leg Press (Single Leg)
Single-arm cable chest press = Single Arm Chest Press (Cable)
Single-arm cable cross-body extension = Single Arm Cross Body Triceps Extension (Cable)
Single-arm cable seated row = Single Arm Row (Cable)
Single-arm dumbbell bench press = Single Arm Bench Press (Dumbbell)
Single-arm dumbbell bent-over row = Bent Over One Arm Row (Dumbbell)
Single-arm dumbbell clean = Clean (Dumbbell)
Single-arm dumbbell snatch = One Arm Snatch (Dumbbell)
Single-arm dumbbell thruster = Single Arm Thruster (Dumbbell)
Single-arm incline dumbbell bench press = Single Arm Incline Bench Press (Dumbbell)
Single-arm kettlebell farmer's walk = Single Arm Farmer's Walk (Kettlebell)
Single-arm kettlebell snatch = One Arm Snatch (Kettlebell)
Single-arm kettlebell swing = Single Arm Swing (Kettlebell)
Single-arm kneeling pulldown = Lat Pulldown (Single Arm)
Single-arm reverse-grip cable lat pulldown = Lat Pulldown - Underhand (Single Arm)
Single-leg barbell deadlift = Single Leg Deadlift (Barbell)
Single-leg box jumps = Single Leg Box Jump
Single-leg cable chop = Single Leg Chop (Cable)
Single-leg cable lift = Single Leg Lift (Cable)
Single-leg cable stability pull = Single Leg Stability Pull (Cable)
Single-leg cable stability push = Single Leg Stability Push (Cable)
Single-leg calf raise = Single Leg Standing Calf Raise (Bodyweight)
Single-leg dumbbell deadlift = Single Leg Deadlift (Dumbbell)
Single-leg dumbbell Romanian deadlift = Single Leg Romanian Deadlift (Dumbbell)
Single-leg kettlebell deadlift = Single Leg Deadlift (Kettlebell)
Single-leg press-up = Single Leg Push Up
Skipping rope = Jump Rope
Slider mountain climbers = Slider Mountain Climber
Smith machine back squat = Squat (Smith Machine)
Smith machine close-grip bench press = Bench Press - Close Grip (Smith Machine)
Smith machine shoulder press standing = Overhead Press (Smith Machine)
Snatch-grip barbell deadlift = Snatch Grip Deadlift (Barbell)
Spiderman press-up = Spiderman Push Up
Spine twists = Spine Twist
Split squat = Split Squat (Bodyweight)
Split-stance alternating kettlebell overhead press = Split Stance Alternating Overhead Press (Kettlebell)
Split-stance cable rotational lift = Split Stance Rotational Lift (Cable)
Split-stance cable rotational pull = Split Stance Rotational Pull (Cable)
Split-stance cable rotational push = Split Stance Rotational Push (Cable)
Split-stance cable rotational woodchop = Split Stance Rotational Wood Chop (Cable)
Split-stance kettlebell overhead press = Split Stance Overhead Press (Kettlebell)
Split-stance kettlebell woodchop = Split Stance Wood Chop (Kettlebell)
Squat = Squat (Bodyweight)
Squat jumps = Jump Squat
Squat rack band-assisted pull-up = Assisted Pull Up (Band)
Squat rack barbell decline bench press = Decline Bench Press (Barbell)
Squat rack ring dip = Ring Dip
Stability cable push = Stability Push (Cable)
Standing cable row = Standing Row (v Bar) (Cable)
Standing dumbbell Arnold press = Arnold Press (Dumbbell)
Standing leg curl = Standing Leg Curl (Machine)
Standing rows alternating with cable machine = Standing Alternating Row (Cable)
Standing single-arm cable rotational press = Standing Single Arm Rotational Press (Cable)
Standing single-arm dumbbell preacher curl = Standing Preacher Curl (Dumbbell)
Step-ups with bench = Step-up
Stiff-leg barbell deadlift = Stiff Leg Deadlift (Barbell)
Straight bar dip = Chest Dip - Straight Bar
Straight-arm pulldown = Straight Arm Pulldown (Cable)
Straight-bar cable pushdown = Triceps Pushdown (Cable - Straight Bar)
Straight-leg cable kickback = Straight Leg Kickback (Cable)
Sumo squat with barbell = Sumo Squat (Barbell)
Suspended tricep extension = Triceps Extension (Suspension)
Tibialis raise = Tibialis Raise (Machine)
Toe-elevated barbell Romanian deadlift = Toe Elevated Romanian Deadlift (Barbell)
Treadmill run = Running (Treadmill)
Treadmill walk = Walking (Treadmill)
Tricep bench dips = Bench Dip
Tricep dip from bench = Bench Dip
Tricep press-up = Push Up - Close Grip
Tricep rope pushdown = Triceps Pushdown (Cable - Rope)
Tricep stretches = Triceps Stretch
TRX ab rollout/fallout = Ab Rollout/Fallout (Suspension)
TRX body saw = Body Saw (Suspension)
TRX chest fly = Chest Fly (Suspension)
TRX hamstring runner/curl = Hamstring Runner/Curl (Suspension)
TRX incline press-up = Incline Push Up (Suspension)
TRX pike = Pike (Suspension)
TRX pistol squat = Pistol Squat (Suspension)
TRX plank = Plank (Suspension)
TRX power pull = Power Pull (Suspension)
TRX press-up = Push Up (Suspension)
TRX pull-up = Pull Up (Suspension)
TRX row = Row (Suspension)
TRX squat = Squat (Suspension)
TRX suspended triceps extension = Triceps Extension (Suspension)
TRX T/Y/W deltoid fly = T/Y/W Deltoid Fly (Suspension)
Ulnar deviation with dumbbells = Ulnar Deviation (Dumbbell)
V-squat = V Squat (Machine)
V-ups = V Up
Vertical chest press = Chest Press (Machine)
Walk = Walking
Wall sit with squat rack = Wall Sit
Wall tap press-up = Wall Tap Push Up
Weight plate halo = Halo (Plate)
Weighted back extension = Back Extension (Weighted)
Weighted sit-up = Sit Up (Weighted)
Wide neutral-grip bar lat pulldown = Lat Pulldown - Wide Neutral Grip (Cable)
Wide press-up = Wide Push Up
Wide-grip barbell curl = Bicep Curl - Wide Grip (Barbell)
Wide-grip cable lat pulldown with bench = Lat Pulldown - Wide Grip (Cable)
"""

internal val GOOGLE_ALIASES: String get() = GOOGLE_ALIASES_0 + GOOGLE_ALIASES_1
