package nl.pragmasoft.traingen

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import SectionType.*

case class TrainingProfile(
    trainingDuration: FiniteDuration,
    exerciseDuration: FiniteDuration,
    warmupDuration: FiniteDuration,
    comboTimeFraction: Double,
    comboReps: Int,
    comboProfile: ComboProfile,
    calisthenicExercises: Int,
    closeExercises: Int,
    focusedCalisthenics: Boolean,
    probabilityComposite: Double
)

object TrainingProfile:
  val Default: TrainingProfile =
    TrainingProfile(45 minutes, 1 minute, 15 minutes, 0.5, 20, ComboProfile.Default, 5, 1, false, 0.5)
