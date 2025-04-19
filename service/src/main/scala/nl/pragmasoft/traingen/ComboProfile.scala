package nl.pragmasoft.traingen

import nl.pragmasoft.traingen.SectionType.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

case class ComboProfile(
    buildUpLength: Int,
    movements: Int,
    transitionDuration: (BodyPart, BodyPart) => FiniteDuration,
    doublesMax: Int,
    oneSideMovementsMax: Int
)

object ComboProfile:
  val Default: ComboProfile =
    ComboProfile(3, 6, BodyPart.DefaultTransitionDuration, 1, 2)
