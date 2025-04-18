package nl.pragmasoft.traingen

import cats.Applicative
import cats.implicits.catsSyntaxEq
import cats.syntax.applicative.*
import nl.pragmasoft.traingen.RandomUtils.*
import nl.pragmasoft.traingen.SectionType.*
import nl.pragmasoft.traingen.http.definitions.*
import nl.pragmasoft.traingen.http.support.Presence.*
import nl.pragmasoft.traingen.http.{Handler, Resource}
import cats.syntax.eq.*
import cats.Eq
import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.Random

object Generator:
  val StartExercise = "_start_"
  implicit val sectionTypeEq: Eq[SectionType] = Eq.fromUniversalEquals


abstract class Generator[F[_]: Applicative] extends Handler[F]:
  import Generator.*

  def availableElements: Library

  def getLibrary(respond: Resource.GetLibraryResponse.type)(): F[Resource.GetLibraryResponse] =
    respond.Ok(availableElements).pure[F]

  def getTraining(respond: Resource.GetTrainingResponse.type)(): F[Resource.GetTrainingResponse] =
    respond.Ok(generateTraining(TrainingProfile.Default)).pure[F]

  def generateTraining(profile: TrainingProfile): Training =
    val elements: Map[SectionType, Vector[Element]] =
      availableElements.elements.flatMap(e => e.sections.map(s => s -> e)).groupMap(_._1)(_._2)

    val remainingDuration =
      profile.trainingDuration - profile.warmupDuration - profile.closeExercises * profile.exerciseDuration

    val calisthenicsDuration = (profile.exerciseDuration * profile.calisthenicExercises)

    val comboDuration =
      val trialCombos = Vector.fill(100)(generateCombo(profile.comboProfile)).map(_.duration)
      val avgDuration = trialCombos.fold(0 seconds)(_ + _) / trialCombos.size
      avgDuration

    val sections: Vector[SectionType] = Vector(SectionType.Warmup, SectionType.Filler) ++
      Vector
        .fill((remainingDuration / (comboDuration + calisthenicsDuration)).toInt)(
          Vector(SectionType.Combo, SectionType.Calisthenics, SectionType.Filler)
        )
        .flatten ++ Vector(SectionType.Close)

    def elementToSimpleExercise(e: Element): Exercise =
      SimpleExercise(
        e.id,
        duration = Present(profile.exerciseDuration),
        reps = Absent
      )

    def makeExercises(
        existingSections: Vector[TrainingSection],
        sectionType: SectionType
    ): Vector[Exercise] =

      def makeCalisthenics =
        shuffle(elements(SectionType.Calisthenics))
          .take(profile.calisthenicExercises)
          .map(elementToSimpleExercise)

      def makeWarmup =
        val openingExercises = elements(SectionType.Warmup)
          .map(e => (e, e.order.toOption))
          .filter(_._2.nonEmpty)
          .sortBy(_._2.getOrElse(Int.MaxValue))
          .map(_._1)

        val otherExercises =
          shuffle(elements(SectionType.Warmup).filter(_.order.toOption.isEmpty))
            .take(
              ((profile.warmupDuration - (openingExercises.length * profile.exerciseDuration * 2)) /
                (profile.exerciseDuration * 2)).toInt
            )

        (openingExercises ++ otherExercises).map(elementToSimpleExercise)

      def makeCombo =
        val allCombo = generateCombo(profile.comboProfile)

        val comboParts =
          split(allCombo.movements, profile.comboProfile.buildUpLength)
            .foldLeft(Vector.empty[Vector[ComboMovementInstance]]) { (acc, curr) =>
              acc :+ (acc.lastOption.getOrElse(Vector.empty) ++ curr)
            }

        comboParts.map { ms =>
          ComboExercise(
            "combo-training",
            Present(profile.exerciseDuration),
            Absent,
            ms
          )
        } :+ ComboExercise(
          "combo-reps",
          Present(allCombo.duration * profile.comboReps),
          Present(profile.comboReps),
          allCombo.movements
        )

      def makeFiller =
        val existingExercises = existingSections.filter(_.`type` === SectionType.Filler).flatMap(_.exercises)
        val remainingExercises = elements(SectionType.Filler).filterNot(existingExercises.contains)
        shuffle(remainingExercises).take(1).map(elementToSimpleExercise)

      def makeClose = shuffle(elements(SectionType.Close)).take(1).map(elementToSimpleExercise)

      sectionType match
        case SectionType.Warmup       => makeWarmup
        case SectionType.Combo        => makeCombo
        case SectionType.Calisthenics => makeCalisthenics
        case SectionType.Filler       => makeFiller
        case SectionType.Close        => makeClose

    def sectionDuration(g: GroupType, e: Vector[Exercise]): FiniteDuration =
      (g match
        case GroupType.together => 1
        case GroupType.split    => 2
      ) * e.length * profile.exerciseDuration

    def makeSections: Vector[TrainingSection] = sections.foldLeft(
      Vector.empty[TrainingSection]
    ) { (previousSections, sectionType) =>
      val exercises = makeExercises(previousSections, sectionType)
      val group = SectionType.group(sectionType)

      previousSections :+ TrainingSection(
        s"$sectionType",
        sectionType,
        group,
        sectionDuration(group, exercises),
        exercises
      )
    }

    val s =
      val baseSections = makeSections
      val durationSoFar = baseSections.map(_.duration).fold(0 seconds)(_ + _)
      val moreFillers = (profile.trainingDuration - durationSoFar) / 2

      val sectionsWithFillers = baseSections.map {
        case s if s.`type` === SectionType.Filler => s
        case s                                   => s
      }
      sectionsWithFillers

    Training(
      s.map(_.duration).foldLeft(0 seconds)(_ + _),
      s
    )

  def getMovements(respond: Resource.GetMovementsResponse.type)(): F[Resource.GetMovementsResponse] =
    respond.Ok(allMovements).pure[F]

  def getCombo(respond: Resource.GetComboResponse.type)(): F[Resource.GetComboResponse] =
    respond
      .Ok(generateCombo(ComboProfile.Default))
      .pure[F]

  def allMovements: Vector[ComboMovement]

  lazy val comboMovementsMap: Map[String, ComboMovement] = allMovements.map(e => e.id -> e).toMap

  def generateCombo(profile: ComboProfile): ComboInstance =

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

    ComboInstance(
      calculateDuration[ComboMovement](allCombo, (m1, m2) => profile.transitionDuration(m1.bodyPart, m2.bodyPart)),
      allCombo.map(m => ComboMovementInstance(m.id, m.description, m.picture, m.video))
    )
