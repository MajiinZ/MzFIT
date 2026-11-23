package org.mz.mzfit.data

/** Training categories used to browse workouts. */
enum class BodyPart(val displayName: String, val tagline: String) {
    CHEST("Chest", "Pecs, upper & lower"),
    BACK("Back", "Lats, traps & spinal erectors"),
    SHOULDERS("Shoulders", "Front, side & rear delts"),
    ARMS("Arms", "Biceps, triceps & forearms"),
    CORE("Core", "Abs & obliques"),
    LEGS("Legs", "Quads, hamstrings & calves"),
    GLUTES("Glutes", "Glutes & hips"),
    FULL_BODY("Full Body", "Compound & conditioning");

    val muscles: List<Muscle> get() = Muscle.entries.filter { it.bodyPart == this }
}

/** Which side of the body figure a muscle is drawn on. */
enum class BodySide { FRONT, BACK, BOTH }

/** Detailed muscle groups rendered on the anatomical muscle map. */
enum class Muscle(
    val displayName: String,
    val anatomicalName: String,
    val bodyPart: BodyPart,
    val side: BodySide,
    val description: String,
) {
    NECK("Neck", "Sternocleidomastoid", BodyPart.SHOULDERS, BodySide.FRONT,
        "Rotates and flexes the head. Trained indirectly by most heavy compound lifts."),
    TRAPS("Traps", "Trapezius", BodyPart.BACK, BodySide.BOTH,
        "Elevates, retracts and rotates the shoulder blades. Key for posture and heavy pulls."),
    FRONT_DELTS("Front Delts", "Anterior deltoid", BodyPart.SHOULDERS, BodySide.FRONT,
        "Raises the arm forward. Heavily involved in pressing movements."),
    SIDE_DELTS("Side Delts", "Lateral deltoid", BodyPart.SHOULDERS, BodySide.FRONT,
        "Raises the arm out to the side and builds shoulder width."),
    REAR_DELTS("Rear Delts", "Posterior deltoid", BodyPart.SHOULDERS, BodySide.BACK,
        "Pulls the arm back. Balances pressing work and protects the shoulder joint."),
    UPPER_CHEST("Upper Chest", "Pectoralis major (clavicular head)", BodyPart.CHEST, BodySide.FRONT,
        "Upper pec fibers that lift the arm forward and up. Emphasized by incline work."),
    LOWER_CHEST("Lower Chest", "Pectoralis major (sternal head)", BodyPart.CHEST, BodySide.FRONT,
        "The bulk of the chest. Drives horizontal pressing and arm adduction."),
    BICEPS("Biceps", "Biceps brachii", BodyPart.ARMS, BodySide.FRONT,
        "Flexes the elbow and supinates the forearm."),
    TRICEPS("Triceps", "Triceps brachii", BodyPart.ARMS, BodySide.BACK,
        "Extends the elbow. Makes up about two-thirds of upper-arm mass."),
    FOREARMS("Forearms", "Brachioradialis & wrist flexors", BodyPart.ARMS, BodySide.BOTH,
        "Grip strength and wrist control. Trained by every pull and carry."),
    LATS("Lats", "Latissimus dorsi", BodyPart.BACK, BodySide.BACK,
        "The widest back muscle. Pulls the arms down and back."),
    MID_BACK("Mid Back", "Rhomboids & teres major", BodyPart.BACK, BodySide.BACK,
        "Retracts the shoulder blades. Builds back thickness and posture."),
    LOWER_BACK("Lower Back", "Erector spinae", BodyPart.BACK, BodySide.BACK,
        "Keeps the spine extended and stable under load."),
    ABS("Abs", "Rectus abdominis", BodyPart.CORE, BodySide.FRONT,
        "Flexes the spine and braces the trunk."),
    OBLIQUES("Obliques", "External & internal obliques", BodyPart.CORE, BodySide.FRONT,
        "Rotates and side-bends the trunk; resists twisting."),
    GLUTES("Glutes", "Gluteus maximus & medius", BodyPart.GLUTES, BodySide.BACK,
        "Extends and stabilizes the hip. The most powerful muscle group in the body."),
    ADDUCTORS("Adductors", "Adductor magnus, longus & brevis", BodyPart.LEGS, BodySide.FRONT,
        "Pulls the legs together and assists hip extension in deep squats."),
    QUADS("Quads", "Quadriceps femoris", BodyPart.LEGS, BodySide.FRONT,
        "Extends the knee. Primary driver of squats, lunges and jumps."),
    HAMSTRINGS("Hamstrings", "Biceps femoris & semitendinosus", BodyPart.LEGS, BodySide.BACK,
        "Flexes the knee and extends the hip. Key for hinges and sprinting."),
    CALVES("Calves", "Gastrocnemius & soleus", BodyPart.LEGS, BodySide.BOTH,
        "Points the foot. Essential for running, jumping and ankle stability."),
    TIBIALIS("Tibialis", "Tibialis anterior", BodyPart.LEGS, BodySide.FRONT,
        "Lifts the toes. Protects the knees and shins.");
}
