package nl.pragmasoft.traingen

import cats.Eq

import scala.concurrent.duration.*
import cats.syntax.eq.*

enum BodyPart:
  case LeftArm, RightArm, LeftLeg, RightLeg, Abs, Core

object BodyPart:

  given Eq[BodyPart] = Eq.fromUniversalEquals[BodyPart]

  val Arms: Set[BodyPart] = Set(LeftArm, RightArm)
  val Legs: Set[BodyPart] = Set(LeftLeg, RightLeg)
  val Left: Set[BodyPart] = Set(LeftLeg, LeftArm)
  val Right: Set[BodyPart] = Set(RightLeg, RightArm)

  def left(p: BodyPart): Boolean = Left.contains(p)
  def right(p: BodyPart): Boolean = Right.contains(p)
  def arms(p: BodyPart): Boolean = Arms.contains(p)
  def legs(p: BodyPart): Boolean = Legs.contains(p)

  def sameSide(p1: BodyPart, p2: BodyPart): Boolean =
    (Left.contains(p1) && Left.contains(p2)) || (Right.contains(p1) && Right.contains(p2))

  val DefaultTransitionDuration: (BodyPart, BodyPart) => FiniteDuration =
    (from: BodyPart, to: BodyPart) =>
      (from, to) match
        case (from, to) if from === to && Arms.contains(from)            => 300 millis
        case (from, to) if from === to && Legs.contains(from)            => 700 millis
        case (from, to) if arms(from) && arms(to) && sameSide(from, to)  => 700 millis
        case (from, to) if arms(from) && arms(to) && !sameSide(from, to) => 500 millis
        case _                                                           => 800 millis
