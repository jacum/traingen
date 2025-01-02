package nl.pragmasoft.traingen

enum BodyParts:
  case LeftArm, RightArm, LeftLeg, RightLeg, Abs, Core

object BodyParts:
  val Arms: Set[BodyParts] = Set(LeftArm, RightArm)
  val Legs: Set[BodyParts] = Set(LeftLeg, RightLeg)
  val Left: Set[BodyParts] = Set(LeftLeg, LeftArm)
  val Right: Set[BodyParts] = Set(RightLeg, RightArm)
