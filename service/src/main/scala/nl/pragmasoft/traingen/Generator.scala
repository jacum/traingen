package nl.pragmasoft.traingen

import cats.{Applicative, Eq}
import cats.implicits.catsSyntaxEq
import cats.syntax.applicative.*
import cats.syntax.eq.*
import nl.pragmasoft.traingen.RandomUtils.*
import nl.pragmasoft.traingen.http.definitions.*
import nl.pragmasoft.traingen.http.support.Presence.*
import nl.pragmasoft.traingen.http.{Handler, Resource}

import scala.annotation.tailrec
import scala.concurrent.duration.*

object Generator:
  val StartExercise = "_start_"
  implicit val sectionTypeEq: Eq[SectionType] = Eq.fromUniversalEquals
  implicit val elementEq: Eq[Element] = Eq.fromUniversalEquals

@SuppressWarnings(Array("org.wartremover.warts.SeqApply", "org.wartremover.warts.Throw"))
abstract class Generator[F[_]: Applicative] extends Handler[F]:
  import Generator.*

  def allExercises: Vector[Element]

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

  def validateTraining(training: Training, requestedDuration: FiniteDuration): Boolean =
    Math.abs(training.duration.toSeconds - requestedDuration.toSeconds) <= 60

  def generateTraining(profile: TrainingProfile): Training =
    @tailrec
    def tryGenerateTraining(): Training =
      val training = createTraining(profile)
      if validateTraining(training, profile.trainingDuration) then training
      else tryGenerateTraining()

    tryGenerateTraining()

  private def createTraining(profile: TrainingProfile): Training =

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
          "Combo training",
          profile.exerciseDuration,
          Absent,
          ms
        )
      } :+ ComboExercise(
        "combo-reps",
        s"Combo: ${profile.comboReps} repetitions",
        allCombo.duration * profile.comboReps,
        Present(profile.comboReps),
        allCombo.movements
      )

    def elementToSimpleExercise(e: Element): Exercise =
      SimpleExercise(
        e.id,
        e.title,
        duration = profile.exerciseDuration,
        reps = Absent
      )

    val elements: Map[SectionType, Vector[Element]] =
      allExercises.flatMap(e => e.sections.map(s => s -> e)).groupMap(_._1)(_._2)

    def makeExercises(
        sections: Vector[TrainingSection],
        sectionType: SectionType,
        usedElements: Set[Element] = Set.empty
    ): Vector[Exercise] =

      val allUsedElements = usedElements ++ sections
        .flatMap(_.exercises)
        .collect { case SimpleExercise(id, _, _, _) =>
          elements.values.flatten.find(_.id === id)
        }
        .flatten
        .toSet

      def selectExercises(sectionType: SectionType, numberOfExercises: Int, filter: Element => Boolean = _ => true) =
        val exercises = shuffle(elements(sectionType).filterNot(allUsedElements.contains)).filter(filter)

        @tailrec
        def select(remaining: Vector[Element], selected: Vector[Element], count: Int): Vector[Element] =
          if count == 0 || remaining.isEmpty then selected
          else
            val element = pickOne(remaining)
            val excludedIds = element.excludes.toOption.getOrElse(Vector.empty)
            val nextRemaining = remaining.filterNot(e => e === element || excludedIds.contains(e.id))

            select(nextRemaining, selected :+ element, count - 1)

        select(exercises, Vector.empty, numberOfExercises)

      def makeCalisthenics =
        selectExercises(SectionType.Calisthenics, profile.calisthenicExercises)
          .map(elementToSimpleExercise)

      def makeWarmup =
        val openingExercises = elements(SectionType.Warmup)
          .map(e => (e, e.order.toOption))
          .filter(_._2.nonEmpty)
          .sortBy(_._2.getOrElse(Int.MaxValue))
          .map(_._1)

        val otherExercises =
          selectExercises(
            SectionType.Warmup,
            ((profile.warmupDuration - (openingExercises.length * profile.exerciseDuration * 2))
              / (profile.exerciseDuration * 2)).toInt,
            _.order.toOption.isEmpty
          )

        (openingExercises ++ otherExercises).map(elementToSimpleExercise)

      def makeClose = shuffle(elements(SectionType.Close).filterNot(allUsedElements.contains))
        .take(profile.closeExercises)
        .map(elementToSimpleExercise)

      sectionType match
        case SectionType.Warmup       => makeWarmup
        case SectionType.Combo        => makeCombo
        case SectionType.Calisthenics => makeCalisthenics
        case SectionType.Filler       => Vector.empty // filled after
        case SectionType.Close        => makeClose

    def sectionDuration(g: GroupType, e: Vector[Exercise]): FiniteDuration =
      (g match
        case GroupType.together => 1
        case GroupType.split    => 2
      ) * e.map(_.duration).fold(0 seconds)(_ + _)

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
          Vector(SectionType.Filler, SectionType.Combo, SectionType.Calisthenics)
        )
        .flatten ++ Vector(SectionType.Filler, SectionType.Close)

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
        val newUsedElements = usedElements ++ exercises.collect { case SimpleExercise(id, _, _, _) =>
          elements.values.flatten.find(_.id === id)
        }.flatten
        (previousSections :+ newSection, newUsedElements)
      }
      ._1

    def calculateTotalDuration(sections: Vector[TrainingSection]): FiniteDuration =
      sections.map(s => sectionDuration(s.group, s.exercises)).fold(0.seconds)(_ + _)

    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    def injectFillerExercises(sections: Vector[TrainingSection]): Vector[TrainingSection] =
      val remainingDuration = profile.trainingDuration - calculateTotalDuration(sections)
      val totalExercises = Math.ceil(remainingDuration / (profile.exerciseDuration * 2)).toInt

      val fillerSections = sections.count(_.`type` === SectionType.Filler)
      if fillerSections === 0 then sections
      else
        val usedElements = sections
          .flatMap(_.exercises)
          .collect { case SimpleExercise(id, _, _, _) =>
            elements.values.flatten.find(_.id === id)
          }
          .flatten
          .toSet

        val availableFillers = shuffle(
          elements(SectionType.Filler).filterNot(usedElements.contains)
        )

        val exercisesPerSection =
          val baseAmount = totalExercises / fillerSections
          val remainder = totalExercises % fillerSections
          (0 until fillerSections).map(i => if i < remainder then baseAmount + 1 else baseAmount).toVector

        var fillerIndex = 0
        sections
          .map { section =>
            if section.`type` =!= SectionType.Filler then section
            else
              val start = exercisesPerSection.take(fillerIndex).sum
              val end = start + exercisesPerSection(fillerIndex)
              fillerIndex += 1

              val fillerExercises = availableFillers
                .slice(start, end)
                .map(elementToSimpleExercise)

              if fillerExercises.isEmpty then section
              else
                section.copy(
                  exercises = fillerExercises,
                  duration = sectionDuration(section.group, fillerExercises)
                )
          }
          .filter(section => section.`type` =!= SectionType.Filler || section.exercises.nonEmpty)

    val finalSections = injectFillerExercises(makeSections)
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
    val MinimumMovements = 4
    val adjustedProfile = if profile.movements < MinimumMovements then
      profile.copy(movements = MinimumMovements)
    else profile

    val movementsAfter: Map[String, Vector[ComboMovementChance]] =
      allMovements
        .flatMap(e => e.after.map(s => s.id -> ComboMovementChance(e.id, s.chance)))
        .groupMap(_._1)(_._2)

    val allOpeningMovements: Vector[ComboMovementChance] =
      allMovements.flatMap { m =>
        m.after.find(_.id === StartExercise).map(mc => ComboMovementChance(m.id, mc.chance))
      }

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

    case class ComboGenerationException(message: String) extends RuntimeException(message)

    @tailrec
    def leftRightBalanced(attempts: Int = 0): Vector[ComboMovement] =
      if attempts >= 1000 then
        throw ComboGenerationException("Failed to generate balanced combo after 1000 attempts")
      val combo = pickMovements(Vector(pickOneWithChance(toChanceVector(allOpeningMovements))))
      val leftRatio = combo.count(m => BodyPart.left(m.bodyPart)).toDouble / combo.size
      if leftRatio >= 0.4 && leftRatio <= 0.6 then combo
      else leftRightBalanced(attempts + 1)

    val allCombo = leftRightBalanced(0)

    ComboInstance(
      calculateDuration[ComboMovement](allCombo, (m1, m2) => profile.transitionDuration(m1.bodyPart, m2.bodyPart)),
      allCombo.map(m => ComboMovementInstance(m.id, m.description, m.picture, m.video))
    )
