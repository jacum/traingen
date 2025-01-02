package nl.pragmasoft.traingen

import nl.pragmasoft.traingen.SectionType.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

case class ComboProfile(
    buildUpLength: Int,
    movementsMin: Int,
    movementsMax: Int,
    armMovementDuration: FiniteDuration,
    legMovementDuration: FiniteDuration,
    chanceOfDouble: Double
)

object ComboProfile:
  val Default: ComboProfile =
    ComboProfile(2, 6, 8, 500 millis, 800 millis, 0.1)
