package nl.pragmasoft.traingen

import cats.Applicative
import cats.syntax.applicative.*
import nl.pragmasoft.traingen.http.definitions.{Combo, ComboMovement}
import nl.pragmasoft.traingen.http.{Handler, Resource}

import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.Random

import RandomUtils.*

class ComboGenerator[F[_]: Applicative] extends Handler[F]:

  def getCombo(respond: Resource.GetComboResponse.type)(): F[Resource.GetComboResponse] =
    respond
      .Ok(generateCombo(ComboProfile.Default))
      .pure[F]

  def generateCombo(profile: ComboProfile): Combo =

    val allMovements: Vector[ComboMovement] = LibraryLoader.load("combo-movements.json")
    val comboMovementsMap: Map[String, ComboMovement] = allMovements.map(e => e.id -> e).toMap

    val movementsAfter: Map[String, Vector[ComboMovement]] =
      allMovements
        .flatMap(e => e.after.toOption.getOrElse(Vector.empty).map(s => s -> comboMovementsMap(e.id)))
        .groupMap(_._1)(_._2)

    val allOpeningMovements: Vector[(ComboMovement, Double)] =
      allMovements.collect {
        case m if m.order.toOption.contains(1) => (m, m.chance.toOption.getOrElse(0))
      }

    // crude integrity check
    allMovements.map(_.id).foreach(k => assert(movementsAfter.contains(k), s"$k has no 'after' movements"))

    val comboLength = between(profile.movementsMin, profile.movementsMax)

    def calculateMovementDuration(ms: Vector[ComboMovement]): FiniteDuration =
      ms.map(m =>
        m.bodyParts match
          case p if p.exists(BodyParts.Arms.contains) => profile.armMovementDuration
          case p if p.exists(BodyParts.Legs.contains) => profile.legMovementDuration
          case _ => 0 seconds // catch all, shouldn't happen because all combos are either legs or arms
      ).foldLeft(0 seconds)(_ + _)

    def lastTwo[A](vector: Vector[A], predicate: A => Boolean): Boolean =
      vector match
        case init :+ last1 :+ last2 => predicate(last1) && predicate(last2)
        case _                      => false

    @tailrec
    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def pickMovements(acc: Vector[ComboMovement]): Vector[ComboMovement] =
      if acc.sizeIs == comboLength then acc
      else
        val last = acc.last
        pickMovements(
          acc :+ (if last.doubles.toOption.contains(true) &&
                    acc.groupBy(identity).filter(_._2.sizeIs > 1).keys.isEmpty &&
                    chance(profile.chanceOfDouble)
                  then last
                  else
                    pickOne(
                      movementsAfter(last.id)
                        .filterNot(m => last.excludes.toOption.exists(_.contains(m.id)))
                        .filterNot(m =>
                          m.bodyParts.exists(BodyParts.Left.contains) && lastTwo[ComboMovement](
                            acc,
                            _.bodyParts.exists(BodyParts.Left.contains)
                          ) ||
                            m.bodyParts.exists(BodyParts.Right.contains) && lastTwo[ComboMovement](
                              acc,
                              _.bodyParts.exists(BodyParts.Right.contains)
                            )
                        )
                    )
          )
        )

    val allCombo = pickMovements(Vector(pickOneWithChance(allOpeningMovements)))

    Combo(calculateMovementDuration(allCombo), allCombo.map(_.id))
