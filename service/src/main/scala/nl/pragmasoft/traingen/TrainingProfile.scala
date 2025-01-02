package nl.pragmasoft.traingen

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import SectionType.*

case class TrainingProfile(
    trainingDuration: FiniteDuration,
    exerciseDuration: FiniteDuration,
    warmupDuration: FiniteDuration,
    comboBuildUpLength: Int,
    comboMovementsMin: Int,
    comboMovementsMax: Int,
    comboReps: Int,
    comboArmMovementDuration: FiniteDuration,
    comboLegMovementDuration: FiniteDuration,
    calistenicsExercises: Int,
    closeExercises: Int,
    focusedCalisthenics: Boolean,
    probabilityComposite: Double
)

object TrainingProfile:
  val Default: TrainingProfile =
    TrainingProfile(45 minutes, 1 minute, 15 minutes, 2, 6, 8, 20, 1 seconds, 2 seconds, 5, 1, false, 0.5)
