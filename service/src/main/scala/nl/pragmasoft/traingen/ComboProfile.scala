package nl.pragmasoft.traingen

import nl.pragmasoft.traingen.SectionType.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

case class ComboProfile(
    buildUpLength: Int,
    movementsMin: Int,
    movementsMax: Int,
    transitionDuration: (BodyPart, BodyPart) => FiniteDuration,
    doublesMax: Int,
    oneSideMovementsMax: Int
)

object ComboProfile:
  val Default: ComboProfile =
    ComboProfile(2, 6, 8, BodyPart.DefaultTransitionDuration, 1, 2)
