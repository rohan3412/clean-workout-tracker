package clean.core

/*
 * Snapshots taken before the Google Health names were added (LibraryGoogleHealth.kt), for
 * GoogleHealthMappingTest: where each other name for an exercise led, and which names the library had.
 */
internal const val ALIAS_SNAPSHOT = """
Skull Crusher (Barbell) => Skullcrusher (Barbell)
Skull Crusher (Dumbbell) => Skullcrusher (Dumbbell)
Skull Crusher (EZ Bar) => Skullcrusher (EZ Bar)
Lying Triceps Extension (EZ Bar) => Skullcrusher (EZ Bar)
Biceps Curl (Barbell) => Bicep Curl (Barbell)
Biceps Curl (Dumbbell) => Bicep Curl (Dumbbell)
Biceps Curl (Cable) => Bicep Curl (Cable)
Biceps Curl (Machine) => Bicep Curl (Machine)
Biceps Curl (EZ Bar) => Bicep Curl (EZ Bar)
Biceps Curl (Band) => Bicep Curl (Band)
Biceps Curl (Kettlebell) => Bicep Curl (Kettlebell)
Biceps Curl (Plate) => Bicep Curl (Plate)
Biceps Curl (Suspension) => Bicep Curl (Suspension)
Biceps Curl (Smith Machine) => Bicep Curl (Smith Machine)
Tricep Pushdown (Cable - Straight Bar) => Triceps Pushdown (Cable - Straight Bar)
Tricep Pushdown (Cable - Rope) => Triceps Pushdown (Cable - Rope)
Tricep Pushdown (Cable - V Bar) => Triceps Pushdown (Cable - V Bar)
Tricep Pushdown (Band) => Triceps Pushdown (Band)
Tricep Pushdown (v Bar) (with Arm Blaster) (Cable) => Triceps Pushdown (v Bar) (with Arm Blaster) (Cable)
Tricep Extension => Triceps Extension
Tricep Extension (Dumbbell) => Triceps Extension (Dumbbell)
Tricep Extension (Cable) => Triceps Extension (Cable)
Tricep Extension (Barbell) => Triceps Extension (Barbell)
Tricep Extension (Machine) => Triceps Extension (Machine)
Tricep Extension (EZ Bar) => Triceps Extension (EZ Bar)
Tricep Extension (Band) => Triceps Extension (Band)
Tricep Extension (Suspension) => Triceps Extension (Suspension)
Tricep Kickback (Dumbbell) => Triceps Kickback (Dumbbell)
Tricep Kickback (Band) => Triceps Kickback (Band)
Tricep Dip => Triceps Dip
Tricep Dip (Assisted) => Triceps Dip (Assisted)
Tricep Dip (Weighted) => Triceps Dip (Weighted)
Tricep Dip (bench Leg) => Triceps Dip (bench Leg)
Tricep Dip (between Benches) => Triceps Dip (between Benches)
Tricep Dip (kneeling) (Assisted) => Triceps Dip (kneeling) (Assisted)
Overhead Tricep Extension (Dumbbell) => Overhead Triceps Extension (Dumbbell)
Overhead Tricep Extension (Cable) => Overhead Triceps Extension (Cable)
Overhead Tricep Extension (EZ Bar) => Overhead Triceps Extension (EZ Bar)
Overhead Tricep Extension (Band) => Overhead Triceps Extension (Band)
Overhead Tricep Extension (rope Attachment) (Cable) => Overhead Triceps Extension (rope Attachment) (Cable)
French Press (Dumbbell) => Overhead Triceps Extension (Dumbbell)
French Press (Cable) => Overhead Triceps Extension (Cable)
French Press (EZ Bar) => Overhead Triceps Extension (EZ Bar)
French Press (Band) => Overhead Triceps Extension (Band)
French Press (rope Attachment) (Cable) => Overhead Triceps Extension (rope Attachment) (Cable)
Military Press (Barbell) => Overhead Press (Barbell)
Military Press (Dumbbell) => Overhead Press (Dumbbell)
Military Press (Smith Machine) => Overhead Press (Smith Machine)
Military Press (Cable) => Overhead Press (Cable)
Military Press (Kettlebell) => Overhead Press (Kettlebell)
Military Press (Band) => Overhead Press (Band)
OHP (Barbell) => Overhead Press (Barbell)
OHP (Dumbbell) => Overhead Press (Dumbbell)
OHP (Smith Machine) => Overhead Press (Smith Machine)
OHP (Cable) => Overhead Press (Cable)
OHP (Kettlebell) => Overhead Press (Kettlebell)
OHP (Band) => Overhead Press (Band)
RDL (Barbell) => Romanian Deadlift (Barbell)
RDL (Dumbbell) => Romanian Deadlift (Dumbbell)
RDL (Smith Machine) => Romanian Deadlift (Smith Machine)
RDL (Kettlebell) => Romanian Deadlift (Kettlebell)
RDL (Cable) => Romanian Deadlift (Cable)
Stiff Legged Deadlift (Barbell) => Stiff Leg Deadlift (Barbell)
Stiff Legged Deadlift (Dumbbell) => Stiff Leg Deadlift (Dumbbell)
Stiff Legged Deadlift (Band) => Stiff Leg Deadlift (Band)
Hyperextension => Back Extension
Hyperextension (Weighted) => Back Extension (Weighted)
Hyperextension (Machine) => Back Extension (Machine)
Pulldown (Machine) => Lat Pulldown (Machine)
Pulldown (Band) => Lat Pulldown (Band)
Pulldown (Single Arm) => Lat Pulldown (Single Arm)
Lat Pull Down (Machine) => Lat Pulldown (Machine)
Lat Pull Down (Cable) => Lat Pulldown (Cable)
Lat Pull Down (Band) => Lat Pulldown (Band)
Lat Pull Down (Single Arm) => Lat Pulldown (Single Arm)
Pullup => Pull Up
Pullup (Assisted) => Pull Up (Assisted)
Pullup (Weighted) => Pull Up (Weighted)
Pullup (Band) => Pull Up (Band)
Pullup (Neutral Grip) => Pull Up (Neutral Grip)
Pullup (Wide Grip) => Pull Up (Wide Grip)
Pullup (Close Grip) => Pull Up (Close Grip)
Chinup => Chin Up
Chinup (Assisted) => Chin Up (Assisted)
Chinup (Weighted) => Chin Up (Weighted)
Pushup => Push Up
Pushup (Weighted) => Push Up (Weighted)
Pushup (Knees) => Push Up (Knees)
Pushup (Band) => Push Up (Band)
Pushup (on Stability Ball) (Stability Ball) => Push Up (on Stability Ball) (Stability Ball)
Pushup (wall) => Push Up (wall)
Pushup (Suspension) => Push Up (Suspension)
Press Up => Push Up
Press Up (Weighted) => Push Up (Weighted)
Press Up (Knees) => Push Up (Knees)
Press Up (Band) => Push Up (Band)
Press Up (on Stability Ball) (Stability Ball) => Push Up (on Stability Ball) (Stability Ball)
Press Up (wall) => Push Up (wall)
Press Up (Suspension) => Push Up (Suspension)
Situp => Sit Up
Situp (Weighted) => Sit Up (Weighted)
Situp (Decline) => Sit Up (Decline)
Situp (Assisted) => Sit Up (Assisted)
Step Up => Step-up
Step Up (Dumbbell) => Step-up (Dumbbell)
Step Up (Barbell) => Step-up (Barbell)
Rear Delt Reverse Fly (Dumbbell) => Reverse Fly (Dumbbell)
Rear Delt Reverse Fly (Cable) => Reverse Fly (Cable)
Rear Delt Reverse Fly (Machine) => Reverse Fly (Machine)
Rear Delt Reverse Fly (Band) => Reverse Fly (Band)
Rear Delt Fly (Dumbbell) => Reverse Fly (Dumbbell)
Rear Delt Fly (Cable) => Reverse Fly (Cable)
Rear Delt Fly (Machine) => Reverse Fly (Machine)
Rear Delt Fly (Band) => Reverse Fly (Band)
Reverse Pec Deck (Dumbbell) => Reverse Fly (Dumbbell)
Reverse Pec Deck (Cable) => Reverse Fly (Cable)
Reverse Pec Deck (Machine) => Reverse Fly (Machine)
Reverse Pec Deck (Band) => Reverse Fly (Band)
Pec Fly => Chest Fly
Pec Fly (Dumbbell) => Chest Fly (Dumbbell)
Pec Fly (Machine) => Chest Fly (Machine)
Pec Fly (Cable) => Chest Fly (Cable)
Pec Fly (Band) => Chest Fly (Band)
Pec Fly (Suspension) => Chest Fly (Suspension)
Fly => Chest Fly
Fly (Machine) => Chest Fly (Machine)
Fly (Cable) => Chest Fly (Cable)
Fly (Band) => Chest Fly (Band)
Fly (Suspension) => Chest Fly (Suspension)
Calf Raise (Machine) => Standing Calf Raise (Machine)
Calf Raise (Smith Machine) => Standing Calf Raise (Smith Machine)
Calf Raise (Dumbbell) => Standing Calf Raise (Dumbbell)
Calf Raise (Barbell) => Standing Calf Raise (Barbell)
Calf Raise (Bodyweight) => Standing Calf Raise (Bodyweight)
Calf Raise (on a Staircase) => Standing Calf Raise (on a Staircase)
Calf Raise (Cable) => Standing Calf Raise (Cable)
Leg Curl (Machine) => Lying Leg Curl (Machine)
Hamstring Curl (Machine) => Lying Leg Curl (Machine)
Hip Abduction (Machine) => Hip Abductor (Machine)
Hip Abduction (Cable) => Hip Abductor (Cable)
Hip Abduction (Band) => Hip Abductor (Band)
Hip Adduction (Machine) => Hip Adductor (Machine)
Hip Adduction (Band) => Hip Adductor (Band)
Side Raise (Dumbbell) => Lateral Raise (Dumbbell)
Side Raise (Cable) => Lateral Raise (Cable)
Side Raise (Machine) => Lateral Raise (Machine)
Side Raise (Band) => Lateral Raise (Band)
Lateral Raises (Dumbbell) => Lateral Raise (Dumbbell)
Lateral Raises (Cable) => Lateral Raise (Cable)
Lateral Raises (Machine) => Lateral Raise (Machine)
Lateral Raises (Band) => Lateral Raise (Band)
Shrugs (Barbell) => Shrug (Barbell)
Shrugs (Dumbbell) => Shrug (Dumbbell)
Shrugs (Smith Machine) => Shrug (Smith Machine)
Shrugs (Machine) => Shrug (Machine)
Shrugs (Cable) => Shrug (Cable)
Shrugs (Trap Bar) => Shrug (Trap Bar)
Shrugs (Band) => Shrug (Band)
Lunges (Barbell) => Lunge (Barbell)
Lunges (Dumbbell) => Lunge (Dumbbell)
Lunges (Bodyweight) => Lunge (Bodyweight)
Lunges (Smith Machine) => Lunge (Smith Machine)
Lunges (Kettlebell) => Lunge (Kettlebell)
Squats (Barbell) => Squat (Barbell)
Squats (Dumbbell) => Squat (Dumbbell)
Squats (Smith Machine) => Squat (Smith Machine)
Squats (Machine) => Squat (Machine)
Squats (Bodyweight) => Squat (Bodyweight)
Squats (Band) => Squat (Band)
Squats (Kettlebell) => Squat (Kettlebell)
Squats (Suspension) => Squat (Suspension)
Squats (on Knees) (Barbell) => Squat (on Knees) (Barbell)
Squats (Weighted) => Squat (Weighted)
Deadlifts (Barbell) => Deadlift (Barbell)
Deadlifts (Dumbbell) => Deadlift (Dumbbell)
Deadlifts (Smith Machine) => Deadlift (Smith Machine)
Deadlifts (Kettlebell) => Deadlift (Kettlebell)
Deadlifts (Band) => Deadlift (Band)
Deadlifts (Cable) => Deadlift (Cable)
Deadlifts (Machine) => Deadlift (Machine)
Wrist Extension (Barbell) => Reverse Wrist Curl (Barbell)
Wrist Extension (Dumbbell) => Reverse Wrist Curl (Dumbbell)
Wrist Extension (Cable) => Reverse Wrist Curl (Cable)
Wrist Extension (Band) => Reverse Wrist Curl (Band)
Good Mornings (Barbell) => Good Morning (Barbell)
Good Mornings (Smith Machine) => Good Morning (Smith Machine)
Good Mornings (Band) => Good Morning (Band)
Face Pulls (Cable) => Face Pull (Cable)
Face Pulls (Band) => Face Pull (Band)
Hip Thrusts (Barbell) => Hip Thrust (Barbell)
Hip Thrusts (Dumbbell) => Hip Thrust (Dumbbell)
Hip Thrusts (Machine) => Hip Thrust (Machine)
Hip Thrusts (Smith Machine) => Hip Thrust (Smith Machine)
Hip Thrusts (Bodyweight) => Hip Thrust (Bodyweight)
Hip Thrusts (Band) => Hip Thrust (Band)
Single Leg Calf Raise (Bodyweight) => Single Leg Standing Calf Raise (Bodyweight)
Single Leg Calf Raise (Barbell) => Single Leg Standing Calf Raise (Barbell)
Single Leg Calf Raise (Machine) => Single Leg Standing Calf Raise (Machine)
Rear Delt Reverse Fly Single Arm (Cable) => Reverse Fly Single Arm (Cable)
Back Squat => Squat (Barbell)
Full Squat => Squat (Barbell)
Barbell Row => Bent Over Row (Barbell)
Dumbbell Row => Bent Over One Arm Row (Dumbbell)
Flat Bench Press => Bench Press (Barbell)
Close Grip Bench Press => Bench Press - Close Grip (Barbell)
Close Grip Bench Press (Barbell) => Bench Press - Close Grip (Barbell)
Wide Grip Bench Press (Barbell) => Bench Press - Wide Grip (Barbell)
Assault Bike => Air Bike
Around The World (Dumbbell) => Around the World
Kettlebell Around the World => Around the World (Kettlebell)
Assisted Pistol Squats => Pistol Squat (Assisted)
Back Extension (Hyperextension) => Back Extension
Back Extension (Weighted Hyperextension) => Back Extension (Weighted)
Band Pullaparts => Band Pull Apart
Behind the Back Bicep Wrist Curl (Barbell) => Behind the Back Wrist Curl (Barbell)
Behind the Back Curl (Cable) => Bayesian Curl (Cable)
Butterfly (Pec Deck) => Pec Deck (Machine)
Cable Core Palloff Press => Pallof Press (Cable)
Cable Fly Crossovers => Cable Crossover
Low Cable Fly Crossovers => Low To High Cable Fly
Chest Supported Incline Row (Dumbbell) => Incline Row (Dumbbell)
Clap Push Ups => Clap Push Up
Clean => Clean (Barbell)
Clean and Jerk => Clean and Jerk (Barbell)
Clean and Press => Clean and Press (Barbell)
Clean Pull => Clean Pull (Barbell)
Kettlebell Clean => Clean (Kettlebell)
Concentration Curl => Concentration Curl (Dumbbell)
Cross Body Hammer Curl => Cross Body Hammer Curl (Dumbbell)
Pinwheel Curl (Dumbbell) => Cross Body Hammer Curl (Dumbbell)
Deadlift (Trap bar) => Trap Bar Deadlift
Hex Bar Deadlift => Trap Bar Deadlift
Deadlift High Pull => Deadlift High Pull (Barbell)
Decline Bench Press (Machine) => Decline Chest Press (Machine)
Drag Curl => Drag Curl (Barbell)
Dumbbell Snatch => Snatch (Dumbbell)
Kettlebell Snatch => Snatch (Kettlebell)
Dumbbell Squeeze Press => Squeeze Press (Dumbbell)
Hex Press (Dumbbell) => Squeeze Press (Dumbbell)
Dumbbell Step Up => Step-up (Dumbbell)
Elliptical Trainer => Elliptical Machine
EZ Bar Biceps Curl => Bicep Curl (EZ Bar)
Kettlebell Curl => Bicep Curl (Kettlebell)
Plate Curl => Bicep Curl (Plate)
Rope Cable Curl => Hammer Curl (Cable)
Face Pull => Face Pull (Cable)
Farmers Walk => Farmer's Walk
Farmers Carry => Farmer's Walk
Fire Hydrants => Fire Hydrant (Bodyweight)
Flutter Kicks => Flutter Kick
Frog Pumps (Dumbbell) => Frog Pump (Dumbbell)
Front Squat => Front Squat (Barbell)
Glute Bridge => Glute Bridge (Bodyweight)
Single Leg Glute Bridge => Single Leg Bridge
Glute Kickback on Floor => Donkey Kick (Bodyweight)
Standing Cable Glute Kickbacks => Glute Kickback (Cable)
Rear Kick (Machine) => Glute Kickback (Machine)
Goblet Squat => Goblet Squat (Dumbbell)
Kettlebell Goblet Squat => Goblet Squat (Kettlebell)
Kettlebell Halo => Halo (Kettlebell)
Kettlebell High Pull => High Pull (Kettlebell)
Kettlebell Shoulder Press => Overhead Press (Kettlebell)
Hack Squat (Machine) => Hack Squat
Hang Clean => Hang Clean (Barbell)
Hang Snatch => Hang Snatch (Barbell)
Heel Taps => Heel Touch
High Knees => High Knee Skips
Hip Thrust => Hip Thrust (Bodyweight)
Single Leg Hip Thrust => Single Leg Hip Thrust (Bodyweight)
Incline Push Ups => Incline Push Up
Inverted Row => Inverted Row (Bodyweight)
Iso-Lateral Low Row => Iso-Lateral Low Row (Machine)
Jump Shrug => Jump Shrug (Barbell)
Knee Raise Parallel Bars => Knee Raise (Captain's Chair)
Leg Raise Parallel Bars => Leg Raise (Captain's Chair)
Kneeling Push Up => Push Up (Knees)
L-Sit Hold => L Sit
Lateral Lunge => Side Lunge (Bodyweight)
Leg Press Horizontal (Machine) => Horizontal Leg Press (Machine)
Single Leg Press (Machine) => Leg Press (Single Leg)
Single Leg Extensions => Leg Extension (Single Leg)
Lunge => Lunge (Bodyweight)
Reverse Lunge => Reverse Lunge (Bodyweight)
Walking Lunge => Walking Lunge (Bodyweight)
Overhead Dumbbell Lunge => Overhead Lunge (Dumbbell)
Lying Knee Raise => Flat Knee Raise
Lying Leg Raise => Flat Leg Raise
Meadows Rows (Barbell) => Meadows Row
Nordic Hamstrings Curls => Nordic Hamstring Curl
Nordic Curl => Nordic Hamstring Curl
Overhead Curl (Cable) => High Cable Curl
Overhead Squat => Overhead Squat (Barbell)
Pike Pushup => Pike Push Up
Plank Pushup => Plank Push Up
Plate Front Raise => Front Raise (Plate)
Plate Squeeze (Svend Press) => Svend Press
Power Snatch => Power Snatch (Barbell)
Power Clean (Barbell) => Power Clean
Press Under => Press Under (Barbell)
Push Press (Barbell) => Push Press
Rack Pull => Rack Pull (Barbell)
Reverse Grip Concentration Curl => Reverse Grip Concentration Curl (Dumbbell)
Reverse Grip Lat Pulldown (Cable) => Lat Pulldown - Underhand (Cable)
Wide Grip Lat Pulldown (Cable) => Lat Pulldown - Wide Grip (Cable)
Single Arm Lat Pulldown => Lat Pulldown (Single Arm)
Vertical Traction (Machine) => Lat Pulldown (Machine)
Rope Straight Arm Pulldown => Straight Arm Lat Pulldown (Cable)
Rowing Machine => Rowing (Machine)
Russian Twist (Bodyweight) => Russian Twist
Scapular Pull Ups => Scapular Pull Up
Seated Cable Row - Bar Grip => Seated Row - Bar Grip (Cable)
Seated Cable Row - Bar Wide Grip => Seated Wide-Grip Row (Cable)
Seated Cable Row - V Grip (Cable) => Seated Row (Cable)
Seated Cable Row => Seated Row (Cable)
Single Arm Cable Row => Single Arm Row (Cable)
Seated Calf Raise => Seated Calf Raise (Machine)
Standing Calf Raise => Standing Calf Raise (Bodyweight)
Standing Calf Raise (Smith) => Standing Calf Raise (Smith Machine)
Seated Chest Flys (Cable) => Chest Fly (Cable)
Seated Dip Machine => Seated Dip (Machine)
Seated Incline Curl (Dumbbell) => Incline Curl (Dumbbell)
Seated Palms Up Wrist Curl => Seated Palms Up Wrist Curl (Dumbbell)
Seated Shoulder Press (Machine) => Shoulder Press (Machine)
Shoulder Press (Machine Plates) => Shoulder Press (Plate Loaded)
Seated Wrist Extension (Barbell) => Reverse Wrist Curl (Barbell)
Side Bend => Side Bend (Dumbbell)
Single Arm Landmine Press (Barbell) => Landmine Press
Single Arm Lateral Raise (Cable) => Lateral Raise (Cable)
Single Arm Tricep Extension (Dumbbell) => Single Arm Triceps Extension (Dumbbell)
Snatch => Snatch (Barbell)
Spinning => Cycling (Indoor)
Split Jerk => Split Jerk (Barbell)
Squat Row => Squat Row (Band)
Stair Machine (Floors) => Stair Climber
Stair Machine (Steps) => Stair Climber
Stair Machine => Stair Climber
Standing Leg Curls => Standing Leg Curl (Machine)
Standing Military Press (Barbell) => Strict Military Press (Barbell)
Sternum Pull up (Gironda) => Sternum Pull Up
Straight Leg Deadlift => Stiff Leg Deadlift (Barbell)
Sumo Deadlift => Sumo Deadlift (Barbell)
Torso Rotation => Torso Rotation (Machine)
Treadmill => Running (Treadmill)
Triceps Kickback (Cable) => Cable Kickback
Triceps Pressdown => Triceps Pushdown (Cable - Straight Bar)
Triceps Pushdown => Triceps Pushdown (Cable - Straight Bar)
Triceps Rope Pushdown => Triceps Pushdown (Cable - Rope)
Wide-Elbow Triceps Press (Dumbbell) => Tate Press (Dumbbell)
Zercher Squat => Zercher Squat (Barbell)
Pause Squat => Pause Squat (Barbell)
Pendlay Row => Pendlay Row (Barbell)
Good Morning => Good Morning (Barbell)
Arnold Press => Arnold Press (Dumbbell)
Cable Crunch (Kneeling) => Cable Crunch
Calf Press (Leg Press) => Calf Press on Leg Press
Ab Wheel Rollout => Ab Wheel
Ab Rollout => Ab Wheel
Jumping Jacks => Jumping Jack
Mountain Climbers => Mountain Climber
Burpees => Burpee
Box Jumps => Box Jump
Toes to Bar => Toes To Bar
T-Bar Row => T Bar Row
"""

private const val NAMES_0 = """
Bench Press (Barbell)
Bench Press (Dumbbell)
Bench Press (Smith Machine)
Bench Press (Cable)
Bench Press (Band)
Incline Bench Press (Barbell)
Incline Bench Press (Dumbbell)
Incline Bench Press (Smith Machine)
Incline Bench Press (Cable)
Decline Bench Press (Barbell)
Decline Bench Press (Dumbbell)
Decline Bench Press (Smith Machine)
Bench Press - Close Grip (Barbell)
Bench Press - Close Grip (Dumbbell)
Bench Press - Close Grip (Smith Machine)
Bench Press - Wide Grip (Barbell)
Bench Press - Reverse Grip (Barbell)
Bench Press - Pause (Barbell)
Pause Bench Press (Barbell)
Floor Press (Barbell)
Floor Press (Dumbbell)
Squeeze Press (Dumbbell)
Chest Press (Machine)
Chest Press (Cable)
Chest Press (Band)
Incline Chest Press (Machine)
Decline Chest Press (Machine)
Iso-Lateral Chest Press (Machine)
Chest Fly
Chest Fly (Dumbbell)
Chest Fly (Machine)
Chest Fly (Cable)
Chest Fly (Band)
Chest Fly (Suspension)
Incline Chest Fly (Dumbbell)
Incline Chest Fly (Cable)
Decline Chest Fly (Dumbbell)
Decline Chest Fly (Cable)
Low To High Cable Fly
High To Low Cable Fly
Cable Crossover
Pec Deck (Machine)
Push Up
Push Up (Weighted)
Push Up (Knees)
Push Up (Band)
Incline Push Up
Decline Push Up
Wide Push Up
Diamond Push Up
Archer Push Up
Chest Dip
Chest Dip (Assisted)
Chest Dip (Weighted)
Pullover (Dumbbell)
Pullover (Barbell)
Pullover (Machine)
Pullover (Cable)
Landmine Press
Overhead Press (Barbell)
Overhead Press (Dumbbell)
Overhead Press (Smith Machine)
Overhead Press (Cable)
Overhead Press (Kettlebell)
Overhead Press (Band)
Strict Military Press (Barbell)
Seated Overhead Press (Barbell)
Seated Overhead Press (Dumbbell)
Shoulder Press (Machine)
Shoulder Press (Plate Loaded)
Shoulder Press (Cable)
Shoulder Press (Dumbbell)
Arnold Press (Dumbbell)
Push Press
Push Press (Dumbbell)
Behind The Neck Press (Barbell)
Behind The Neck Press (Smith Machine)
Z Press (Barbell)
Z Press (Dumbbell)
Pike Push Up
Handstand Push Up
Lateral Raise (Dumbbell)
Lateral Raise (Cable)
Lateral Raise (Machine)
Lateral Raise (Band)
Seated Lateral Raise (Dumbbell)
Lying Lateral Raise (Dumbbell)
Front Raise (Dumbbell)
Front Raise (Cable)
Front Raise (Barbell)
Front Raise (Plate)
Front Raise (Band)
Front Raise (Suspension)
Y Raise (Dumbbell)
Y Raise (Cable)
Lu Raise (Dumbbell)
Lu Raise (Plate)
Reverse Fly (Dumbbell)
Reverse Fly (Cable)
Reverse Fly (Machine)
Reverse Fly (Band)
Rear Delt Row (Barbell)
Rear Delt Row (Dumbbell)
Rear Delt Row (Cable)
Face Pull (Cable)
Face Pull (Band)
Upright Row (Barbell)
Upright Row (Dumbbell)
Upright Row (Cable)
Upright Row (Kettlebell)
Upright Row (EZ Bar)
Shrug (Barbell)
Shrug (Dumbbell)
Shrug (Smith Machine)
Shrug (Machine)
Shrug (Cable)
Shrug (Trap Bar)
Shoulder External Rotation (Cable)
Shoulder External Rotation (Dumbbell)
Shoulder External Rotation (Band)
Seated Shoulder External Rotation (Dumbbell)
Shoulder Internal Rotation (Cable)
Shoulder Internal Rotation (Band)
Cuban Press (Dumbbell)
Around the World
Around the World (Kettlebell)
Pull Up
Pull Up (Assisted)
Pull Up (Weighted)
Pull Up (Band)
Pull Up (Neutral Grip)
Pull Up (Wide Grip)
Pull Up (Close Grip)
Wide Pull Up
Kipping Pull Up
Chin Up
Chin Up (Assisted)
Chin Up (Weighted)
Scapular Pull Up
Muscle Up
Inverted Row (Bodyweight)
Inverted Row - Underhand (Bodyweight)
Lat Pulldown (Machine)
Lat Pulldown (Cable)
Lat Pulldown (Band)
Lat Pulldown (Single Arm)
Lat Pulldown - Wide Grip (Cable)
Lat Pulldown - Close Grip (Cable)
Lat Pulldown - Neutral Grip (Cable)
Lat Pulldown - Underhand (Cable)
Lat Pulldown - Underhand (Band)
Lat Pulldown - Behind The Neck (Cable)
Kneeling Pulldown (Band)
Straight Arm Lat Pulldown (Cable)
Straight Arm Lat Pulldown (Band)
Iso-Lateral High Row (Machine)
Iso-Lateral Row (Machine)
Iso-Lateral Low Row (Machine)
Seated Row (Cable)
Seated Row (Machine)
Seated Row (Band)
Seated Row - Neutral Grip (Cable)
Seated Row - Underhand (Cable)
Seated Wide-Grip Row (Cable)
Single Arm Row (Cable)
Single Arm Row (Machine)
Lat Row (Single Arm) (Cable)
Bent Over Row (Barbell)
Bent Over Row (Dumbbell)
Bent Over Row (Smith Machine)
Bent Over Row (Band)
Bent Over One Arm Row (Dumbbell)
Bent Over Row - Underhand (Barbell)
Incline Row (Dumbbell)
Pendlay Row (Barbell)
T Bar Row
T Bar Row (Machine)
Landmine Row
Meadows Row
Chest Supported Row (Dumbbell)
Chest Supported Row (Barbell)
Chest Supported Row (Machine)
Seal Row (Barbell)
Seal Row (Dumbbell)
Renegade Row (Dumbbell)
Deadlift (Barbell)
Deadlift (Dumbbell)
Deadlift (Smith Machine)
Deadlift (Kettlebell)
Deadlift (Band)
Trap Bar Deadlift
Straight Leg Deadlift (Band)
Deadlift High Pull (Barbell)
Sumo Deadlift High Pull (Barbell)
Jump Shrug (Barbell)
Sumo Deadlift (Barbell)
Deficit Deadlift (Barbell)
Romanian Deadlift (Barbell)
Romanian Deadlift (Dumbbell)
Romanian Deadlift (Smith Machine)
Romanian Deadlift (Kettlebell)
Romanian Deadlift (Cable)
Stiff Leg Deadlift (Barbell)
Stiff Leg Deadlift (Dumbbell)
Single Leg Romanian Deadlift (Dumbbell)
Single Leg Romanian Deadlift (Kettlebell)
Single Leg Romanian Deadlift (Barbell)
Rack Pull (Barbell)
Good Morning (Barbell)
Good Morning (Smith Machine)
Good Morning (Band)
Back Extension
Back Extension (Weighted)
Back Extension (Machine)
Back Extension - Glute Focus
Back Extension - Glute Focus (Weighted)
Reverse Hyperextension
Superman
Bicep Curl (Barbell)
Bicep Curl (Dumbbell)
Bicep Curl (Cable)
Bicep Curl (Machine)
Bicep Curl (EZ Bar)
Bicep Curl (Band)
Bicep Curl (Kettlebell)
Bicep Curl (Plate)
Bicep Curl (Suspension)
Bicep Curl - Wide Grip (Barbell)
Bicep Curl - Close Grip (Barbell)
Hammer Curl (Dumbbell)
Hammer Curl (Cable)
Hammer Curl (Machine)
Hammer Curl (Band)
Cross Body Hammer Curl (Dumbbell)
Preacher Curl (Barbell)
Preacher Curl (Dumbbell)
Preacher Curl (Machine)
Preacher Curl (EZ Bar)
Preacher Curl (Cable)
Incline Curl (Dumbbell)
Concentration Curl (Dumbbell)
Concentration Curl (Cable)
Spider Curl (Barbell)
Spider Curl (Dumbbell)
Spider Curl (EZ Bar)
Drag Curl (Barbell)
Drag Curl (Dumbbell)
Bayesian Curl (Cable)
High Cable Curl
Waiter Curl (Dumbbell)
Zottman Curl (Dumbbell)
Reverse Curl (Barbell)
Reverse Curl (Dumbbell)
Reverse Curl (EZ Bar)
Reverse Curl (Cable)
Reverse Curl (Band)
Reverse Grip Concentration Curl (Dumbbell)
Seated Palms Up Wrist Curl (Dumbbell)
Wrist Curl (Barbell)
Wrist Curl (Dumbbell)
Wrist Curl (Cable)
Reverse Wrist Curl (Barbell)
Reverse Wrist Curl (Dumbbell)
Reverse Wrist Curl (Cable)
Wrist Roller
Triceps Pushdown (Cable - Straight Bar)
Triceps Pushdown (Cable - Rope)
Triceps Pushdown (Cable - V Bar)
Triceps Pushdown (Band)
Single Arm Triceps Pushdown (Cable)
Triceps Pushdown - Reverse Grip (Cable)
Triceps Extension
Triceps Extension (Dumbbell)
Triceps Extension (Cable)
Triceps Extension (Barbell)
Triceps Extension (Machine)
Triceps Extension (EZ Bar)
Triceps Extension (Band)
Triceps Extension (Suspension)
Overhead Triceps Extension (Dumbbell)
Overhead Triceps Extension (Cable)
Overhead Triceps Extension (EZ Bar)
Overhead Triceps Extension (Band)
Skullcrusher (Barbell)
Skullcrusher (Dumbbell)
Skullcrusher (EZ Bar)
Triceps Kickback (Dumbbell)
Triceps Kickback (Band)
Cable Kickback
Triceps Dip
Triceps Dip (Assisted)
Triceps Dip (Weighted)
Bench Dip
Seated Dip (Machine)
JM Press (Barbell)
JM Press (Smith Machine)
Tate Press (Dumbbell)
Squat (Barbell)
Squat (Dumbbell)
Squat (Smith Machine)
Squat (Machine)
Squat (Bodyweight)
Squat (Band)
Squat (Kettlebell)
Squat (Suspension)
Squat - Wide Stance (Barbell)
Squat - Narrow Stance (Barbell)
Squat - Pause (Barbell)
Front Squat (Barbell)
Front Squat (Kettlebell)
Front Squat (Smith Machine)
Goblet Squat (Dumbbell)
Goblet Squat (Kettlebell)
Box Squat (Barbell)
Pause Squat (Barbell)
Zercher Squat (Barbell)
Overhead Squat (Barbell)
Hack Squat
Hack Squat (Barbell)
Pendulum Squat (Machine)
Belt Squat (Machine)
V Squat (Machine)
Sissy Squat
Sissy Squat (Weighted)
Pistol Squat
Pistol Squat (Assisted)
Jump Squat
Box Jump
Lateral Box Jump
High Knee Skips
Jumping Jack
Bulgarian Split Squat
Bulgarian Split Squat (Dumbbell)
Bulgarian Split Squat (Barbell)
Bulgarian Split Squat (Smith Machine)
Split Squat (Dumbbell)
Split Squat (Barbell)
Split Squat (Bodyweight)
Lunge (Barbell)
Lunge (Dumbbell)
Lunge (Bodyweight)
Lunge (Smith Machine)
Lunge (Kettlebell)
Reverse Lunge (Barbell)
Reverse Lunge (Dumbbell)
Reverse Lunge (Bodyweight)
Reverse Lunge (Smith Machine)
Walking Lunge (Barbell)
Walking Lunge (Dumbbell)
Walking Lunge (Bodyweight)
Side Lunge (Dumbbell)
Side Lunge (Bodyweight)
Curtsy Lunge (Dumbbell)
Curtsy Lunge (Bodyweight)
Step-up
Step-up (Dumbbell)
Step-up (Barbell)
Leg Press
Leg Press (Machine)
Leg Press (Single Leg)
Leg Press - Feet High (Machine)
Leg Press - Feet Low (Machine)
Leg Press - Wide Stance (Machine)
Leg Press - Narrow Stance (Machine)
Seated Leg Press (Machine)
Horizontal Leg Press (Machine)
Leg Extension (Machine)
Leg Extension (Single Leg)
Lying Leg Curl (Machine)
Seated Leg Curl (Machine)
Standing Leg Curl (Machine)
Standing Leg Curl (Cable)
Iso Standing Leg Curl (Plate Loaded)
Nordic Hamstring Curl
Glute Ham Raise
Hip Thrust (Barbell)
Hip Thrust (Dumbbell)
Hip Thrust (Machine)
Hip Thrust (Smith Machine)
Hip Thrust (Bodyweight)
Hip Thrust (Band)
Single Leg Hip Thrust (Bodyweight)
Single Leg Hip Thrust (Dumbbell)
Glute Bridge (Barbell)
Glute Bridge (Dumbbell)
Glute Bridge (Bodyweight)
Glute Bridge (Band)
Single Leg Bridge
Glute Kickback (Cable)
Glute Kickback (Machine)
Glute Kickback (Band)
Cable Pull Through
Kettlebell Swing
Hip Abductor (Machine)
Hip Abductor (Cable)
Hip Abductor (Band)
Hip Adductor (Machine)
Hip Adductor (Cable)
Hip Adductor (Band)
Donkey Kick (Bodyweight)
Donkey Kick (Band)
Fire Hydrant (Bodyweight)
Fire Hydrant (Band)
Clamshell (Band)
Clamshell
Frog Pump
Frog Pump (Dumbbell)
Standing Calf Raise (Machine)
Standing Calf Raise (Smith Machine)
Standing Calf Raise (Dumbbell)
Standing Calf Raise (Barbell)
Standing Calf Raise (Bodyweight)
Seated Calf Raise (Machine)
Seated Calf Raise (Plate Loaded)
Seated Calf Raise (Dumbbell)
Seated Calf Raise (Barbell)
Single Leg Standing Calf Raise (Bodyweight)
Single Leg Standing Calf Raise (Barbell)
Single Leg Standing Calf Raise (Dumbbell)
Single Leg Standing Calf Raise (Machine)
Donkey Calf Raise (Machine)
Calf Press on Leg Press
Calf Press on Seated Leg Press
Tibialis Raise (Machine)
Tibialis Raise (Band)
Crunch
Crunch (Weighted)
Crunch (Stability Ball)
Cable Crunch
Cross Body Crunch
Crunch (Machine)
Decline Crunch
Decline Crunch (Weighted)
Reverse Crunch
Bicycle Crunch
Oblique Crunch (Bodyweight)
Oblique Crunch (Machine)
Oblique Crunch
Sit Up
Sit Up (Weighted)
Sit Up (Decline)
Jackknife Sit Up
V Up
Hanging Knee Raise
Hanging Leg Raise
Knee Raise (Captain's Chair)
Leg Raise (Captain's Chair)
Flat Leg Raise
Flat Knee Raise
Knees to Elbows
Toes To Bar
Flutter Kick
Mountain Climber
Heel Touch
Dead Bug
Bird Dog
Dragon Flag
Ab Wheel
Ab Coaster (Machine)
Russian Twist
Russian Twist (Weighted)
Side Bend (Dumbbell)
Side Bend (Cable)
Side Bend (Band)
Cable Twist
Cable Twist (Down to up)
Cable Twist (Up to down)
Wood Chop (Cable)
Wood Chop (Band)
Wood Chop (Dumbbell)
Pallof Press
Pallof Press (Cable)
Pallof Press (Band)
Torso Rotation (Machine)
Clean (Barbell)
Clean (Kettlebell)
Power Clean
Hang Clean (Barbell)
Clean and Jerk (Barbell)
Press Under (Barbell)
Clean Pull (Barbell)
Snatch (Barbell)
Snatch (Dumbbell)
Snatch (Kettlebell)
Power Snatch (Barbell)
Hang Snatch (Barbell)
Snatch Pull (Barbell)
Push Jerk (Barbell)
Split Jerk (Barbell)
Thruster (Barbell)
Thruster (Dumbbell)
Thruster (Kettlebell)
Wall Ball
Devil Press (Dumbbell)
Man Maker (Dumbbell)
Kettlebell Turkish Get Up
Turkish Get Up (Dumbbell)
Squat Row (Band)
Ball Slams
Burpee
21s Bicep Curl
Ab Scissors
Band Pull Apart
Behind the Back Wrist Curl (Barbell)
Bicycle Crunch Raised Legs
Burpee Over the Bar
Calf Extension (Machine)
Calf Press (Machine)
Chest Supported Reverse Fly (Dumbbell)
Chest Supported Y Raise (Dumbbell)
Clap Push Up
Clean and Press (Barbell)
Clean and Press (Dumbbell)
Clean and Press (Kettlebell)
Dragonfly
Elbow to Knee
Feet Up Bench Press (Barbell)
Floor Triceps Dip
Frog Jumps
Front Lever Raise
Gorilla Row (Kettlebell)
Halo (Kettlebell)
High Pull (Kettlebell)
Hollow Rock
Jack Knife (Suspension)
Jumping Lunge
Landmine 180
Landmine Squat and Press
Lateral Band Walks
Lateral Leg Raises
Lateral Squat
Low Row (Suspension)
Lying Neck Curls
Lying Neck Curls (Weighted)
Lying Neck Extension
Lying Neck Extension (Weighted)
Negative Pull Up
One Arm Push Up
Overhead Lunge (Dumbbell)
Overhead Plate Raise
Partial Glute Bridge (Barbell)
Plank Push Up
Plate Press
Svend Press
Push Up - Close Grip
Push Up - Wide Grip
Reverse Fly Single Arm (Cable)
Ring Dip
Ring Pull Up
Ring Push Up
Seated Row - Bar Grip (Cable)
Seated Triceps Press
Shoulder Taps
Single Arm Cable Crossover
Single Arm Curl (Cable)
Single Arm Triceps Extension (Dumbbell)
Spiderman
Sternum Pull Up
Sumo Squat
Sumo Squat (Barbell)
Sumo Squat (Dumbbell)
Sumo Squat (Kettlebell)
Toe Touch
Plank
Plank (Weighted)
Side Plank
Reverse Plank
Hollow Hold
L Sit
Wall Sit
Dead Hang
Farmer's Walk
Front Lever Hold
Handstand Hold
Downward Dog
Running
Running (Treadmill)
Walking
Walking (Treadmill)
Hiking
Cycling
Cycling (Indoor)
Elliptical Machine
Rowing (Machine)
Stair Climber
Air Bike
Boxing
HIIT
Pilates
Sprints
Warm Up
Sled Push
Jump Rope
Battle Ropes
Swimming
Aerobics
Climbing
Skating
Skiing
Snowboarding
Stretching
Yoga
3/4 Sit Up
45 Degrees One Leg Press (Machine)
45° Leg Press (side Pov) (Machine)
45° Side Bend
45в° Calf Press (Machine)
45в° Leg Press (back Pov) (Machine)
45в° Leg Press (Machine)
45в° Leg Wide Press (Machine)
Abdominal Fallout (Suspension)
Advanced Windmill (Kettlebell)
Alternate Biceps Curl (with Arm Blaster) (Dumbbell)
Alternate Biceps Curl (Barbell)
Alternate Biceps Curl (Dumbbell)
Alternate Hammer Preacher Curl (Dumbbell)
Alternate Heel Touchers
Alternate Lateral Pulldown (Cable)
Alternate Leg Press (Machine)
Alternate Preacher Curl (Dumbbell)
Alternate Seated Hammer Curl (Dumbbell)
Alternate Shoulder Press (Cable)
Alternate Side Press (Dumbbell)
Alternate Triceps Extension (Cable)
Alternating Arm Ups (Stability Ball)
Alternating Bicep Curl with Leg Raised on Exercise Ball (Dumbbell)
Alternating Biceps Curl (Band)
Alternating Hang Clean (Kettlebell)
Alternating Narrow Grip Seated Row (Machine)
Alternating Press on Floor (Kettlebell)
Alternating Press (Kettlebell)
Alternating Renegade Row (Kettlebell)
Alternating Row (Kettlebell)
Alternating Seated Bicep Curl on Exercise Ball (Dumbbell)
Alternating V Up (Band)
Ankle Circles
Anti Gravity Press (EZ Bar)
Archer Pull Up
Arm Slingers Hanging Bent Knee Legs
Arm Slingers Hanging Straight Legs
Arnold Press (Kettlebell)
Around Pullover (Dumbbell)
Assisted Chin Up (Machine)
Assisted Inverse Leg Curl (Cable)
Assisted Pull Up (Band)
Assisted Wheel Rollerout (Band)
Back Extension on Exercise Ball (Stability Ball)
Back Extension with Arms Extended (Stability Ball)
Back Extension with Hands Behind Head (Stability Ball)
Back Extension with Knees Off Ground (Stability Ball)
Back Extension with Rotation (Stability Ball)
Back Lever
Back Shrug (Smith Machine)
Back and Forth Step
Backward Jump
Balance Board
Bar Lateral Pulldown (Cable)
Battling Ropes (Rope)
Bear Crawl
Bench Dip (knees Bent)
Bench Dip on Floor
Bench Dip (Weighted)
Bench Front Squat (Barbell)
Bench Hip Extension
Bench Pull Ups
Bench Seated Press (Dumbbell)
Bench Squat (Barbell)
Bench Squat (Dumbbell)
Bent Arm Pullover (Barbell)
Bent Knee Good Morning (Smith Machine)
Bent Over Hip Extension (Band)
Bent Over Row with V Bar (Machine)
Bent Over Row (Machine)
Bent Press (Kettlebell)
Bicep Curl Lunge with Bowling Motion (Dumbbell)
Bicep Curl on Exercise Ball with Leg Raised (Dumbbell)
Bicep Curl with Stork Stance (Dumbbell)
Bicep Curl (Smith Machine)
Biceps Curl (with Arm Blaster) (Barbell)
Biceps Curl (with Arm Blaster) (Dumbbell)
Biceps Curl (with Arm Blaster) (EZ Bar)
Biceps Curl Reverse (Dumbbell)
Biceps Curl Squat (Dumbbell)
Biceps Curl V Sit on Bosu Ball (Dumbbell)
Biceps Leg Concentration Curl
Biceps Narrow Pull Ups
Biceps Pull Up
Bicycle Crunch (Band)
Bottoms Up Clean From the Hang Position (Kettlebell)
Bottoms Up
Box Jump Down with One Leg Stabilization
Bridge Mountain Climber (cross Body)
Butt Ups
Butterfly Yoga Pose
Calf Press on Leg Press (Machine)
Cambered Bar Lying Row (Barbell)
Captains Chair Straight Leg Raise
Catch and Overhead Throw (Medicine Ball)
Chair Squat (Smith Machine)
Chest Dip (kneeling) (Assisted)
Chest Dip (on Dip Pull Up Cage)
Chest Dip on Straight Bar
Chest Pass (Medicine Ball)
Chest Push From 3 Point Stance (Medicine Ball)
Chest Push Multiple Response (Medicine Ball)
Chest Push Single Response (Medicine Ball)
Chest Push with Run Release (Medicine Ball)
Chin Ups (narrow Parallel Grip)
Clean Grip Front Squat (Barbell)
Clean (Dumbbell)
Clock Push Up
Close Grip Bench Press (EZ Bar)
Close Grip Chin Up on Dip Cage (Weighted)
Close Grip Chin Up
Close Grip Curl (Cable)
Close Grip Curl (EZ Bar)
Close Grip Preacher Curl (EZ Bar)
Close Grip Press (Dumbbell)
Close Grip Pulldown (Band)
Close Grip Push Up (on Knees)
Close Grip Push Up (Band)
Close Grip Push Up (Medicine Ball)
Closer Hack Squat (Machine)
Cocoons
Concentration Curl (Band)
Concentration Extension (on Knee) (Cable)
Contralateral Forward Lunge (Dumbbell)
Crab Twist Toe Touch
Cross Over Lateral Pulldown (Cable)
Cross Over Revers Fly (Cable)
Cross Over Variation (Cable)
Crunch (full Range Hands Behind Head) (Stability Ball)
Crunch (hands Overhead)
Crunch (on Stability Ball) (Stability Ball)
Crunch (on Stability Ball, Arms Straight) (Stability Ball)
Crunch Floor
Curl Up
Curl (Barbell)
Curl (Cable)
Curl (EZ Bar)
Curtsey Squat
Cycle Cross Trainer
Deadlift (Cable)
Deadlift (Machine)
Decline Bent Arm Pullover (Barbell)
Decline Close Grip Bench Press (Smith Machine)
Decline Close Grip Face Press (EZ Bar)
Decline Close Grip to Skull Press (Barbell)
Decline Fly (Cable)
Decline Fly (Dumbbell)
Decline Hammer Press (Dumbbell)
Decline One Arm Fly (Dumbbell)
Decline One Arm Hammer Press (Dumbbell)
Decline One Arm Press (Cable)
Decline Press (Cable)
Decline Pullover (Barbell)
Decline Reverse Grip Press (Smith Machine)
Decline Seated Wide Grip Row (Cable)
Decline Shrug (Dumbbell)
Decline Sit Up (Weighted)
Decline Triceps Extension (Dumbbell)
Decline Triceps Extension (EZ Bar)
Decline Twist Fly (Dumbbell)
Decline Wide Grip Press (Barbell)
Decline Wide Grip Pullover (Barbell)
Deep Push Up (Dumbbell)
Dip (Stability Ball)
Dips (Rings)
Donkey Calf Raise
Donkey Calf Raise (Weighted)
Double Alternating Hang Clean (Kettlebell)
Double Jerk (Kettlebell)
Double Push Press (Kettlebell)
Double Snatch (Kettlebell)
Double Windmill (Kettlebell)
Drag Curl (Cable)
Drop Jump Squat
Drop Push Up
Drop Push Up (Weighted)
Dumbbells Seated Triceps Extension (Dumbbell)
Elbow Dips
Elbow Lift Reverse Push Up
Elevator
Extended Range One Arm Press on Floor (Kettlebell)
Farmers Walk (Dumbbell)
Figure 8 (Kettlebell)
Finger Curls (Barbell)
Finger Curls (Dumbbell)
Fixed Back Close Grip Pulldown (Band)
Fixed Back Underhand Pulldown (Band)
Flag
Flexion Leg Sit Up (bent Knee)
Flexion Leg Sit Up (straight Arm)
Floor Calf Raise (Barbell)
Floor Fly (with Barbell) (Barbell)
Floor Seated Wide Grip Row (Cable)
Fly on Exercise Ball (Dumbbell)
Fly (Dumbbell)
Forward Angled Calf Raise (Machine)
Forward Jump
Forward Lunge Triceps Extension (Dumbbell)
Forward Raise (Cable)
"""

private const val NAMES_1 = """
Frankenstein Squat (Barbell)
French Press on Exercise Ball (EZ Bar)
Frog Crunch
Frog Planche
Front Chest Squat (Barbell)
Front Lateral Raise (Band)
Front Lever Reps
Front Lever
Front Plank with Twist
Front Plank (Weighted)
Front Pulldown (Machine)
Front Raise and Pullover (Barbell)
Front Raise (Weighted)
Front Shoulder Raise (Cable)
Front Squat (clean Grip) (Smith Machine)
Full Can Lateral Raise (Dumbbell)
Full Maltese
Full Planche Push Up
Full Planche
Full Squat (back Pov) (Barbell)
Full Squat (side Pov) (Barbell)
Full Squat (Barbell)
Full Squat (Smith Machine)
Full Zercher Squat (Barbell)
Gironda Sternum Chin
Glute Bridge March
Gorilla Chin
Gripless Shrug (Machine)
Gripper Hands (Machine)
Groin Crunch
Guillotine Bench Press (Barbell)
Hack Calf Raise (Machine)
Hack One Leg Calf Raise (Machine)
Hack Squat (Smith Machine)
Hammer Curl (with Rope) (Cable)
Hammer Curl on Exercise Ball (Dumbbell)
Hammer Curl (Barbell)
Hammer Curls (with Arm Blaster) (Dumbbell)
Hammer Grip Preacher Curl (Machine)
Hands Bike
Handstand
Hang Clean (Kettlebell)
Hanging Knee Raise with Throw Down (Assisted)
Hanging Knee Raise (Assisted)
Hanging Leg Hip Raise
Hanging Leg Hip Raise (Weighted)
Hanging Oblique Knee Raise
Hanging Pike
Hanging Straight Leg Hip Raise
Hanging Straight Leg Raise
Hanging Straight Twisting Leg Hip Raise
High Bar Squat (Barbell)
High Curl (Dumbbell)
High Knee Against Wall
High Pulley Overhead Tricep Extension (Cable)
High Row (kneeling) (Cable)
High Row (Machine)
Hip Adduction (Cable)
Hip Lift (Band)
Hip Raise (bent Knee)
Hip Raise (Smith Machine)
Horizontal One Leg Press (Machine)
Horizontal Pallof Press (Band)
Hug Keens to Chest
Hug (Stability Ball)
Hyght Dumbbell Fly (Dumbbell)
Hyperextension (on Bench)
Hyperextension (on Stability Ball) (Weighted)
Impossible Dips
Inchworm
Incline Alternate Press (Dumbbell)
Incline Bench Row (Cable)
Incline Biceps Curl (Dumbbell)
Incline Breeding (Dumbbell)
Incline Close Grip Bench Press (Barbell)
Incline Close Grip Push Up
Incline Fly (on Stability Ball) (Cable)
Incline Fly on Exercise Ball (Dumbbell)
Incline Fly (Cable)
Incline Fly (Dumbbell)
Incline Hammer Curl (Dumbbell)
Incline Hammer Press on Exercise Ball (Dumbbell)
Incline Hammer Press (Dumbbell)
Incline Inner Biceps Curl (Dumbbell)
Incline Leg Hip Raise (leg Straight)
Incline One Arm Fly on Exercise Ball (Dumbbell)
Incline One Arm Fly (Dumbbell)
Incline One Arm Hammer Press on Exercise Ball (Dumbbell)
Incline One Arm Hammer Press (Dumbbell)
Incline One Arm Lateral Raise (Dumbbell)
Incline One Arm Press on Exercise Ball (Dumbbell)
Incline One Arm Press (Dumbbell)
Incline Palm in Press (Dumbbell)
Incline Press on Exercise Ball (Dumbbell)
Incline Push Up (on Box)
Incline Push Up Depth Jump
Incline Pushdown (Cable)
Incline Raise (Dumbbell)
Incline Rear Lateral Raise (Dumbbell)
Incline Reverse Grip Press (Barbell)
Incline Reverse Grip Press (Smith Machine)
Incline Reverse Grip Push Up
Incline Row (Barbell)
Incline Scapula Push Up
Incline Shoulder Raise (Barbell)
Incline Shoulder Raise (Dumbbell)
Incline Shoulder Raises (Smith Machine)
Incline Shrug (Dumbbell)
Incline Side Plank
Incline T Raise (Dumbbell)
Incline Tricep Extension (Smith Machine)
Incline Triceps Extension (Cable)
Incline Triceps Extension (Dumbbell)
Incline Triceps Extension (EZ Bar)
Incline Twisted Flyes (Dumbbell)
Incline Twisting Sit Up
Incline Two Arm Extension (Dumbbell)
Incline Y Raise (Dumbbell)
Inverse Leg Curl (bench Support)
Inverse Leg Curl (on Pull Up Cable Machine)
Inverted Row Bent Knees
Inverted Row on Bench
Inverted Row with Straps
Iron Cross (Dumbbell)
Isometric Chest Squeeze
Isometric Wipers
Jack Burpee
Jack Knife Sit Up (Band)
Janda Sit Up
Jefferson Squat (Barbell)
Jm Bench Press (Barbell)
Jm Bench Press (EZ Bar)
Judo Flip (Cable)
Jump Squat (Barbell)
Kick Out Sit
Kickback (Dumbbell)
Kickbacks on Exercise Ball (Dumbbell)
Kipping Muscle Up
Knee Touch Crunch
Kneeling Bicep Curl Exercise Ball (Dumbbell)
Kneeling Jump Squat (Barbell)
Kneeling Leg Curl (Machine)
Kneeling One Arm Pulldown (Band)
Kneeling Step with Swing (Weighted)
Kneeling Triceps Extension
Kneeling Triceps Extension (Cable)
Kneeling Twist (Machine)
Kneeling Twisting Crunch (Band)
Korean Dips
L Pull Up
L Sit on Floor
Landmine 180 (Barbell)
Landmine Lateral Raise (Barbell)
Lat Pulldown Full Range of Motion (Cable)
Lateral Lunge (Barbell)
Lateral Pulldown (with Rope Attachment) (Cable)
Lateral to Front Raise (Dumbbell)
Lean Planche
Left Hook. Boxing
Leg Extension (Band)
Leg Press (Smith Machine)
Leg Pull in Flat Bench
London Bridge (Rope)
Low Bar Squat (Barbell)
Low Bar Squat (Smith Machine)
Low Fly (Cable)
Low Glute Bridge on Floor
Low Seated Row (Cable)
Lower Back Curl
Lunge Pass Through (Kettlebell)
Lunge with Bicep Curl (Dumbbell)
Lunge with Jump
Lunge with Swing (Weighted)
Lunge with Twist
Lying Alternate Extension (Dumbbell)
Lying Back of the Head Tricep Extension (Barbell)
Lying Bent Arms Pullover (EZ Bar)
Lying Bicep Curl (Cable)
Lying Calf Press (Machine)
Lying Close Grip Curl (Cable)
Lying Close Grip Press (Barbell)
Lying Close Grip Triceps Extension Behind Head (EZ Bar)
Lying Close Grip Triceps Extension (Barbell)
Lying Elbow Press (Dumbbell)
Lying Elbow to Knee
Lying Extension (across Face) (Dumbbell)
Lying Extension Pullover (with Rope Attachment) (Cable)
Lying Extension (Barbell)
Lying External Shoulder Rotation (Dumbbell)
Lying Femoral (Dumbbell)
Lying Fly (Cable)
Lying Hammer Press (Dumbbell)
Lying Hip Internal Rotation (Band)
Lying Leg Hip Raise
Lying Leg Raise Flat Bench
Lying Leg Raise with Lateral Throw Down (Assisted)
Lying Leg Raise with Throw Down (Assisted)
Lying Lifting (on Hip) (Barbell)
Lying One Arm Deltoid Rear (Dumbbell)
Lying One Arm Press (Dumbbell)
Lying One Arm Pronated Triceps Extension (Dumbbell)
Lying One Arm Rear Lateral Raise (Dumbbell)
Lying One Arm Supinated Triceps Extension (Dumbbell)
Lying Preacher Curl (Barbell)
Lying Pronation on Floor (Dumbbell)
Lying Pronation (Dumbbell)
Lying Pullover on Exercise Ball (Dumbbell)
Lying Rear Delt Row (Dumbbell)
Lying Rear Lateral Raise (Dumbbell)
Lying Single Extension (Dumbbell)
Lying Squat (Machine)
Lying Straight Leg Raise (Band)
Lying Supination on Floor (Dumbbell)
Lying Supination (Dumbbell)
Lying Supine Biceps Curl (Dumbbell)
Lying Supine Curl (Dumbbell)
Lying Triceps Extension Skull Crusher (Barbell)
Lying Triceps Extension (Barbell)
Lying Triceps Extension (Dumbbell)
Lying Two One Leg Curl (Machine)
Lying Wide Curl (Dumbbell)
Lying on Floor Rear Delt Raise (Dumbbell)
Machine Inner Chest Press (Machine)
March Sit (wall)
Middle Fly (Cable)
Military Press (Machine)
Mixed Grip Chin Up
Modified Push Up to Lower Arms
Monster Walk
Motion Russian Twist (Assisted)
Muscle Up (on Bar) (Weighted)
Muscle Up (on Vertical Bar)
Muscle Up (Weighted)
Narrow Grip Seated Row (Machine)
Narrow Push Up on Exercise Ball (Stability Ball)
Narrow Row (Smith Machine)
Narrow Stance Squat (Barbell)
Negative Crunch
Neutral Grip Bench Press (Dumbbell)
Oblique Crunches Floor
On the Wall Calf Raise (tennis Ball Between Ankles) (Stability Ball)
On the Wall Calf Raise (tennis Ball Between Knees) (Stability Ball)
On the Wall Calf Raise (Stability Ball)
One Arm Against Wall
One Arm Bench Fly (Dumbbell)
One Arm Bent Over Row (Barbell)
One Arm Bent Over Row (Cable)
One Arm Bent Over Row (Machine)
One Arm Chest Fly on Exercise Ball (Dumbbell)
One Arm Chin Up
One Arm Clean and Jerk (Kettlebell)
One Arm Concentration Curl (on Stability Ball) (Dumbbell)
One Arm Curl (Cable)
One Arm Decline Chest Fly (Cable)
One Arm Decline Chest Press (Dumbbell)
One Arm Dip
One Arm Floor Press (Barbell)
One Arm Floor Press (Kettlebell)
One Arm Fly on Exercise Ball (Cable)
One Arm Fly on Exercise Ball (Dumbbell)
One Arm French Press on Exercise Ball (Dumbbell)
One Arm Hammer Preacher Curl (Dumbbell)
One Arm Hammer Press on Exercise Ball (Dumbbell)
One Arm Incline Chest Press (Dumbbell)
One Arm Incline Fly on Exercise Ball (Cable)
One Arm Incline Press on Exercise Ball (Cable)
One Arm Incline Press (Cable)
One Arm Jerk (Kettlebell)
One Arm Kickback (Dumbbell)
One Arm Lateral Bent Over (Cable)
One Arm Lateral High Row (Machine)
One Arm Lateral Raise with Support (Dumbbell)
One Arm Lateral Raise (Cable)
One Arm Lateral Raise (Dumbbell)
One Arm Lateral Wide Pulldown (Machine)
One Arm Military Press to the Side (Kettlebell)
One Arm Overhead Biceps Curl (Band)
One Arm Preacher Curl (Cable)
One Arm Press on Exercise Ball (Cable)
One Arm Press on Exercise Ball (Dumbbell)
One Arm Prone Curl (Dumbbell)
One Arm Prone Hammer Curl (Dumbbell)
One Arm Pulldown (Cable)
One Arm Pullover on Exercise Ball (Dumbbell)
One Arm Push Press (Kettlebell)
One Arm Reverse Fly (with Support) (Dumbbell)
One Arm Reverse Grip Press (Dumbbell)
One Arm Reverse Preacher Curl (Cable)
One Arm Reverse Preacher Curl (Dumbbell)
One Arm Reverse Spider Curl (Dumbbell)
One Arm Reverse Wrist Curl (Dumbbell)
One Arm Row (Kettlebell)
One Arm Row (Smith Machine)
One Arm Seated Bicep Curl on Exercise Ball (Dumbbell)
One Arm Seated Hammer Curl (Dumbbell)
One Arm Seated Neutral Wrist Curl (Dumbbell)
One Arm Shoulder Press (Dumbbell)
One Arm Shoulder Press (Machine)
One Arm Side Deadlift (Barbell)
One Arm Single Leg Split Squat (Band)
One Arm Slam (with Medicine Ball) (Medicine Ball)
One Arm Snatch (Barbell)
One Arm Snatch (Dumbbell)
One Arm Snatch (Kettlebell)
One Arm Standing Curl (Dumbbell)
One Arm Standing Hammer Curl (Dumbbell)
One Arm Standing Low Row (Band)
One Arm Straight Back High Row (kneeling) (Cable)
One Arm Towel Row
One Arm Tricep Pushdown (Cable)
One Arm Triceps Extension (on Bench) (Dumbbell)
One Arm Twisting Chest Press (Band)
One Arm Twisting Seated Row (Band)
One Arm Upright Row (Dumbbell)
One Arm Wrist Curl (Dumbbell)
One Arm Zottman Preacher Curl (Dumbbell)
One Hand Pull Up (Weighted)
One Leg Calf Press on Leg Press (Machine)
One Leg Donkey Calf Raise
One Leg Floor Calf Raise
One Leg Floor Calf Raise (Smith Machine)
One Leg Fly on Exercise Ball (Dumbbell)
One Leg Prone Lower Body Rotation (Stability Ball)
One Leg Squat
One Leg Squat (Barbell)
One Legged Diagonal Kick Hamstring Curl (Stability Ball)
Otis Up (Weighted)
Outside Leg Kick Push Up
Over Bench Neutral Wrist Curl (Dumbbell)
Over Bench One Arm Neutral Wrist Curl (Dumbbell)
Over Bench One Arm Reverse Wrist Curl (Dumbbell)
Over Bench One Arm Wrist Curl (Dumbbell)
Over Bench Revers Wrist Curl (Dumbbell)
Over Bench Wrist Curl (Dumbbell)
Overhand Triceps Dip (Machine)
Overhead Crunch (on Stability Ball) (Weighted)
Overhead Curl on Exercise Ball (Cable)
Overhead Slam (Medicine Ball)
Overhead Triceps Extension (rope Attachment) (Cable)
Palm Rotational Bent Over Row (Dumbbell)
Palm Rotational Row (Cable)
Palms Down Wrist Curl Over a Bench (Barbell)
Palms Up Wrist Curl Over a Bench (Barbell)
Palms in Incline Bench Press (Dumbbell)
Parallel Close Grip Pull Up (Assisted)
Peacher Hammer Curl (Dumbbell)
Pelvic Tilt Into Bridge
Pelvic Tilt
Pike Push Up (Stability Ball)
Pike to Cobra Push Up
Pin Presses (Barbell)
Pirate Supper Legs (Kettlebell)
Pistol Squat (Kettlebell)
Plyo Push Up
Plyo Push Up (Kettlebell)
Plyo Squat (Dumbbell)
Posterior Step to Overhead Reach
Potty Squat with Support
Potty Squat
Power Point Plank
Preacher Curl Over Exercise Ball (Dumbbell)
Press Sit Up (Barbell)
Press on Exercise Ball (Cable)
Press on Exercise Ball (Dumbbell)
Pronate Grip Triceps Extension (Dumbbell)
Prone Hamstring (Assisted)
Prone Incline Curl (Barbell)
Prone Incline Curl (Dumbbell)
Prone Incline Hammer Curl (Dumbbell)
Prone Leg Raise (Stability Ball)
Prone Twist on Stability Ball (Stability Ball)
Pull Through (with Rope) (Cable)
Pull Through (Band)
Pull in (on Stability Ball) (Stability Ball)
Pulldown (pro Lat Bar) (Cable)
Pulldown Bicep Curl (Cable)
Pulldown (Cable)
Pullover Hip Extension on Exercise Ball (Dumbbell)
Pullover on Exercise Ball (Dumbbell)
Pullover to Press (Barbell)
Push Sit Up (Band)
Push Up (on Stability Ball) (Stability Ball)
Push Up (wall)
Push Up Close Grip Off Dumbbell (Dumbbell)
Push Up Inside Leg Kick
Push Up Medicine Ball (Medicine Ball)
Push Up Plus
Push Up on Bosu Ball (Bosu Ball)
Push Up on Lower Arms
Push Up to Side Plank
Push Up (Suspension)
Push and Pull Bodyweight
Push to Run
Pushdown (with Rope Attachment) (Cable)
Pushdown (Cable)
Quads
Quarter Sit Up
Raise Single Arm Push Up
Raise (Dumbbell)
Rear Decline Bridge
Rear Delt Raise (Barbell)
Rear Delt Raise (Dumbbell)
Rear Delt Row (stirrups) (Cable)
Rear Delt Row (with Rope) (Cable)
Rear Delt Row (Smith Machine)
Rear Delt Row_shoulder (Dumbbell)
Rear Drive (Cable)
Rear Fly (Dumbbell)
Rear Lateral Raise (support Head) (Dumbbell)
Rear Lateral Raise (Dumbbell)
Rear Lunge (Barbell)
Rear Lunge (Dumbbell)
Rear Pull Up
Rear Pulldown (Cable)
Reclining Big Toe Pose with Rope (Rope)
Revers Grip Biceps Curl (Dumbbell)
Reverse Bench Press (Dumbbell)
Reverse Calf Raises (Smith Machine)
Reverse Close Grip Bench Press (Barbell)
Reverse Crunch (Cable)
Reverse Crunch (Suspension)
Reverse Decline Close Grip Bench Press (Smith Machine)
Reverse Dip
Reverse Grip Bent Over Row (Barbell)
Reverse Grip Bent Over Row (EZ Bar)
Reverse Grip Bent Over Row (Smith Machine)
Reverse Grip Curl (EZ Bar)
Reverse Grip Decline Bench Press (Barbell)
Reverse Grip Incline Bench One Arm Row (Dumbbell)
Reverse Grip Incline Bench Press (Barbell)
Reverse Grip Incline Bench Row (Barbell)
Reverse Grip Incline Bench Two Arm Row (Dumbbell)
Reverse Grip Lateral Pulldown (Machine)
Reverse Grip Machine Lat Pulldown (Machine)
Reverse Grip Preacher Curl (EZ Bar)
Reverse Grip Preacher Curl (Machine)
Reverse Grip Press (Smith Machine)
Reverse Grip Pull Up
Reverse Grip Pushdown (Cable)
Reverse Grip Skullcrusher (Barbell)
Reverse Grip Straight Back Seated High Row (Cable)
Reverse Grip Triceps Pushdown (sz Bar) (with Arm Blaster) (Cable)
Reverse Grip Vertical Row (Machine)
Reverse Hyper Extension (on Stability Ball) (Stability Ball)
Reverse Hyper on Flat Bench
Reverse Hyperextension (Machine)
Reverse One Arm Curl (Cable)
Reverse Plank with Leg Lift
Reverse Preacher Curl (Barbell)
Reverse Preacher Curl (Cable)
Reverse Preacher Curl (Dumbbell)
Reverse Spider Curl (Dumbbell)
Reverse T Bar Row (Machine)
Reverse Wrist Curl (Band)
Rocky Pull Up Pulldown
Roller Body Saw (Roller)
Roller Reverse Crunch (Roller)
Roller Seated Shoulder Flexor Depresor Retractor (Roller)
Roller Seated Single Leg Shoulder Flexor Depresor Retractor (Roller)
Rollerout From Bench (Barbell)
Rollerout (Barbell)
Rope Climb (Rope)
Rope Crossover Seated Row (Cable)
Rope Elevated Seated Row (Cable)
Rope Extension Incline Bench Row (Cable)
Rope Hammer Preacher Curl (Cable)
Rope High Pulley Overhead Tricep Extension (Cable)
Rope Incline Tricep Extension (Cable)
Rope Lying on Floor Tricep Extension (Cable)
Rope One Arm Hammer Preacher Curl (Cable)
Rope Seated Row (Cable)
Rotary Calf (Machine)
Rotation Reverse Fly (Dumbbell)
Round Arm (Weighted)
Row (Suspension)
Run (equipment)
Run
Russian Twist (legs Up) (Weighted)
Russian Twists (on Stability Ball) (Cable)
Scapula Dips
Scapula Push Up
Scott Press (Dumbbell)
Seated Alternate Front Raise (Dumbbell)
Seated Alternate Hammer Curl on Exercise Ball (Dumbbell)
Seated Alternate Press (Dumbbell)
Seated Alternate Shoulder (Dumbbell)
Seated Behind Head Military Press (Barbell)
Seated Bench Extension (Dumbbell)
Seated Bent Arm Lateral Raise (Dumbbell)
Seated Bent Over Alternate Kickback (Dumbbell)
Seated Bent Over Triceps Extension (Dumbbell)
Seated Bicep Curl (on Stability Ball) (Weighted)
Seated Bicep Curl (Dumbbell)
Seated Biceps Curl (on Stability Ball) (Dumbbell)
Seated Biceps Curl to Shoulder Press (Dumbbell)
Seated Biceps Curl (Band)
Seated Bradford Rocky Press (Barbell)
Seated Calf Press (Machine)
Seated Chest Press (Band)
Seated Chest Press (Cable)
Seated Close Grip Behind Neck Triceps Extension (Barbell)
Seated Close Grip Concentration Curl (Barbell)
Seated Close Grip Concentration Curl (EZ Bar)
Seated Crunch (chest Pad) (Machine)
Seated Crunch (Cable)
Seated Crunch (Machine)
Seated Curl (Cable)
Seated Curl (Dumbbell)
Seated Curls (EZ Bar)
Seated Fly (Machine)
Seated Front Raise (Dumbbell)
Seated Good Morning (Barbell)
Seated Good Morning (Machine)
Seated Hammer Curl (Dumbbell)
Seated High Row (v Bar) (Cable)
Seated Hip Abduction (Band)
Seated Hip Abduction (Machine)
Seated Hip Adduction (Machine)
Seated Hip Internal Rotation (Band)
Seated Inner Biceps Curl (Dumbbell)
Seated Kickback (Dumbbell)
Seated Leg Raise Crunch (Machine)
Seated Leg Raise
Seated Neutral Wrist Curl (Dumbbell)
Seated One Arm Alternate Row (Cable)
Seated One Arm Bicep Curl on Exercise Ball with Leg Raised (Dumbbell)
Seated One Arm Concentration Curl (Cable)
Seated One Arm Kickback (Dumbbell)
Seated One Arm Rotate (Dumbbell)
Seated One Leg Calf Raise Hammer Grip (Dumbbell)
Seated One Leg Calf Raise Palm Up (Dumbbell)
Seated One Leg Calf Raise (Dumbbell)
Seated One Leg Calf Raise (Smith Machine)
Seated Overhead Curl (Cable)
Seated Overhead Triceps Extension (Barbell)
Seated Preacher Curl (Dumbbell)
Seated Press (Kettlebell)
Seated Rear Lateral Raise (Cable)
Seated Revers Grip Concentration Curl (Dumbbell)
Seated Reverse Fly (parallel Grip) (Machine)
Seated Reverse Fly (Machine)
Seated Reverse Grip One Arm Overhead Tricep Extension (Dumbbell)
Seated Shoulder Internal Rotation (Cable)
Seated Shoulder Press (parallel Grip) (Dumbbell)
Seated Shoulder Press (Band)
Seated Shoulder Press (Dumbbell)
Seated Shoulder Press (Smith Machine)
Seated Side Crunch (wall)
Seated Squat Calf Raise on Leg Press Machine (Machine)
Seated Straight Back Row (Band)
Seated Triceps Extension (Dumbbell)
Seated Triceps Extension (EZ Bar)
Seated Twist (on Stability Ball) (Weighted)
Seated Twist (Band)
Seated Twist (Barbell)
Seated Twist (Cable)
Seated Two Arm Military Press (Kettlebell)
Seated Wide Angle Pose Sequence
Seated Wrist Curl (Smith Machine)
Seesaw Press (Kettlebell)
Self Assisted Inverse Leg Curl (on Floor)
Self Assisted Inverse Leg Curl
Short Stride Run
Shoulder Grip Pull Up
Shoulder Press (Band)
Shoulder Press (Smith Machine)
Shoulder Tap Push Up
Shoulder Tap
Shrug (Band)
Side Bend (on Stability Ball) (Weighted)
Side Bend Crunch (bosu Ball) (Cable)
Side Bridge Hip Abduction
Side Crunch (Cable)
Side Hip (on Parallel Bars)
Side Hip Abduction
Side Lying Biceps Curl
Side Lying One Hand Raise (Dumbbell)
Side Plank Hip Adduction
Side Plank with Rear Fly (Dumbbell)
Side Push Up
Side Split Squat (Barbell)
Side Triceps Extension (Band)
Side to Side Chin
Single Arm Overhead Carry (Dumbbell)
Single Arm Push Up
Single Leg Bridge with Outstretched Leg
Single Leg Calf Raise (on a Dumbbell) (Dumbbell)
Single Leg Calf Raise (Band)
Single Leg Calf Raise (Dumbbell)
Single Leg Deadlift with Stepbox Support (Dumbbell)
Single Leg Deadlift (Barbell)
Single Leg Deadlift (Dumbbell)
Single Leg Platform Slide
Single Leg Reverse Calf Raise (Band)
Single Leg Split Squat (Band)
Single Leg Split Squat (Barbell)
Single Leg Split Squat (Dumbbell)
Single Leg Split Squat (Smith Machine)
Single Leg Squat (pistol) Male
Single Leg Squat (Dumbbell)
Sit Up with Arms on Chest
Sit Up (Assisted)
Sitted Alternate Leg Raise (Barbell)
Skater Hops
Ski Ergometer
Ski Step
Skier (Barbell)
Skin the Cat
Sledge Hammer (Hammer)
Speed Squat (Barbell)
Spell Caster (Dumbbell)
Sphinx
Spider Crawl Push Up
Spine Twist
Split Squat (Suspension)
Sprint Lunge (Smith Machine)
Squat (on Knees) (Barbell)
Squat Jerk (Barbell)
Squat Jump Step Rear Lunge (Barbell)
Squat Row (with Rope Attachment) (Cable)
Squat on Bosu Ball (Bosu Ball)
Squat to Overhead Reach with Twist
Squat to Overhead Reach
Squat (Weighted)
Squatting Curl (Cable)
Squatting Row
Stalder Press
Standing Ab Rollerout (Barbell)
Standing Alternate Hammer Curl and Press (Dumbbell)
Standing Alternate Overhead Press (Dumbbell)
Standing Alternate Raise (Dumbbell)
Standing Alternating Tricep Kickback (Dumbbell)
Standing Archer
Standing Around World (Dumbbell)
Standing Back Wrist Curl (Barbell)
Standing Back Wrist Curl (Cable)
Standing Back Wrist Curl (Smith Machine)
Standing Behind Head Military Press (Smith Machine)
Standing Behind Neck Press (Barbell)
Standing Bent Over One Arm Triceps Extension (Dumbbell)
Standing Bent Over Two Arm Triceps Extension (Dumbbell)
Standing Biceps Curl (Dumbbell)
Standing Bradford Press (Barbell)
Standing Calf Raise (on a Staircase)
Standing Calf Raise (Cable)
Standing Calves
Standing Chest Press (Machine)
Standing Chin Up (Assisted)
Standing Close Grip Curl (Barbell)
Standing Close Grip Military Press (Barbell)
Standing Close Grip One Arm Row
Standing Close Grip Row
Standing Concentration Curl (Barbell)
Standing Concentration Curl (Dumbbell)
Standing Cross Over High Reverse Fly (Cable)
Standing Crunch (with Rope Attachment) (Cable)
Standing Crunch (Band)
Standing Crunch (Cable)
Standing Curl (Weighted)
Standing Fly (Cable)
Standing French Press (EZ Bar)
Standing Front Raise Above Head (Dumbbell)
Standing Front Raise Over Head (Barbell)
Standing Hand Squeeze (Weighted)
Standing Hip Extension (Cable)
Standing Inner Curl (Cable)
Standing Kickback (Dumbbell)
Standing Leg Calf Raise (Barbell)
Standing Leg Calf Raise (Smith Machine)
Standing Lift (Cable)
Standing Military Press (Smith Machine)
Standing One Arm Concentration Curl (Dumbbell)
Standing One Arm Curl (over Incline Bench) (Dumbbell)
Standing One Arm Extension (Dumbbell)
Standing One Arm Palm in Press (Dumbbell)
Standing One Arm Reverse Curl (Dumbbell)
Standing One Arm Row
Standing One Arm Triceps Extension (Cable)
Standing One Leg Calf Raise (Cable)
Standing Overhead Press (Dumbbell)
Standing Overhead Triceps Extension (Barbell)
Standing Palms in Press (Dumbbell)
Standing Pelvic Tilt
Standing Preacher Curl (Dumbbell)
Standing Pull Up (Assisted)
Standing Pulldown (with Rope) (Cable)
Standing Rear Delt Row (with Rope) (Cable)
Standing Rear Delt Row (Band)
Standing Reverse Curl (Dumbbell)
Standing Reverse Grip Curl (Barbell)
Standing Reverse Grip One Arm Overhead Tricep Extension (Cable)
Standing Rocking Leg Calf Raise (Barbell)
Standing Row (v Bar) (Cable)
Standing Row
Standing Shoulder External Rotation (Cable)
Standing Single Leg Curl
Standing Triceps Extension (Dumbbell)
Standing Twist Row (v Bar) (Cable)
Standing Twist (Barbell)
Standing Twisting Crunch (Band)
Standing Up Straight Crossovers (Cable)
Standing Wheel Rollerout (Wheel)
Standing Wide Grip Biceps Curl (Barbell)
Standing Wide Grip Biceps Curl (EZ Bar)
Standing Wide Grip Curl (Barbell)
Standing Wide Military Press (Barbell)
Standing Zottman Preacher Curl (Dumbbell)
Stationary Bike Walk
Step Up Lunge (Dumbbell)
Step Up Single Leg Balance with Bicep Curl (Dumbbell)
Step Up Split Squat (Dumbbell)
Step Up (Band)
Stiff Leg Deadlift (Band)
Stiff Leg Good Morning (Barbell)
Straddle Maltese
Straddle Planche
Straight Arm Pulldown (with Rope) (Cable)
Straight Arm Pulldown (Cable)
Straight Arm Pullover (Dumbbell)
Straight Back Seated Row (Cable)
Straight Back Stiff Leg Deadlift (Band)
Straight Bar Dip (Weighted)
Straight Leg Deadlift (Barbell)
Straight Leg Deadlift (Dumbbell)
Straight Leg Outer Hip Abductor
Sumo High Pull (Kettlebell)
Sumo Pull Through (Dumbbell)
Sumo Squat (Smith Machine)
Superman Push Up
Supine Chest Throw (Medicine Ball)
Supine Reverse Fly (Cable)
Supine Triceps Extension (Stability Ball)
Supported Squat (Dumbbell)
Svend Press (Weighted)
Swing 360
T Bar Reverse Grip Row (Machine)
Thibaudeau Kayak Row (Cable)
Three Bench Dip
Three Bench Dips (Weighted)
Tire Flip (Tire)
Toe Raise (Smith Machine)
Tricep Kickback with Stork Stance (Dumbbell)
Triceps Dip (bench Leg)
Triceps Dip (between Benches)
Triceps Dip (kneeling) (Assisted)
Triceps Dip on High Parallel Bars (Weighted)
Triceps Press
Triceps Pushdown (v Bar) (with Arm Blaster) (Cable)
Tuck Crunch
Tuck Reverse Crunch (Cable)
Turkish Get Up (squat Style) (Kettlebell)
Twin Handle Parallel Grip Lat Pulldown (Cable)
Twist (up Down) (Cable)
Twist Hip Lift
Twisted Leg Raise
Twisting Bench Press (Dumbbell)
Twisting Overhead Press (Band)
Twisting Pull (Cable)
Two Arm Clean (Kettlebell)
Two Arm Curl on Incline Bench (Cable)
Two Arm Military Press (Kettlebell)
Two Arm Row (Kettlebell)
Two Arm Tricep Kickback (Cable)
Underhand Pulldown (Band)
Underhand Pulldown (Cable)
Unilateral Row (Machine)
Upper Chest Crossovers (Cable)
Upper Row (Cable)
Upright Row (back Pov) (Dumbbell)
Upright Row (Smith Machine)
Upright Shoulder External Rotation (Dumbbell)
Upward Facing Dog
V Sit on Floor
V Up (Band)
Vertical Leg Raise (on Parallel Bars)
Vertical Pallof Press (Band)
W Press (Dumbbell)
Waiter Biceps Curl (Dumbbell)
Walk Elliptical Cross Trainer
Walking High Knees Lunge
Walking on Incline Treadmill
Walking on Stepmill
Wheel Rollerout (Wheel)
Wheel Run
Wide Bench Press (Barbell)
Wide Grip Bench Press (Smith Machine)
Wide Grip Chest Dip (kneeling) (Assisted)
Wide Grip Chest Dip on High Parallel Bars
Wide Grip Decline Bench Press (Smith Machine)
Wide Grip Pull Up
Wide Grip Rear Pull Up
Wide Grip Rear Pulldown Behind Neck (Cable)
Wide Grip Upright Row (Barbell)
Wide Hand Push Up
Wide Reverse Grip Bench Press (Barbell)
Wide Squat (Barbell)
Wind Sprints
Windmill (Kettlebell)
Wrist Circles
Wrist Curl (Band)
Wrist Rollerer (Weighted)
Y Raise (Band)
Zottman Preacher Curl (Dumbbell)
"""

internal val NAMES_BEFORE: List<String> get() = (NAMES_0 + NAMES_1).lines().filter { it.isNotEmpty() }
