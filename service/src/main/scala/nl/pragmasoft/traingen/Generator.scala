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

@SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
abstract class Generator[F[_]: Applicative] extends Handler[F]:
  import Generator.*

  def allExercises: Library

  def getLibrary(respond: Resource.GetLibraryResponse.type)(): F[Resource.GetLibraryResponse] =
    respond.Ok(allExercises).pure[F]

  def getTraining(respond: Resource.GetTrainingResponse.type)(
      comboMovements: Option[BigInt],
      comboBuildup: Option[BigInt],
      totalMinutes: Option[BigInt],
      calisthenicsExercises: Option[BigInt],
      warmupMinutes: Option[BigInt]
  ): F[Resource.GetTrainingResponse] =
    val profile = TrainingProfile.Default.copy(
      trainingDuration = totalMinutes.map(m => m.longValue.minutes).getOrElse(TrainingProfile.Default.trainingDuration),
      calisthenicExercises =
        calisthenicsExercises.map(_.intValue).getOrElse(TrainingProfile.Default.calisthenicExercises),
      warmupDuration = warmupMinutes.map(m => m.longValue.minutes).getOrElse(TrainingProfile.Default.warmupDuration),
      comboProfile = TrainingProfile.Default.comboProfile.copy(
        movements = comboMovements.map(_.intValue).getOrElse(TrainingProfile.Default.comboProfile.movements),
        buildUpLength = comboBuildup.map(_.intValue).getOrElse(TrainingProfile.Default.comboProfile.buildUpLength)
      )
    )
    respond.Ok(generateTraining(profile)).pure[F]

  def generateTraining(profile: TrainingProfile): Training =

    def makeCombo =
      val allCombo = generateCombo(profile.comboProfile)

      val comboParts =
        split(allCombo.movements, allCombo.movements.size / profile.comboProfile.buildUpLength)
          .foldLeft(Vector.empty[Vector[ComboMovementInstance]]) { (acc, curr) =>
            if curr.isEmpty then acc
            else acc :+ (acc.lastOption.getOrElse(Vector.empty) ++ curr)
          }

      comboParts.map { ms =>
        ComboExercise(
          "combo-training",
          profile.exerciseDuration,
          Absent,
          ms
        )
      } :+ ComboExercise(
        "combo-reps",
        allCombo.duration * profile.comboReps,
        Present(profile.comboReps),
        allCombo.movements
      )

    def elementToSimpleExercise(e: Element): Exercise =
      SimpleExercise(
        e.id,
        duration = profile.exerciseDuration,
        reps = Absent
      )

    val elements: Map[SectionType, Vector[Element]] =
      allExercises.elements.flatMap(e => e.sections.map(s => s -> e)).groupMap(_._1)(_._2)

    def makeExercises(
        existingSections: Vector[TrainingSection],
        sectionType: SectionType,
        usedElements: Set[Element] = Set.empty
    ): Vector[Exercise] =

      def collectExistingExercises =
        existingSections
          .flatMap(_.exercises)
          .collect { case SimpleExercise(id, _, _) =>
            elements.values.flatten.find(_.id === id)
          }
          .flatten
          .toSet

      val allUsedElements = usedElements ++ collectExistingExercises

      def makeCalisthenics =
        shuffle(elements(SectionType.Calisthenics).filterNot(allUsedElements.contains))
          .take(profile.calisthenicExercises)
          .map(elementToSimpleExercise)

      def makeWarmup =
        val openingExercises = elements(SectionType.Warmup)
          .filterNot(allUsedElements.contains)
          .map(e => (e, e.order.toOption))
          .filter(_._2.nonEmpty)
          .sortBy(_._2.getOrElse(Int.MaxValue))
          .map(_._1)

        val otherExercises =
          shuffle(elements(SectionType.Warmup).filterNot(allUsedElements.contains).filter(_.order.toOption.isEmpty))
            .take(
              ((profile.warmupDuration - (openingExercises.length * profile.exerciseDuration * 2)) /
                (profile.exerciseDuration * 2)).toInt
            )

        (openingExercises ++ otherExercises).map(elementToSimpleExercise)

      def makeFiller =
        shuffle(elements(SectionType.Filler).filterNot(allUsedElements.contains))
          .take(1)
          .map(elementToSimpleExercise)

      def makeClose = shuffle(elements(SectionType.Close).filterNot(allUsedElements.contains))
        .take(profile.closeExercises)
        .map(elementToSimpleExercise)

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
      ) * e.length * {
        profile.exerciseDuration
      }

    val remainingDuration =
      profile.trainingDuration - profile.warmupDuration - profile.closeExercises * profile.exerciseDuration

    val calisthenicsDuration = profile.exerciseDuration * profile.calisthenicExercises

    // assessing average combo size for given profile
    val expectedFullComboDuration =
      val trialCombos = Vector.fill(100)(makeCombo).map(_.map(_.duration).fold(0 seconds)(_ + _))
      val avgDuration = trialCombos.fold(0 seconds)(_ + _) / trialCombos.size
      avgDuration * 2 // split group

    val numberOfCombos = (remainingDuration / (expectedFullComboDuration + calisthenicsDuration)).toInt
    val sections: Vector[SectionType] = Vector(SectionType.Warmup) ++
      Vector
        .fill(numberOfCombos)(
          Vector(SectionType.Combo, SectionType.Calisthenics)
        )
        .flatten ++ Vector(SectionType.Close)

    def makeSections: Vector[TrainingSection] = sections
      .foldLeft(
        (Vector.empty[TrainingSection], Set.empty[Element])
      ) { case ((previousSections, usedElements), sectionType) =>
        val exercises = makeExercises(previousSections, sectionType, usedElements)
        val group = SectionType.group(sectionType)
        val newSection = TrainingSection(
          s"$sectionType",
          sectionType,
          group,
          sectionDuration(group, exercises),
          exercises
        )
        val newUsedElements = usedElements ++ exercises.collect { case SimpleExercise(id, _, _) =>
          elements.values.flatten.find(_.id === id)
        }.flatten
        (previousSections :+ newSection, newUsedElements)
      }
      ._1

    // Helper methods
    def calculateTotalDuration(sections: Vector[TrainingSection]): FiniteDuration =
      sections.map(_.duration).fold(0.seconds)(_ + _)

    def findFillerInsertionPoints(sections: Vector[TrainingSection]): Vector[Int] =
      sections.zipWithIndex.collect {
        case (section, index) if section.`type` === SectionType.Combo || section.`type` === SectionType.Close => index
      }

    def distributeFillers(totalFillers: Int, insertionPointCount: Int): (Int, Int) =
      val baseFillerCount = totalFillers / insertionPointCount
      val remainingFillers = totalFillers % insertionPointCount
      (baseFillerCount, remainingFillers)

    def injectFillers(baseSections: Vector[TrainingSection]): Vector[TrainingSection] =
      val durationSoFar = calculateTotalDuration(baseSections)
      val remainingDuration = profile.trainingDuration - durationSoFar
      val FillerMultiplier = 2 // Multiplier used for filler duration calculation

      // Calculate how many filler exercises we need to add
      val totalFillers = ((remainingDuration / FillerMultiplier) / profile.exerciseDuration).toInt

      // Find suitable places to insert fillers (after combo or close sections)
      val insertionPoints = findFillerInsertionPoints(baseSections)

      if totalFillers <= 0 || insertionPoints.isEmpty then baseSections
      else
        // Calculate distribution of fillers
        val (fillersPerPoint, extraFillers) = distributeFillers(totalFillers, insertionPoints.length)

        // Insert fillers at calculated points
        insertFillerSections(baseSections, insertionPoints, fillersPerPoint, extraFillers)

    def insertFillerSections(
        sections: Vector[TrainingSection],
        insertionPoints: Vector[Int],
        baseCount: Int,
        extraCount: Int
    ): Vector[TrainingSection] =
      insertionPoints.zipWithIndex
        .foldLeft((sections, Set.empty[Element])) { case ((currentSections, usedFillerElements), (insertIndex, idx)) =>
          val fillerCount = baseCount + (if idx < extraCount then 1 else 0)

          if fillerCount == 0 then (currentSections, usedFillerElements)
          else {
            val (sectionsBeforePoint, sectionsAfterPoint) = currentSections.splitAt(insertIndex)
            val fillerExercises = createFillerExercises(fillerCount, currentSections, usedFillerElements)
            val newUsedElements = trackUsedElements(usedFillerElements, fillerExercises)
            val fillerSection = createFillerSection(fillerCount, fillerExercises)

            (sectionsBeforePoint ++ Vector(fillerSection) ++ sectionsAfterPoint, newUsedElements)
          }
        }
        ._1

    def createFillerExercises(
        count: Int,
        sections: Vector[TrainingSection],
        usedElements: Set[Element]
    ): Vector[Exercise] =
      (0 until count).flatMap(_ => makeExercises(sections, SectionType.Filler, usedElements)).toVector

    def trackUsedElements(usedElements: Set[Element], exercises: Vector[Exercise]): Set[Element] =
      usedElements ++ exercises.collect { case SimpleExercise(id, _, _) =>
        elements.values.flatten.find(_.id === id)
      }.flatten

    def createFillerSection(count: Int, exercises: Vector[Exercise]): TrainingSection =
      TrainingSection(
        "Filler",
        SectionType.Filler,
        GroupType.split,
        profile.exerciseDuration * 2 * count,
        exercises
      )

    val finalSections = injectFillers(makeSections)

    Training(
      finalSections.map(_.duration).foldLeft(0 seconds)(_ + _),
      finalSections
    )

  def getMovements(respond: Resource.GetMovementsResponse.type)(): F[Resource.GetMovementsResponse] =
    respond.Ok(allMovements).pure[F]

  def getCombo(respond: Resource.GetComboResponse.type)(movements: Option[BigInt]): F[Resource.GetComboResponse] =
    val profile = movements.fold(ComboProfile.Default)(m => ComboProfile.Default.copy(movements = m.intValue))
    respond
      .Ok(generateCombo(profile))
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

      if acc.sizeIs == profile.movements then acc
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
