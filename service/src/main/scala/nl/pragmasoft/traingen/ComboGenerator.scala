package nl.pragmasoft.traingen

import cats.Applicative
import cats.syntax.applicative.*
import nl.pragmasoft.traingen.http.definitions.{Combo, ComboMovement, ComboMovementChance}
import nl.pragmasoft.traingen.http.{Handler, Resource}

import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.Random
import RandomUtils.*
import cats.implicits.catsSyntaxEq

object ComboGenerator:
  val StartExercise = "<start>"

abstract class ComboGenerator[F[_]: Applicative] extends Handler[F]:
  import ComboGenerator.*

  def getCombo(respond: Resource.GetComboResponse.type)(): F[Resource.GetComboResponse] =
    respond
      .Ok(generateCombo(ComboProfile.Default))
      .pure[F]

  def allMovements: Vector[ComboMovement]

  lazy val comboMovementsMap: Map[String, ComboMovement] = allMovements.map(e => e.id -> e).toMap

  def generateCombo(profile: ComboProfile): Combo =

    val movementsAfter: Map[String, Vector[ComboMovementChance]] =
      allMovements
        .flatMap(e => e.after.map(s => s.id -> ComboMovementChance(e.id, s.chance)))
        .groupMap(_._1)(_._2)

    val allOpeningMovements: Vector[ComboMovementChance] =
      allMovements.flatMap { m =>
        m.after.find(_.id === StartExercise).map(mc => ComboMovementChance(m.id, mc.chance))
      }

    // crude integrity check
    allMovements.map(_.id).foreach(k => assert(movementsAfter.contains(k), s"$k has no 'after' movements"))

    val comboLength = between(profile.movementsMin, profile.movementsMax)

    @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
    def calculateDuration[A](s: Vector[A], duration: (A, A) => FiniteDuration): FiniteDuration =
      s.sliding(2).foldLeft(0.seconds) { (acc, pair) =>
        acc + duration(pair(0), pair(1))
      }

    def lastN[A](vector: Vector[A], n: Int, predicate: A => Boolean): Boolean =
      if n > vector.length || n <= 0 then false
      else vector.takeRight(n).forall(predicate)

    def toChanceVector: Vector[ComboMovementChance] => Vector[(ComboMovement, Double)] =
      _.map(mc => (comboMovementsMap(mc.id), mc.chance.toOption.getOrElse(1.0)))
        .filter(_._2 > 0)

    @tailrec
    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def pickMovements(acc: Vector[ComboMovement]): Vector[ComboMovement] =

      def tooManyDoubles: Boolean =
        acc.groupBy(_.id).exists(_._2.sizeIs >= profile.doublesMax)

      def doubles(movement: ComboMovement): Boolean =
        !tooManyDoubles && chance(
          movement.after
            .find(_.id === movement.id)
            .map(_.chance.toOption.getOrElse(0.0))
            .getOrElse(0.0)
        )

      if acc.sizeIs == comboLength then acc
      else
        val last = acc.last
        pickMovements(
          acc :+ (if doubles(last) then last
                  else
                    pickOneWithChance(
                      toChanceVector(
                        movementsAfter(last.id)
                          .filterNot(mc =>
                            val m = comboMovementsMap(mc.id)
                            last.excludes.toOption.exists(_.contains(m.id)) ||
                            (BodyPart.left(m.bodyPart) && lastN[ComboMovement](
                              acc,
                              profile.oneSideMovementsMax,
                              m => BodyPart.left(m.bodyPart)
                            )) ||
                            (BodyPart.right(m.bodyPart) && lastN[ComboMovement](
                              acc,
                              profile.oneSideMovementsMax,
                              m => BodyPart.right(m.bodyPart)
                            ))
                          )
                      )
                    )
          )
        )

    val allCombo = pickMovements(Vector(pickOneWithChance(toChanceVector(allOpeningMovements))))

    Combo(
      calculateDuration[ComboMovement](allCombo, (m1, m2) => profile.transitionDuration(m1.bodyPart, m2.bodyPart)),
      allCombo.map(_.id)
    )
