package clean.core

/*
 * More built-in exercises, generated from the open exercise dataset that openGym ships
 * (hasaneyldrm/exercises-dataset). Only facts were taken: names, equipment and muscles. The names
 * were rewritten into the `Movement (Equipment)` form by a script, so some read less naturally than
 * the hand-written table in Library.kt; correct a line here when one is wrong. Same line format,
 * with `~` marking a minor muscle; the split into supporting and minor was made by rule (grip, trunk
 * and lower-leg helpers are minor).
 * Nothing here duplicates the hand-written table or its aliases.
 */
private const val MORE_0 = """
3/4 Sit Up: - | AB | ~LB | bw
45 Degrees One Leg Press: Machine | GL | QD HM ~CV
45° Leg Press (side Pov): Machine | GL | QD HM ~CV
45° Side Bend: - | AB | OB | bw
45в° Calf Press: Machine | CV | HM
45в° Leg Press (back Pov): Machine | GL | QD HM ~CV
45в° Leg Press: Machine | GL | QD HM ~CV
45в° Leg Wide Press: Machine | GL | QD HM ~CV
Abdominal Fallout: Suspension | AB | OB | bw
Advanced Windmill: Kettlebell | AB | OB SH
Alternate Biceps Curl (with Arm Blaster): Dumbbell | BI | ~FA
Alternate Biceps Curl: Barbell | BI | ~FA
Alternate Biceps Curl: Dumbbell | BI | ~FA
Alternate Hammer Preacher Curl: Dumbbell | BI | ~FA
Alternate Heel Touchers: - | AB | OB | bw
Alternate Lateral Pulldown: Cable | LT | BI UB ~FA
Alternate Leg Press: Machine | QD | HM GL
Alternate Preacher Curl: Dumbbell | BI | ~FA
Alternate Seated Hammer Curl: Dumbbell | BI | ~FA
Alternate Shoulder Press: Cable | SH | TR UB
Alternate Side Press: Dumbbell | SH | TR ~AB
Alternate Triceps Extension: Cable | TR | SH
Alternating Arm Ups: Stability Ball | LT | SH ~AB ~FA | bw
Alternating Bicep Curl with Leg Raised on Exercise Ball: Dumbbell | BI | SH ~FA
Alternating Biceps Curl: Band | BI | ~FA
Alternating Hang Clean: Kettlebell | FA | SH TP ~AB
Alternating Narrow Grip Seated Row: Machine | UB | BI ~FA
Alternating Press on Floor: Kettlebell | CH | SH TR
Alternating Press: Kettlebell | SH | TR ~AB
Alternating Renegade Row: Kettlebell | UB | SH ~AB ~FA
Alternating Row: Kettlebell | UB | BI ~FA
Alternating Seated Bicep Curl on Exercise Ball: Dumbbell | BI | ~FA
Alternating V Up: Band | AB | 
Ankle Circles: - | CV |  | bw
Anti Gravity Press: EZ Bar | SH | TR CH
Archer Pull Up: - | LT | BI ~FA | bw
Arm Slingers Hanging Bent Knee Legs: - | AB | SH UB | bw
Arm Slingers Hanging Straight Legs: - | AB | SH UB | bw
Arnold Press: Kettlebell | SH | TR UB
Around Pullover: Dumbbell | CH | TR LT
Assisted Chin Up: Machine | LT | BI ~FA
Assisted Inverse Leg Curl: Cable | HM | GL ~CV
Assisted Pull Up: Band | LT | BI ~FA
Assisted Wheel Rollerout: Band | AB | ~LB
Back Extension on Exercise Ball: Stability Ball | LB | GL HM | bw
Back Extension with Arms Extended: Stability Ball | LB | GL HM | bw
Back Extension with Hands Behind Head: Stability Ball | LB | GL HM | bw
Back Extension with Knees Off Ground: Stability Ball | LB | GL HM | bw
Back Extension with Rotation: Stability Ball | LB | GL HM | bw
Back Lever: - | UB | BI ~FA ~AB | bw
Back Shrug: Smith Machine | TP | SH
Back and Forth Step: - | | | cardio
Backward Jump: - | QD | HM GL ~CV | bw
Balance Board: - | QD | HM GL ~CV | bw
Bar Lateral Pulldown: Cable | LT | BI UB SH ~FA
Battling Ropes: Rope | SH | ~FA ~AB
Bear Crawl: - | | | cardio
Bench Dip (knees Bent): - | TR | CH SH | bw
Bench Dip on Floor: - | TR | CH SH | bw
Bench Dip: Weighted | TR | CH SH | bw
Bench Front Squat: Barbell | QD | HM GL ~CV ~AB
Bench Hip Extension: - | GL | HM | bw
Bench Pull Ups: - | LT | BI ~FA | bw
Bench Seated Press: Dumbbell | SH | TR CH
Bench Squat: Barbell | QD | GL HM ~CV ~AB
Bench Squat: Dumbbell | GL | QD HM ~CV ~AB
Bent Arm Pullover: Barbell | LT | TR CH ~FA
Bent Knee Good Morning: Smith Machine | GL | HM ~LB ~AB
Bent Over Hip Extension: Band | GL | HM ~LB
Bent Over Row with V Bar: Machine | UB | BI ~FA ~AB
Bent Over Row: Machine | UB | BI ~FA ~AB
Bent Press: Kettlebell | AB | SH TR OB
Bicep Curl Lunge with Bowling Motion: Dumbbell | BI | SH ~FA ~AB
Bicep Curl on Exercise Ball with Leg Raised: Dumbbell | BI | SH ~FA
Bicep Curl with Stork Stance: Dumbbell | BI | ~FA
Bicep Curl: Smith Machine | BI | ~FA
Biceps Curl (with Arm Blaster): Barbell | BI | ~FA
Biceps Curl (with Arm Blaster): Dumbbell | BI | ~FA
Biceps Curl (with Arm Blaster): EZ Bar | BI | ~FA
Biceps Curl Reverse: Dumbbell | BI | ~FA
Biceps Curl Squat: Dumbbell | BI | ~FA ~AB
Biceps Curl V Sit on Bosu Ball: Dumbbell | BI | SH ~FA
Biceps Leg Concentration Curl: - | BI | ~FA | bw
Biceps Narrow Pull Ups: - | BI | SH ~FA | bw
Biceps Pull Up: - | BI | SH ~FA | bw
Bicycle Crunch: Band | AB | OB
Bottoms Up Clean From the Hang Position: Kettlebell | BI | SH ~FA ~AB | time
Bottoms Up: - | AB | OB | bw
Box Jump Down with One Leg Stabilization: - | CV | QD HM GL | bw
Bridge Mountain Climber (cross Body): - | AB | GL QD HM SH | bw
Butt Ups: - | AB | ~LB | bw
Butterfly Yoga Pose: - | AD | HM | bw
Calf Press on Leg Press: Machine | CV | HM QD
Cambered Bar Lying Row: Barbell | UB | BI ~FA
Captains Chair Straight Leg Raise: - | AB | HF | bw
Catch and Overhead Throw: Medicine Ball | LT | SH ~AB ~FA
Chair Squat: Smith Machine | QD | GL HM ~CV ~AB
Chest Dip (kneeling): Assisted | CH | TR SH | bw
Chest Dip (on Dip Pull Up Cage): - | CH | TR SH | bw
Chest Dip on Straight Bar: - | CH | TR SH | bw
Chest Pass: Medicine Ball | CH | SH TR
Chest Push From 3 Point Stance: Medicine Ball | CH | SH TR
Chest Push Multiple Response: Medicine Ball | CH | TR SH
Chest Push Single Response: Medicine Ball | CH | TR SH
Chest Push with Run Release: Medicine Ball | CH | TR SH ~AB
Chin Ups (narrow Parallel Grip): - | UB | BI ~FA | bw
Clean Grip Front Squat: Barbell | GL | QD HM ~CV ~AB
Clean: Dumbbell | GL | HM QD ~CV ~AB
Clock Push Up: - | CH | TR SH ~AB | bw
Close Grip Bench Press: EZ Bar | TR | CH SH
Close Grip Chin Up on Dip Cage: Weighted | LT | BI ~FA | bw
Close Grip Chin Up: - | LT | BI ~FA | bw
Close Grip Curl: Cable | BI | ~FA
Close Grip Curl: EZ Bar | BI | ~FA
Close Grip Preacher Curl: EZ Bar | BI | ~FA
Close Grip Press: Dumbbell | TR | CH SH
Close Grip Pulldown: Band | LT | BI ~FA
Close Grip Push Up (on Knees): - | TR | CH SH ~AB | bw
Close Grip Push Up: Band | TR | CH SH ~AB
Close Grip Push Up: Medicine Ball | TR | CH SH ~AB
Closer Hack Squat: Machine | GL | QD HM ~CV ~AB
Cocoons: - | AB | ~LB | bw
Concentration Curl: Band | BI | ~FA
Concentration Extension (on Knee): Cable | TR | ~FA
Contralateral Forward Lunge: Dumbbell | GL | QD HM ~CV ~AB
Crab Twist Toe Touch: - | AB | OB | bw
Cross Over Lateral Pulldown: Cable | LT | BI UB SH ~FA
Cross Over Revers Fly: Cable | SH | UB TP
Cross Over Variation: Cable | CH | SH TR
Crunch (full Range Hands Behind Head): Stability Ball | AB |  | bw
Crunch (hands Overhead): - | AB |  | bw
Crunch (on Stability Ball): Stability Ball | AB |  | bw
Crunch (on Stability Ball, Arms Straight): Stability Ball | AB |  | bw
Crunch Floor: - | AB |  | bw
Curl Up: - | AB |  | bw
Curl: Barbell | BI | ~FA
Curl: Cable | BI | ~FA
Curl: EZ Bar | BI | ~FA
Curtsey Squat: - | GL | QD HM ~CV ~AB | bw
Cycle Cross Trainer: - | | | cardio
Deadlift: Cable | GL | HM QD ~LB ~AB
Deadlift: Machine | GL | HM QD ~AB
Decline Bent Arm Pullover: Barbell | LT | TR CH ~FA
Decline Close Grip Bench Press: Smith Machine | TR | CH SH
Decline Close Grip Face Press: EZ Bar | TR | CH SH
Decline Close Grip to Skull Press: Barbell | TR | CH SH
Decline Fly: Cable | CH | SH TR
Decline Fly: Dumbbell | CH | SH
Decline Hammer Press: Dumbbell | CH | TR SH
Decline One Arm Fly: Dumbbell | CH | SH
Decline One Arm Hammer Press: Dumbbell | TR | CH SH
Decline One Arm Press: Cable | CH | TR SH
Decline Press: Cable | CH | TR SH
Decline Pullover: Barbell | CH | TR SH
Decline Reverse Grip Press: Smith Machine | CH | TR SH
Decline Seated Wide Grip Row: Cable | UB | BI ~FA
Decline Shrug: Dumbbell | TP | SH UB
Decline Sit Up: Weighted | AB | ~LB | bw
Decline Triceps Extension: Dumbbell | TR | SH
Decline Triceps Extension: EZ Bar | TR | SH
Decline Twist Fly: Dumbbell | CH | SH TR
Decline Wide Grip Press: Barbell | CH | TR SH
Decline Wide Grip Pullover: Barbell | LT | TR CH ~FA
Deep Push Up: Dumbbell | CH | TR SH ~AB
Dip: Stability Ball | TR | CH SH | bw
Dips: Rings | TR | CH SH | bw
Donkey Calf Raise: - | CV | HM GL | bw
Donkey Calf Raise: Weighted | CV | HM GL | bw
Double Alternating Hang Clean: Kettlebell | BI | SH ~FA ~AB
Double Jerk: Kettlebell | SH | TR ~AB
Double Push Press: Kettlebell | SH | TR ~AB
Double Snatch: Kettlebell | SH | TP ~FA ~AB
Double Windmill: Kettlebell | AB | SH HM
Drag Curl: Cable | BI | ~FA
Drop Jump Squat: - | GL | QD HM ~CV ~AB | bw
Drop Push Up: - | CH | TR SH ~AB | bw
Drop Push Up: Weighted | CH | TR SH ~AB | bw
Dumbbells Seated Triceps Extension: Dumbbell | TR | SH
Elbow Dips: - | TR | CH SH | bw
Elbow Lift Reverse Push Up: - | UB | TR SH ~FA ~AB | bw
Elevator: - | UB | SH TP ~FA | bw
Extended Range One Arm Press on Floor: Kettlebell | CH | TR SH
Farmers Walk: Dumbbell | QD | ~CV ~FA ~AB
Figure 8: Kettlebell | AB | SH ~FA
Finger Curls: Barbell | FA | 
Finger Curls: Dumbbell | FA | 
Fixed Back Close Grip Pulldown: Band | LT | BI ~FA
Fixed Back Underhand Pulldown: Band | LT | BI UB SH ~FA
Flag: - | AB | OB SH | bw
Flexion Leg Sit Up (bent Knee): - | AB |  | bw
Flexion Leg Sit Up (straight Arm): - | AB | QD | bw
Floor Calf Raise: Barbell | CV | HM
Floor Fly (with Barbell): Barbell | CH | SH TR
Floor Seated Wide Grip Row: Cable | UB | BI ~FA
Fly on Exercise Ball: Dumbbell | CH | SH TR
Fly: Dumbbell | CH | SH
Forward Angled Calf Raise: Machine | CV | HM GL
Forward Jump: - | QD | HM GL ~CV | bw
Forward Lunge Triceps Extension: Dumbbell | TR | SH ~AB
Forward Raise: Cable | SH | TR ~FA ~TP
Frankenstein Squat: Barbell | GL | QD HM ~CV ~AB
French Press on Exercise Ball: EZ Bar | TR | SH
Frog Crunch: - | AB |  | bw
Frog Planche: - | AB | SH CH TR | bw
Front Chest Squat: Barbell | GL | QD HM ~CV ~AB
Front Lateral Raise: Band | SH | TP UB
Front Lever Reps: - | UB | SH ~AB ~FA | bw
Front Lever: - | AB | LT SH ~FA | bw
Front Plank with Twist: - | AB | OB SH | time
Front Plank: Weighted | AB | SH ~LB | time
Front Pulldown: Machine | LT | BI UB SH ~FA
Front Raise and Pullover: Barbell | CH | SH TR
Front Raise: Weighted | SH | BI ~FA ~TP | bw
Front Shoulder Raise: Cable | SH | TP BI
Front Squat (clean Grip): Smith Machine | GL | QD HM ~CV ~AB
Full Can Lateral Raise: Dumbbell | SH | TP
Full Maltese: - | AB | SH CH | bw
Full Planche Push Up: - | CH | SH TR ~AB | bw
Full Planche: - | AB | SH CH TR | bw
Full Squat (back Pov): Barbell | GL | QD HM ~CV ~AB
Full Squat (side Pov): Barbell | GL | QD HM ~CV ~AB
Full Squat: Barbell | GL | QD HM ~CV ~AB
Full Squat: Smith Machine | GL | QD HM ~CV ~AB
Full Zercher Squat: Barbell | GL | QD HM ~CV ~AB
Gironda Sternum Chin: - | LT | BI UB SH ~FA | bw
Glute Bridge March: - | GL | HM QD | bw
Gorilla Chin: - | AB | BI ~FA | bw
Gripless Shrug: Machine | TP | SH ~FA
Gripper Hands: Machine | FA | BI TR
Groin Crunch: - | AB | ~AD | bw
Guillotine Bench Press: Barbell | CH | SH TR
Hack Calf Raise: Machine | CV | HM GL
Hack One Leg Calf Raise: Machine | CV | HM GL
Hack Squat: Smith Machine | GL | QD HM ~CV ~AB
Hammer Curl (with Rope): Cable | BI | ~FA
Hammer Curl on Exercise Ball: Dumbbell | BI | ~FA
Hammer Curl: Barbell | BI | ~FA
Hammer Curls (with Arm Blaster): Dumbbell | BI | ~FA
Hammer Grip Preacher Curl: Machine | BI | ~FA
Hands Bike: - | CH | TR SH | bw
Handstand: - | TR | SH ~AB | bw
Hang Clean: Kettlebell | HM | GL QD ~CV ~AB
Hanging Knee Raise with Throw Down: Assisted | AB | ~LB | bw
Hanging Knee Raise: Assisted | AB | HF | bw
Hanging Leg Hip Raise: - | AB | ~LB | bw
Hanging Leg Hip Raise: Weighted | AB | ~LB | bw
Hanging Oblique Knee Raise: - | AB | OB HF | bw
Hanging Pike: - | AB | SH | bw
Hanging Straight Leg Hip Raise: - | AB |  | bw
Hanging Straight Leg Raise: - | AB | HF | bw
Hanging Straight Twisting Leg Hip Raise: - | AB | OB | bw
High Bar Squat: Barbell | GL | QD HM ~CV ~AB
High Curl: Dumbbell | BI | ~FA
High Knee Against Wall: - | | | cardio
High Pulley Overhead Tricep Extension: Cable | TR | SH
High Row (kneeling): Cable | UB | BI SH ~FA
High Row: Machine | UB | BI SH ~FA
Hip Adduction: Cable | AD | GL QD
Hip Lift: Band | GL | HM QD
Hip Raise (bent Knee): - | AB | GL HM | bw
Hip Raise: Smith Machine | AB | GL HM
Horizontal One Leg Press: Machine | GL | QD HM ~CV
Horizontal Pallof Press: Band | AB | OB GL
Hug Keens to Chest: - | GL | HM QD | bw
Hug: Stability Ball | LB | SH CH | bw
Hyght Dumbbell Fly: Dumbbell | CH | SH
Hyperextension (on Bench): - | LB | GL HM | bw
Hyperextension (on Stability Ball): Weighted | LB | GL HM | bw
Impossible Dips: - | TR | CH SH | bw
Inchworm: - | AB | SH HM | bw
Incline Alternate Press: Dumbbell | CH | SH TR
Incline Bench Row: Cable | UB | BI SH ~FA
Incline Biceps Curl: Dumbbell | BI | ~FA
Incline Breeding: Dumbbell | CH | SH TR
Incline Close Grip Bench Press: Barbell | TR | CH SH
Incline Close Grip Push Up: - | TR | CH SH ~AB | bw
Incline Fly (on Stability Ball): Cable | CH | SH TR
Incline Fly on Exercise Ball: Dumbbell | CH | SH
Incline Fly: Cable | CH | SH TR
Incline Fly: Dumbbell | CH | SH
Incline Hammer Curl: Dumbbell | BI | ~FA
Incline Hammer Press on Exercise Ball: Dumbbell | TR | CH SH
Incline Hammer Press: Dumbbell | CH | SH TR
Incline Inner Biceps Curl: Dumbbell | BI | ~FA
Incline Leg Hip Raise (leg Straight): - | AB |  | bw
Incline One Arm Fly on Exercise Ball: Dumbbell | CH | SH
Incline One Arm Fly: Dumbbell | CH | SH TR
Incline One Arm Hammer Press on Exercise Ball: Dumbbell | TR | SH CH
Incline One Arm Hammer Press: Dumbbell | TR | SH CH
Incline One Arm Lateral Raise: Dumbbell | SH | TP TR
Incline One Arm Press on Exercise Ball: Dumbbell | CH | SH TR
Incline One Arm Press: Dumbbell | CH | SH TR
Incline Palm in Press: Dumbbell | CH | SH TR
Incline Press on Exercise Ball: Dumbbell | CH | SH TR
Incline Push Up (on Box): - | CH | TR SH ~AB | bw
Incline Push Up Depth Jump: - | CH | TR SH ~AB | bw
Incline Pushdown: Cable | LT | TR SH ~FA
Incline Raise: Dumbbell | SH | TP TR
Incline Rear Lateral Raise: Dumbbell | SH | TP UB
Incline Reverse Grip Press: Barbell | TR | CH SH
Incline Reverse Grip Press: Smith Machine | CH | TR SH
Incline Reverse Grip Push Up: - | CH | TR SH ~AB | bw
Incline Row: Barbell | UB | BI ~FA
Incline Scapula Push Up: - | SE | CH SH ~AB | bw
Incline Shoulder Raise: Barbell | CH | SH TP
Incline Shoulder Raise: Dumbbell | CH | SH TP
Incline Shoulder Raises: Smith Machine | CH | SH TP
Incline Shrug: Dumbbell | TP | SH
Incline Side Plank: - | AB | OB SH | time
Incline T Raise: Dumbbell | SH | TP UB
Incline Tricep Extension: Smith Machine | TR | SH
Incline Triceps Extension: Cable | TR | SH
Incline Triceps Extension: Dumbbell | TR | SH
Incline Triceps Extension: EZ Bar | TR | SH
Incline Twisted Flyes: Dumbbell | CH | SH TR
Incline Twisting Sit Up: - | AB | OB | bw
Incline Two Arm Extension: Dumbbell | TR | SH
Incline Y Raise: Dumbbell | UB | SH ~FA
Inverse Leg Curl (bench Support): - | HM | GL ~CV | bw
Inverse Leg Curl (on Pull Up Cable Machine): - | HM | GL ~CV | bw
Inverted Row Bent Knees: - | UB | BI ~FA | bw
Inverted Row on Bench: - | UB | BI ~FA | bw
Inverted Row with Straps: - | UB | BI ~FA | bw
Iron Cross: Dumbbell | SH | TR CH
Isometric Chest Squeeze: - | CH | TR SH | bw
Isometric Wipers: - | CH | SH TR ~AB | bw
Jack Burpee: - | | | cardio
Jack Knife Sit Up: Band | AB | 
Janda Sit Up: - | AB | ~LB | bw
Jefferson Squat: Barbell | GL | QD HM ~CV ~AB
Jm Bench Press: Barbell | TR | CH SH
Jm Bench Press: EZ Bar | TR | CH SH
Judo Flip: Cable | AB | OB SH
Jump Squat: Barbell | GL | QD HM ~CV ~AB
Kick Out Sit: - | HM | QD GL | bw
Kickback: Dumbbell | TR | SH
Kickbacks on Exercise Ball: Dumbbell | TR | SH UB
Kipping Muscle Up: - | LT | BI TR SH ~AB ~FA | bw
Knee Touch Crunch: - | AB |  | bw
Kneeling Bicep Curl Exercise Ball: Dumbbell | BI | ~FA
Kneeling Jump Squat: Barbell | GL | QD HM ~CV ~AB
Kneeling Leg Curl: Machine | HM | GL
Kneeling One Arm Pulldown: Band | LT | BI UB ~FA
Kneeling Step with Swing: Weighted | SH | TR ~AB | bw
Kneeling Triceps Extension: - | TR | SH CH | bw
Kneeling Triceps Extension: Cable | TR | SH
Kneeling Twist: Machine | AB | OB ~LB
Kneeling Twisting Crunch: Band | AB | OB
Korean Dips: - | CH | TR SH | bw
L Pull Up: - | LT | BI ~FA | bw
L Sit on Floor: - | AB |  | bw
Landmine 180: Barbell | AB | OB QD
Landmine Lateral Raise: Barbell | SH | TP UB ~AB
Lat Pulldown Full Range of Motion: Cable | LT | BI UB SH ~FA
Lateral Lunge: Barbell | GL | QD HM ~CV ~AB
Lateral Pulldown (with Rope Attachment): Cable | LT | BI ~FA
Lateral to Front Raise: Dumbbell | SH | TP BI
Lean Planche: - | AB | SH CH TR | bw
Left Hook. Boxing: - | SH | BI TR ~FA | bw
Leg Extension: Band | QD | HM
Leg Press: Smith Machine | GL | QD HM ~CV
Leg Pull in Flat Bench: - | AB | ~LB | bw
London Bridge: Rope | UB | SH BI ~FA
Low Bar Squat: Barbell | GL | QD HM ~CV ~AB
Low Bar Squat: Smith Machine | GL | QD HM ~CV ~AB
Low Fly: Cable | CH | SH TR
Low Glute Bridge on Floor: - | GL | HM ~AB | bw
Low Seated Row: Cable | UB | BI ~FA
Lower Back Curl: - | LB | GL HM | bw
Lunge Pass Through: Kettlebell | GL | QD HM ~CV ~AB
Lunge with Bicep Curl: Dumbbell | BI | QD HM ~FA ~AB
Lunge with Jump: - | GL | QD HM ~CV ~AB | bw
Lunge with Swing: Weighted | GL | QD HM ~CV ~AB | bw
Lunge with Twist: - | AB | QD GL HM | bw
Lying Alternate Extension: Dumbbell | TR | SH
Lying Back of the Head Tricep Extension: Barbell | TR | SH
Lying Bent Arms Pullover: EZ Bar | LT | TR CH ~FA
Lying Bicep Curl: Cable | BI | ~FA
Lying Calf Press: Machine | CV | HM GL
Lying Close Grip Curl: Cable | BI | ~FA
Lying Close Grip Press: Barbell | TR | CH SH
Lying Close Grip Triceps Extension Behind Head: EZ Bar | TR | SH
Lying Close Grip Triceps Extension: Barbell | TR | SH
Lying Elbow Press: Dumbbell | TR | CH SH
Lying Elbow to Knee: - | AB | ~LB | bw
Lying Extension (across Face): Dumbbell | TR | SH
Lying Extension Pullover (with Rope Attachment): Cable | LT | TR SH ~FA
Lying Extension: Barbell | TR | SH
Lying External Shoulder Rotation: Dumbbell | RC | SH TR
Lying Femoral: Dumbbell | HM | GL
Lying Fly: Cable | CH | SH TR
Lying Hammer Press: Dumbbell | CH | TR SH
Lying Hip Internal Rotation: Band | GL | HM QD
Lying Leg Hip Raise: - | AB |  | bw
Lying Leg Raise Flat Bench: - | AB | HF | bw
Lying Leg Raise with Lateral Throw Down: Assisted | AB | OB | bw
Lying Leg Raise with Throw Down: Assisted | AB | QD | bw
Lying Lifting (on Hip): Barbell | GL | HM QD
Lying One Arm Deltoid Rear: Dumbbell | SH | TP UB
Lying One Arm Press: Dumbbell | CH | TR SH
Lying One Arm Pronated Triceps Extension: Dumbbell | TR | SH
Lying One Arm Rear Lateral Raise: Dumbbell | SH | TP UB
Lying One Arm Supinated Triceps Extension: Dumbbell | TR | ~FA
Lying Preacher Curl: Barbell | BI | ~FA
Lying Pronation on Floor: Dumbbell | FA | BI TR
Lying Pronation: Dumbbell | FA | BI SH
Lying Pullover on Exercise Ball: Dumbbell | CH | TR SH UB
Lying Rear Delt Row: Dumbbell | UB | SH BI ~FA
Lying Rear Lateral Raise: Dumbbell | SH | TP UB
Lying Single Extension: Dumbbell | TR | SH
Lying Squat: Machine | GL | QD HM ~CV ~AB
Lying Straight Leg Raise: Band | AB | HF
Lying Supination on Floor: Dumbbell | FA | BI SH
Lying Supination: Dumbbell | FA | BI SH
Lying Supine Biceps Curl: Dumbbell | BI | ~FA
Lying Supine Curl: Dumbbell | BI | ~FA
Lying Triceps Extension Skull Crusher: Barbell | TR | SH
Lying Triceps Extension: Barbell | TR | SH
Lying Triceps Extension: Dumbbell | TR | SH
Lying Two One Leg Curl: Machine | HM | GL
Lying Wide Curl: Dumbbell | BI | ~FA
Lying on Floor Rear Delt Raise: Dumbbell | SH | TP UB
Machine Inner Chest Press: Machine | CH | TR SH
March Sit (wall): - | GL | QD HM ~CV | bw
Middle Fly: Cable | CH | SH TR
Military Press: Machine | SH | TR CH ~AB
Mixed Grip Chin Up: - | LT | BI ~FA | bw
Modified Push Up to Lower Arms: - | FA | TR CH SH ~AB | bw
Monster Walk: - | GL | HM QD | bw
Motion Russian Twist: Assisted | AB | OB ~LB | bw
Muscle Up (on Bar): Weighted | LT | BI TR SH ~FA | bw
Muscle Up (on Vertical Bar): - | LT | BI TR ~FA | bw
Muscle Up: Weighted | LT | BI TR SH CH ~FA | bw
Narrow Grip Seated Row: Machine | UB | BI ~FA
Narrow Push Up on Exercise Ball: Stability Ball | TR | CH SH ~AB | bw
Narrow Row: Smith Machine | UB | BI SH ~FA
Narrow Stance Squat: Barbell | GL | QD HM ~CV ~AB
Negative Crunch: - | AB |  | bw
Neutral Grip Bench Press: Dumbbell | TR | CH SH
Oblique Crunches Floor: - | AB | OB | bw
On the Wall Calf Raise (tennis Ball Between Ankles): Stability Ball | CV | HM QD | bw
On the Wall Calf Raise (tennis Ball Between Knees): Stability Ball | CV | QD HM | bw
On the Wall Calf Raise: Stability Ball | CV | HM QD | bw
"""

private const val MORE_1 = """
One Arm Against Wall: - | LT | SH TR ~FA | bw
One Arm Bench Fly: Dumbbell | CH | SH
One Arm Bent Over Row: Barbell | UB | BI ~FA ~AB
One Arm Bent Over Row: Cable | UB | BI ~FA ~AB
One Arm Bent Over Row: Machine | UB | BI ~FA ~AB
One Arm Chest Fly on Exercise Ball: Dumbbell | CH | SH TR
One Arm Chin Up: - | LT | BI ~FA | bw
One Arm Clean and Jerk: Kettlebell | SH | TR ~AB
One Arm Concentration Curl (on Stability Ball): Dumbbell | BI | ~FA
One Arm Curl: Cable | BI | ~FA
One Arm Decline Chest Fly: Cable | CH | SH TR
One Arm Decline Chest Press: Dumbbell | CH | TR SH
One Arm Dip: - | TR | CH SH | bw
One Arm Floor Press: Barbell | TR | CH SH
One Arm Floor Press: Kettlebell | CH | TR SH
One Arm Fly on Exercise Ball: Cable | CH | SH TR
One Arm Fly on Exercise Ball: Dumbbell | CH | SH TR
One Arm French Press on Exercise Ball: Dumbbell | TR | SH
One Arm Hammer Preacher Curl: Dumbbell | BI | ~FA
One Arm Hammer Press on Exercise Ball: Dumbbell | TR | SH CH
One Arm Incline Chest Press: Dumbbell | CH | SH TR
One Arm Incline Fly on Exercise Ball: Cable | CH | SH TR
One Arm Incline Press on Exercise Ball: Cable | CH | SH TR
One Arm Incline Press: Cable | CH | SH TR
One Arm Jerk: Kettlebell | SH | TR ~AB
One Arm Kickback: Dumbbell | TR | SH
One Arm Lateral Bent Over: Cable | CH | SH TP
One Arm Lateral High Row: Machine | UB | BI SH ~FA
One Arm Lateral Raise with Support: Dumbbell | SH | TP
One Arm Lateral Raise: Cable | SH | TP TR
One Arm Lateral Raise: Dumbbell | SH | TP TR
One Arm Lateral Wide Pulldown: Machine | LT | BI UB SH ~FA
One Arm Military Press to the Side: Kettlebell | SH | TR ~AB
One Arm Overhead Biceps Curl: Band | BI | SH ~FA
One Arm Preacher Curl: Cable | BI | ~FA
One Arm Press on Exercise Ball: Cable | CH | SH TR
One Arm Press on Exercise Ball: Dumbbell | CH | SH TR ~AB
One Arm Prone Curl: Dumbbell | BI | ~FA
One Arm Prone Hammer Curl: Dumbbell | BI | ~FA
One Arm Pulldown: Cable | LT | BI ~FA
One Arm Pullover on Exercise Ball: Dumbbell | CH | TR LT UB
One Arm Push Press: Kettlebell | SH | TR ~AB
One Arm Reverse Fly (with Support): Dumbbell | SH | TP UB
One Arm Reverse Grip Press: Dumbbell | CH | TR SH
One Arm Reverse Preacher Curl: Cable | BI | ~FA
One Arm Reverse Preacher Curl: Dumbbell | BI | ~FA
One Arm Reverse Spider Curl: Dumbbell | BI | ~FA
One Arm Reverse Wrist Curl: Dumbbell | FA | 
One Arm Row: Kettlebell | UB | BI ~FA
One Arm Row: Smith Machine | UB | BI ~FA
One Arm Seated Bicep Curl on Exercise Ball: Dumbbell | BI | ~FA
One Arm Seated Hammer Curl: Dumbbell | BI | ~FA
One Arm Seated Neutral Wrist Curl: Dumbbell | FA | BI
One Arm Shoulder Press: Dumbbell | SH | TR UB
One Arm Shoulder Press: Machine | SH | TR UB
One Arm Side Deadlift: Barbell | GL | HM QD ~LB ~AB
One Arm Single Leg Split Squat: Band | QD | GL HM ~AB
One Arm Slam (with Medicine Ball): Medicine Ball | AB | SH UB
One Arm Snatch: Barbell | SH | TP ~FA ~AB
One Arm Snatch: Dumbbell | GL | HM QD ~AB
One Arm Snatch: Kettlebell | SH | TP ~FA ~AB
One Arm Standing Curl: Dumbbell | BI | ~FA
One Arm Standing Hammer Curl: Dumbbell | BI | ~FA
One Arm Standing Low Row: Band | UB | BI SH ~FA
One Arm Straight Back High Row (kneeling): Cable | UB | BI SH ~FA
One Arm Towel Row: - | UB | BI ~FA | bw
One Arm Tricep Pushdown: Cable | TR | ~FA
One Arm Triceps Extension (on Bench): Dumbbell | TR | SH
One Arm Twisting Chest Press: Band | CH | SH TR
One Arm Twisting Seated Row: Band | UB | BI SH ~FA
One Arm Upright Row: Dumbbell | SH | TP BI
One Arm Wrist Curl: Dumbbell | FA | BI
One Arm Zottman Preacher Curl: Dumbbell | BI | ~FA
One Hand Pull Up: Weighted | LT | BI ~FA | bw
One Leg Calf Press on Leg Press: Machine | CV | HM GL
One Leg Donkey Calf Raise: - | CV | HM GL | bw
One Leg Floor Calf Raise: - | CV |  | bw
One Leg Floor Calf Raise: Smith Machine | CV | HM GL
One Leg Fly on Exercise Ball: Dumbbell | CH | SH TR
One Leg Prone Lower Body Rotation: Stability Ball | GL | HM QD ~AB | bw
One Leg Squat: - | GL | QD HM ~CV ~AB | bw
One Leg Squat: Barbell | QD | GL HM ~CV ~AB
One Legged Diagonal Kick Hamstring Curl: Stability Ball | GL | HM ~CV | bw
Otis Up: Weighted | AB | ~LB | bw
Outside Leg Kick Push Up: - | GL | QD HM ~CV ~AB | bw
Over Bench Neutral Wrist Curl: Dumbbell | BI | ~FA
Over Bench One Arm Neutral Wrist Curl: Dumbbell | BI | ~FA
Over Bench One Arm Reverse Wrist Curl: Dumbbell | FA | BI
Over Bench One Arm Wrist Curl: Dumbbell | FA | BI SH
Over Bench Revers Wrist Curl: Dumbbell | FA | BI
Over Bench Wrist Curl: Dumbbell | FA | BI
Overhand Triceps Dip: Machine | TR | CH SH
Overhead Crunch (on Stability Ball): Weighted | AB | OB ~LB | bw
Overhead Curl on Exercise Ball: Cable | BI | SH ~FA
Overhead Slam: Medicine Ball | UB | SH ~AB ~FA
Overhead Triceps Extension (rope Attachment): Cable | TR | SH
Palm Rotational Bent Over Row: Dumbbell | UB | BI ~FA ~AB
Palm Rotational Row: Cable | UB | BI ~FA
Palms Down Wrist Curl Over a Bench: Barbell | FA | BI
Palms Up Wrist Curl Over a Bench: Barbell | FA | BI SH
Palms in Incline Bench Press: Dumbbell | TR | CH SH
Parallel Close Grip Pull Up: Assisted | LT | BI ~FA | bw
Peacher Hammer Curl: Dumbbell | BI | ~FA
Pelvic Tilt Into Bridge: - | GL | HM ~AB | bw
Pelvic Tilt: - | AB | ~LB | bw
Pike Push Up: Stability Ball | CH | SH TR ~AB | bw
Pike to Cobra Push Up: - | GL | SH TR ~AB | bw
Pin Presses: Barbell | TR | SH
Pirate Supper Legs: Kettlebell | SH | TR ~FA
Pistol Squat: Kettlebell | GL | QD HM ~CV ~AB
Plyo Push Up: - | CH | TR SH ~AB | bw
Plyo Push Up: Kettlebell | CH | TR SH ~AB
Plyo Squat: Dumbbell | GL | QD HM ~CV ~AB
Posterior Step to Overhead Reach: - | AB | GL HM QD SH | bw
Potty Squat with Support: - | GL | QD HM ~CV ~AB | bw
Potty Squat: - | AB | QD GL | bw
Power Point Plank: - | AB | SH TR GL | time
Preacher Curl Over Exercise Ball: Dumbbell | BI | ~FA
Press Sit Up: Barbell | AB | SH CH
Press on Exercise Ball: Cable | CH | SH TR
Press on Exercise Ball: Dumbbell | CH | SH TR
Pronate Grip Triceps Extension: Dumbbell | TR | ~FA
Prone Hamstring: Assisted | HM | GL LB | bw
Prone Incline Curl: Barbell | BI | ~FA
Prone Incline Curl: Dumbbell | BI | ~FA
Prone Incline Hammer Curl: Dumbbell | BI | ~FA
Prone Leg Raise: Stability Ball | LB | GL ~AB | bw
Prone Twist on Stability Ball: Stability Ball | AB | OB ~LB | bw
Pull Through (with Rope): Cable | GL | HM ~LB
Pull Through: Band | GL | HM ~LB
Pull in (on Stability Ball): Stability Ball | AB |  | bw
Pulldown (pro Lat Bar): Cable | LT | BI ~FA
Pulldown Bicep Curl: Cable | BI | ~FA
Pulldown: Cable | LT | BI ~FA
Pullover Hip Extension on Exercise Ball: Dumbbell | CH | TR SH ~AB
Pullover on Exercise Ball: Dumbbell | CH | TR LT
Pullover to Press: Barbell | LT | TR CH SH ~FA
Push Sit Up: Band | AB | SH CH
Push Up (on Stability Ball): Stability Ball | CH | TR SH ~AB | bw
Push Up (wall): - | CH | TR SH ~AB | bw
Push Up Close Grip Off Dumbbell: Dumbbell | TR | CH SH ~AB
Push Up Inside Leg Kick: - | GL | QD HM ~CV ~AB | bw
Push Up Medicine Ball: Medicine Ball | CH | TR SH ~AB
Push Up Plus: - | CH SE | TR SH ~AB | bw
Push Up on Bosu Ball: Bosu Ball | CH | TR SH ~AB | bw
Push Up on Lower Arms: - | TR | CH SH ~AB | bw
Push Up to Side Plank: - | AB | SH CH TR | time
Push Up: Suspension | CH | TR SH ~AB | bw
Push and Pull Bodyweight: - | CH | TR SH | bw
Push to Run: - | | | cardio
Pushdown (with Rope Attachment): Cable | TR | ~FA
Pushdown: Cable | TR | ~FA
Quads: - | QD | HM GL | bw
Quarter Sit Up: - | AB |  | bw
Raise Single Arm Push Up: - | CH | TR SH ~AB | bw
Raise: Dumbbell | SH | TP UB
Rear Decline Bridge: - | GL | HM ~LB | bw
Rear Delt Raise: Barbell | SH | TP UB
Rear Delt Raise: Dumbbell | SH | TP UB
Rear Delt Row (stirrups): Cable | SH | TP UB BI
Rear Delt Row (with Rope): Cable | SH | TP UB BI
Rear Delt Row: Smith Machine | SH | TP UB BI
Rear Delt Row_shoulder: Dumbbell | SH | TP UB
Rear Drive: Cable | TR | SH UB
Rear Fly: Dumbbell | SH | TP UB
Rear Lateral Raise (support Head): Dumbbell | SH | TP UB
Rear Lateral Raise: Dumbbell | SH | TP UB
Rear Lunge: Barbell | GL | QD HM ~CV ~AB
Rear Lunge: Dumbbell | GL | QD HM ~CV ~AB
Rear Pull Up: - | LT | BI ~FA | bw
Rear Pulldown: Cable | LT | BI UB SH ~FA
Reclining Big Toe Pose with Rope: Rope | HM | GL ~CV
Revers Grip Biceps Curl: Dumbbell | BI | ~FA
Reverse Bench Press: Dumbbell | CH | TR SH
Reverse Calf Raises: Smith Machine | CV | HM
Reverse Close Grip Bench Press: Barbell | TR | CH SH
Reverse Crunch: Cable | AB | 
Reverse Crunch: Suspension | AB |  | bw
Reverse Decline Close Grip Bench Press: Smith Machine | CH | TR SH
Reverse Dip: - | TR | CH SH | bw
Reverse Grip Bent Over Row: Barbell | UB | BI ~FA ~AB
Reverse Grip Bent Over Row: EZ Bar | UB | BI ~FA ~AB
Reverse Grip Bent Over Row: Smith Machine | UB | BI ~FA ~AB
Reverse Grip Curl: EZ Bar | BI | ~FA
Reverse Grip Decline Bench Press: Barbell | CH | TR SH
Reverse Grip Incline Bench One Arm Row: Dumbbell | UB | BI SH ~FA
Reverse Grip Incline Bench Press: Barbell | CH | TR SH
Reverse Grip Incline Bench Row: Barbell | UB | BI ~FA
Reverse Grip Incline Bench Two Arm Row: Dumbbell | UB | BI ~FA
Reverse Grip Lateral Pulldown: Machine | LT | BI UB SH ~FA
Reverse Grip Machine Lat Pulldown: Machine | LT | BI ~FA
Reverse Grip Preacher Curl: EZ Bar | BI | ~FA
Reverse Grip Preacher Curl: Machine | BI | ~FA
Reverse Grip Press: Smith Machine | CH | TR SH
Reverse Grip Pull Up: - | LT | BI ~FA | bw
Reverse Grip Pushdown: Cable | TR | ~FA
Reverse Grip Skullcrusher: Barbell | TR | ~FA
Reverse Grip Straight Back Seated High Row: Cable | UB | BI ~FA
Reverse Grip Triceps Pushdown (sz Bar) (with Arm Blaster): Cable | TR | ~FA
Reverse Grip Vertical Row: Machine | UB | BI ~FA
Reverse Hyper Extension (on Stability Ball): Stability Ball | GL | HM ~LB | bw
Reverse Hyper on Flat Bench: - | GL | HM ~LB | bw
Reverse Hyperextension: Machine | GL | HM ~LB
Reverse One Arm Curl: Cable | BI | ~FA
Reverse Plank with Leg Lift: - | AB | GL HM SH | time
Reverse Preacher Curl: Barbell | BI | ~FA
Reverse Preacher Curl: Cable | BI | ~FA
Reverse Preacher Curl: Dumbbell | BI | ~FA
Reverse Spider Curl: Dumbbell | BI | ~FA
Reverse T Bar Row: Machine | UB | BI ~FA
Reverse Wrist Curl: Band | FA | 
Rocky Pull Up Pulldown: - | LT | BI ~FA | bw
Roller Body Saw: Roller | AB | SH TR | bw
Roller Reverse Crunch: Roller | AB |  | bw
Roller Seated Shoulder Flexor Depresor Retractor: Roller | CH | SH TR | bw
Roller Seated Single Leg Shoulder Flexor Depresor Retractor: Roller | CH | SH TR | bw
Rollerout From Bench: Barbell | AB | SH TR
Rollerout: Barbell | AB | ~LB
Rope Climb: Rope | UB | BI SH ~FA
Rope Crossover Seated Row: Cable | UB | BI ~FA
Rope Elevated Seated Row: Cable | UB | BI ~FA
Rope Extension Incline Bench Row: Cable | UB | BI SH ~FA
Rope Hammer Preacher Curl: Cable | BI | ~FA
Rope High Pulley Overhead Tricep Extension: Cable | TR | SH
Rope Incline Tricep Extension: Cable | TR | SH
Rope Lying on Floor Tricep Extension: Cable | TR | SH
Rope One Arm Hammer Preacher Curl: Cable | BI | ~FA
Rope Seated Row: Cable | UB | BI ~FA
Rotary Calf: Machine | CV | 
Rotation Reverse Fly: Dumbbell | SH | TP UB
Round Arm: Weighted | SH | TR UB | bw
Row: Suspension | UB | BI SH ~FA | bw
Run (equipment): - | | | cardio
Run: - | | | cardio
Russian Twist (legs Up): Weighted | AB | OB | bw
Russian Twists (on Stability Ball): Cable | AB | OB ~LB
Scapula Dips: - | TP | UB SH | bw
Scapula Push Up: - | SE | CH SH ~AB | bw
Scott Press: Dumbbell | SH | TR CH
Seated Alternate Front Raise: Dumbbell | SH | TP BI
Seated Alternate Hammer Curl on Exercise Ball: Dumbbell | BI | ~FA
Seated Alternate Press: Dumbbell | SH | TR CH
Seated Alternate Shoulder: Dumbbell | SH | TR UB
Seated Behind Head Military Press: Barbell | SH | TR UB ~AB
Seated Bench Extension: Dumbbell | TR | SH
Seated Bent Arm Lateral Raise: Dumbbell | SH | TP UB
Seated Bent Over Alternate Kickback: Dumbbell | TR | SH UB
Seated Bent Over Triceps Extension: Dumbbell | TR | SH UB
Seated Bicep Curl (on Stability Ball): Weighted | BI | ~FA | bw
Seated Bicep Curl: Dumbbell | BI | ~FA
Seated Biceps Curl (on Stability Ball): Dumbbell | BI | ~FA
Seated Biceps Curl to Shoulder Press: Dumbbell | BI | SH ~FA
Seated Biceps Curl: Band | BI | ~FA
Seated Bradford Rocky Press: Barbell | SH | TR UB
Seated Calf Press: Machine | CV | HM
Seated Chest Press: Band | CH | SH TR
Seated Chest Press: Cable | CH | SH TR
Seated Close Grip Behind Neck Triceps Extension: Barbell | TR | SH
Seated Close Grip Concentration Curl: Barbell | BI | ~FA
Seated Close Grip Concentration Curl: EZ Bar | BI | ~FA
Seated Crunch (chest Pad): Machine | AB | OB
Seated Crunch: Cable | AB | OB
Seated Crunch: Machine | AB | OB
Seated Curl: Cable | BI | ~FA
Seated Curl: Dumbbell | BI | ~FA
Seated Curls: EZ Bar | BI | ~FA
Seated Fly: Machine | CH | SH TP
Seated Front Raise: Dumbbell | SH | TP BI
Seated Good Morning: Barbell | GL | HM ~LB ~AB
Seated Good Morning: Machine | GL | HM ~LB ~AB
Seated Hammer Curl: Dumbbell | BI | ~FA
Seated High Row (v Bar): Cable | LT | BI UB SH ~FA
Seated Hip Abduction: Band | GL | HM
Seated Hip Abduction: Machine | GL | HM
Seated Hip Adduction: Machine | AD | HM GL
Seated Hip Internal Rotation: Band | GL | HM QD
Seated Inner Biceps Curl: Dumbbell | BI | ~FA
Seated Kickback: Dumbbell | TR | SH
Seated Leg Raise Crunch: Machine | AB | 
Seated Leg Raise: - | AB | HF | bw
Seated Neutral Wrist Curl: Dumbbell | BI | ~FA
Seated One Arm Alternate Row: Cable | UB | BI ~FA
Seated One Arm Bicep Curl on Exercise Ball with Leg Raised: Dumbbell | BI | SH ~FA
Seated One Arm Concentration Curl: Cable | BI | ~FA
Seated One Arm Kickback: Dumbbell | TR | SH
Seated One Arm Rotate: Dumbbell | FA | SH BI
Seated One Leg Calf Raise Hammer Grip: Dumbbell | CV | HM GL
Seated One Leg Calf Raise Palm Up: Dumbbell | CV | HM GL
Seated One Leg Calf Raise: Dumbbell | CV | HM GL
Seated One Leg Calf Raise: Smith Machine | CV | HM
Seated Overhead Curl: Cable | BI | SH ~FA
Seated Overhead Triceps Extension: Barbell | TR | SH
Seated Preacher Curl: Dumbbell | BI | ~FA
Seated Press: Kettlebell | SH | TR ~AB
Seated Rear Lateral Raise: Cable | SH | TP UB
Seated Revers Grip Concentration Curl: Dumbbell | BI | ~FA
Seated Reverse Fly (parallel Grip): Machine | SH | TP UB
Seated Reverse Fly: Machine | SH | TP UB
Seated Reverse Grip One Arm Overhead Tricep Extension: Dumbbell | TR | SH
Seated Shoulder Internal Rotation: Cable | RC | SH TR
Seated Shoulder Press (parallel Grip): Dumbbell | SH | TR UB
Seated Shoulder Press: Band | SH | TR UB
Seated Shoulder Press: Dumbbell | SH | TR UB
Seated Shoulder Press: Smith Machine | SH | TR UB
Seated Side Crunch (wall): - | AB | OB | bw
Seated Squat Calf Raise on Leg Press Machine: Machine | CV | QD HM GL ~AB
Seated Straight Back Row: Band | UB | BI SH ~FA
Seated Triceps Extension: Dumbbell | TR | SH
Seated Triceps Extension: EZ Bar | TR | SH
Seated Twist (on Stability Ball): Weighted | AB | OB ~LB | bw
Seated Twist: Band | AB | OB
Seated Twist: Barbell | AB | OB ~LB
Seated Twist: Cable | AB | OB
Seated Two Arm Military Press: Kettlebell | SH | TR UB ~AB
Seated Wide Angle Pose Sequence: - | HM | QD ~CV | bw
Seated Wrist Curl: Smith Machine | FA | BI
Seesaw Press: Kettlebell | SH | TR ~AB
Self Assisted Inverse Leg Curl (on Floor): - | HM | GL ~CV | bw
Self Assisted Inverse Leg Curl: - | HM | GL ~CV | bw
Short Stride Run: - | | | cardio
Shoulder Grip Pull Up: - | LT | BI ~FA | bw
Shoulder Press: Band | SH | TR UB
Shoulder Press: Smith Machine | SH | TR UB
Shoulder Tap Push Up: - | CH | TR ~AB | bw
Shoulder Tap: - | AB | SH TR | bw
Shrug: Band | TP | SH
Side Bend (on Stability Ball): Weighted | AB | OB ~LB | bw
Side Bend Crunch (bosu Ball): Cable | AB | OB
Side Bridge Hip Abduction: - | GL | ~OB | bw
Side Crunch: Cable | AB | OB
Side Hip (on Parallel Bars): - | AB | OB | bw
Side Hip Abduction: - | GL | QD | bw
Side Lying Biceps Curl: - | BI | ~FA | bw
Side Lying One Hand Raise: Dumbbell | SH | TP UB
Side Plank Hip Adduction: - | AD | GL ~OB | time
Side Plank with Rear Fly: Dumbbell | UB | SH ~AB ~FA | time
Side Push Up: - | TR | SH CH ~AB | bw
Side Split Squat: Barbell | QD | GL HM ~CV ~AB
Side Triceps Extension: Band | TR | SH
Side to Side Chin: - | LT | BI ~FA | bw
Single Arm Overhead Carry: Dumbbell | SH | TP ~AB
Single Arm Push Up: - | CH | TR SH ~AB | bw
Single Leg Bridge with Outstretched Leg: - | GL | HM QD | bw
Single Leg Calf Raise (on a Dumbbell): Dumbbell | CV | 
Single Leg Calf Raise: Band | CV | 
Single Leg Calf Raise: Dumbbell | CV | 
Single Leg Deadlift with Stepbox Support: Dumbbell | GL | HM ~LB ~AB
Single Leg Deadlift: Barbell | GL | HM ~LB ~AB
Single Leg Deadlift: Dumbbell | GL | HM ~LB ~AB
Single Leg Platform Slide: - | HM | GL QD | bw
Single Leg Reverse Calf Raise: Band | CV | HM GL
Single Leg Split Squat: Band | QD | GL HM ~AB
Single Leg Split Squat: Barbell | QD | GL HM ~CV ~AB
Single Leg Split Squat: Dumbbell | QD | GL HM ~CV ~AB
Single Leg Split Squat: Smith Machine | QD | GL HM ~AB
Single Leg Squat (pistol) Male: - | GL | QD HM ~CV ~AB | bw
Single Leg Squat: Dumbbell | GL | QD HM ~CV ~AB
Sit Up with Arms on Chest: - | AB |  | bw
Sit Up: Assisted | AB |  | bw
Sitted Alternate Leg Raise: Barbell | AB | 
Skater Hops: - | | | cardio
Ski Ergometer: - | TR | SH ~FA | bw
Ski Step: - | | | cardio
Skier: Barbell | SH | TR ~AB
Skin the Cat: - | UB | SH ~AB ~FA | bw
Sledge Hammer: Hammer | AB | SH ~FA
Speed Squat: Barbell | GL | QD HM ~CV ~AB
Spell Caster: Dumbbell | AB | OB ~LB
Sphinx: - | LB | GL HM | bw
Spider Crawl Push Up: - | GL | CH SH ~AB | bw
Spine Twist: - | AB | OB ~LB | bw
Split Squat: Suspension | QD | GL HM ~CV ~AB | bw
Sprint Lunge: Smith Machine | GL | QD HM ~CV ~AB
Squat (on Knees): Barbell | QD | GL HM ~CV ~AB
Squat Jerk: Barbell | QD | GL HM ~CV ~AB
Squat Jump Step Rear Lunge: Barbell | QD | GL HM ~CV ~AB
Squat Row (with Rope Attachment): Cable | LT | BI UB SH ~FA ~AB
Squat on Bosu Ball: Bosu Ball | QD | GL HM ~CV ~AB | bw
Squat to Overhead Reach with Twist: - | QD | GL HM ~AB | bw
Squat to Overhead Reach: - | QD | GL HM ~AB | bw
Squat: Weighted | GL | QD HM ~CV ~AB | bw
Squatting Curl: Cable | BI | ~FA ~AB
Squatting Row: - | UB | BI SH ~FA ~AB | bw
Stalder Press: - | TR | SH ~AB | bw
Standing Ab Rollerout: Barbell | AB | SH ~LB
Standing Alternate Hammer Curl and Press: Dumbbell | BI | SH ~FA
Standing Alternate Overhead Press: Dumbbell | SH | TR ~AB
Standing Alternate Raise: Dumbbell | SH | TP ~FA
Standing Alternating Tricep Kickback: Dumbbell | TR | SH
Standing Archer: - | UB | SH BI ~FA | bw
Standing Around World: Dumbbell | SH | TR ~FA
Standing Back Wrist Curl: Barbell | FA | BI SH
Standing Back Wrist Curl: Cable | FA | BI
Standing Back Wrist Curl: Smith Machine | FA | BI TR
Standing Behind Head Military Press: Smith Machine | SH | TR UB ~AB
Standing Behind Neck Press: Barbell | SH | TR UB
Standing Bent Over One Arm Triceps Extension: Dumbbell | TR | SH UB
Standing Bent Over Two Arm Triceps Extension: Dumbbell | TR | SH UB
Standing Biceps Curl: Dumbbell | BI | ~FA
Standing Bradford Press: Barbell | SH | TR UB
Standing Calf Raise (on a Staircase): - | CV |  | bw
Standing Calf Raise: Cable | CV | HM GL
Standing Calves: - | CV | HM GL | bw
Standing Chest Press: Machine | CH | SH TR
Standing Chin Up: Assisted | LT | BI ~FA | bw
Standing Close Grip Curl: Barbell | BI | ~FA
Standing Close Grip Military Press: Barbell | SH | TR UB ~AB
Standing Close Grip One Arm Row: - | UB | BI ~FA | bw
Standing Close Grip Row: - | UB | BI ~FA | bw
Standing Concentration Curl: Barbell | BI | ~FA
Standing Concentration Curl: Dumbbell | BI | ~FA
Standing Cross Over High Reverse Fly: Cable | SH | TP UB
Standing Crunch (with Rope Attachment): Cable | AB | OB
Standing Crunch: Band | AB | OB
Standing Crunch: Cable | AB | OB
Standing Curl: Weighted | BI | ~FA | bw
Standing Fly: Cable | CH | SH TR
Standing French Press: EZ Bar | TR | SH
Standing Front Raise Above Head: Dumbbell | SH | TP BI
Standing Front Raise Over Head: Barbell | SH | TR UB ~TP
Standing Hand Squeeze: Weighted | FA | BI TR | bw
Standing Hip Extension: Cable | GL | HM QD
Standing Inner Curl: Cable | BI | ~FA
Standing Kickback: Dumbbell | TR | SH
"""

private const val MORE_2 = """
Standing Leg Calf Raise: Barbell | CV | HM GL
Standing Leg Calf Raise: Smith Machine | CV | HM GL
Standing Lift: Cable | AB | OB ~LB
Standing Military Press: Smith Machine | SH | TR UB ~AB
Standing One Arm Concentration Curl: Dumbbell | BI | ~FA
Standing One Arm Curl (over Incline Bench): Dumbbell | BI | ~FA
Standing One Arm Extension: Dumbbell | TR | SH
Standing One Arm Palm in Press: Dumbbell | SH | TR ~AB
Standing One Arm Reverse Curl: Dumbbell | BI | ~FA
Standing One Arm Row: - | UB | BI ~FA | bw
Standing One Arm Triceps Extension: Cable | TR | SH
Standing One Leg Calf Raise: Cable | CV | 
Standing Overhead Press: Dumbbell | SH | TR UB ~AB
Standing Overhead Triceps Extension: Barbell | TR | SH
Standing Palms in Press: Dumbbell | SH | TR CH
Standing Pelvic Tilt: - | LB | ~AB | bw
Standing Preacher Curl: Dumbbell | BI | ~FA
Standing Pull Up: Assisted | LT | BI ~FA | bw
Standing Pulldown (with Rope): Cable | BI | SH ~FA
Standing Rear Delt Row (with Rope): Cable | SH | TP UB BI
Standing Rear Delt Row: Band | SH | TP UB BI
Standing Reverse Curl: Dumbbell | BI | ~FA
Standing Reverse Grip Curl: Barbell | BI | ~FA
Standing Reverse Grip One Arm Overhead Tricep Extension: Cable | TR | SH
Standing Rocking Leg Calf Raise: Barbell | CV | HM QD
Standing Row (v Bar): Cable | UB | BI SH ~FA
Standing Row: - | UB | BI SH ~FA | bw
Standing Shoulder External Rotation: Cable | RC | SH TP
Standing Single Leg Curl: - | HM | GL | bw
Standing Triceps Extension: Dumbbell | TR | SH
Standing Twist Row (v Bar): Cable | UB | BI SH ~FA
Standing Twist: Barbell | AB | OB ~LB
Standing Twisting Crunch: Band | AB | OB
Standing Up Straight Crossovers: Cable | CH | SH TR
Standing Wheel Rollerout: Wheel | AB | SH ~LB | bw
Standing Wide Grip Biceps Curl: Barbell | BI | ~FA
Standing Wide Grip Biceps Curl: EZ Bar | BI | ~FA
Standing Wide Grip Curl: Barbell | BI | SH ~FA
Standing Wide Military Press: Barbell | SH | TR UB ~AB
Standing Zottman Preacher Curl: Dumbbell | BI | ~FA
Stationary Bike Walk: - | | | cardio
Step Up Lunge: Dumbbell | QD | GL HM ~CV ~AB
Step Up Single Leg Balance with Bicep Curl: Dumbbell | BI | QD GL HM ~FA ~AB
Step Up Split Squat: Dumbbell | QD | GL HM ~CV ~AB
Step Up: Band | GL | QD HM ~CV ~AB
Stiff Leg Deadlift: Band | GL | HM ~LB ~AB
Stiff Leg Good Morning: Barbell | GL | HM ~LB ~AB
Straddle Maltese: - | AB | SH CH | bw
Straddle Planche: - | AB | SH TR CH | bw
Straight Arm Pulldown (with Rope): Cable | LT | BI ~FA
Straight Arm Pulldown: Cable | LT | SH BI ~FA
Straight Arm Pullover: Dumbbell | CH | LT TR
Straight Back Seated Row: Cable | UB | BI SH ~FA
Straight Back Stiff Leg Deadlift: Band | GL | HM ~LB ~AB
Straight Bar Dip: Weighted | CH | TR SH | bw
Straight Leg Deadlift: Barbell | HM | GL LB ~AB
Straight Leg Deadlift: Dumbbell | GL | HM ~LB ~AB
Straight Leg Outer Hip Abductor: - | GL | HM | bw
Sumo High Pull: Kettlebell | TP | SH GL HM
Sumo Pull Through: Dumbbell | GL | HM QD ~AB
Sumo Squat: Smith Machine | GL | QD HM ~CV ~AB
Superman Push Up: - | CH | SH ~AB | bw
Supine Chest Throw: Medicine Ball | TR | CH SH
Supine Reverse Fly: Cable | SH | TP UB
Supine Triceps Extension: Stability Ball | TR | SH | bw
Supported Squat: Dumbbell | QD | GL HM ~CV ~AB
Svend Press: Weighted | CH | SH TR | bw
Swing 360: - | | | cardio
T Bar Reverse Grip Row: Machine | UB | BI ~FA
Thibaudeau Kayak Row: Cable | LT | BI UB SH ~FA
Three Bench Dip: - | TR | CH SH | bw
Three Bench Dips: Weighted | TR | CH SH | bw
Tire Flip: Tire | GL | HM QD ~AB
Toe Raise: Smith Machine | CV | 
Tricep Kickback with Stork Stance: Dumbbell | TR | SH
Triceps Dip (bench Leg): - | TR | CH SH | bw
Triceps Dip (between Benches): - | TR | CH SH | bw
Triceps Dip (kneeling): Assisted | TR | CH SH | bw
Triceps Dip on High Parallel Bars: Weighted | TR | CH SH | bw
Triceps Press: - | TR | SH | bw
Triceps Pushdown (v Bar) (with Arm Blaster): Cable | TR | ~FA
Tuck Crunch: - | AB |  | bw
Tuck Reverse Crunch: Cable | AB | 
Turkish Get Up (squat Style): Kettlebell | GL | QD HM ~AB
Twin Handle Parallel Grip Lat Pulldown: Cable | LT | BI UB SH ~FA
Twist (up Down): Cable | AB | OB ~LB
Twist Hip Lift: - | GL | HM ~OB | bw
Twisted Leg Raise: - | AB | OB | bw
Twisting Bench Press: Dumbbell | TR | CH SH
Twisting Overhead Press: Band | SH | TR UB ~AB
Twisting Pull: Cable | LT | SH BI ~FA
Two Arm Clean: Kettlebell | SH | TP ~FA ~AB
Two Arm Curl on Incline Bench: Cable | BI | ~FA
Two Arm Military Press: Kettlebell | SH | TR UB ~AB
Two Arm Row: Kettlebell | UB | BI ~FA
Two Arm Tricep Kickback: Cable | TR | SH
Underhand Pulldown: Band | LT | BI ~FA
Underhand Pulldown: Cable | LT | BI ~FA
Unilateral Row: Machine | UB | BI ~FA
Upper Chest Crossovers: Cable | CH | SH TR
Upper Row: Cable | UB | BI SH ~FA
Upright Row (back Pov): Dumbbell | SH | TP BI
Upright Row: Smith Machine | SH | TP BI
Upright Shoulder External Rotation: Dumbbell | RC | SH TP
Upward Facing Dog: - | LB | SH CH | bw
V Sit on Floor: - | AB |  | bw
V Up: Band | AB | 
Vertical Leg Raise (on Parallel Bars): - | AB | HF | bw
Vertical Pallof Press: Band | AB | OB GL
W Press: Dumbbell | SH | TR CH
Waiter Biceps Curl: Dumbbell | BI | SH ~FA
Walk Elliptical Cross Trainer: - | | | cardio
Walking High Knees Lunge: - | | | cardio
Walking on Incline Treadmill: - | | | cardio
Walking on Stepmill: - | | | cardio
Wheel Rollerout: Wheel | AB | ~LB | bw
Wheel Run: - | | | cardio
Wide Bench Press: Barbell | CH | SH TR
Wide Grip Bench Press: Smith Machine | CH | TR SH
Wide Grip Chest Dip (kneeling): Assisted | CH | TR SH | bw
Wide Grip Chest Dip on High Parallel Bars: - | CH | TR SH | bw
Wide Grip Decline Bench Press: Smith Machine | CH | TR SH
Wide Grip Pull Up: - | LT | BI ~FA | bw
Wide Grip Rear Pull Up: - | LT | BI ~FA | bw
Wide Grip Rear Pulldown Behind Neck: Cable | LT | BI UB SH ~FA
Wide Grip Upright Row: Barbell | SH | TP BI
Wide Hand Push Up: - | CH | TR SH ~AB | bw
Wide Reverse Grip Bench Press: Barbell | CH | TR SH
Wide Squat: Barbell | QD | GL HM ~CV ~AB
Wind Sprints: - | AB | QD HM ~CV | bw
Windmill: Kettlebell | AB | OB SH
Wrist Circles: - | FA |  | bw
Wrist Curl: Band | FA | BI TR
Wrist Rollerer: Weighted | FA | BI TR | bw
Y Raise: Band | SH | TP UB
Zottman Preacher Curl: Dumbbell | BI | ~FA
"""

internal val MORE_TABLE: String get() = MORE_0 + MORE_1 + MORE_2
