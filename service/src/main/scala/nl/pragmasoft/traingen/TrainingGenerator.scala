//package nl.pragmasoft.traingen
//
//import cats.Applicative
//import cats.syntax.applicative.*
//import nl.pragmasoft.traingen.http.{Handler, Resource}
//import nl.pragmasoft.traingen.http.definitions.{
//  ComboExercise,
//  ComboMovement,
//  Element,
//  Exercise,
//  Library,
//  SimpleExercise,
//  Training,
//  TrainingSection
//}
//import nl.pragmasoft.traingen.http.support.Presence.{Absent, Present}
//
//import scala.annotation.tailrec
//import scala.concurrent.duration.*
//import scala.util.Random
//
//@SuppressWarnings(Array("org.wartremover.warts.Equals"))
//class TrainingGenerator[F[_]: Applicative] extends Handler[F]:
//
//  import RandomUtils.*
//
//  private val elements: Map[SectionType, Vector[Element]] =
//    availableElements.elements.flatMap(e => e.sections.map(s => s -> e)).groupMap(_._1)(_._2)
//
//  private val allComboMovements = elements(SectionType.combo)
//  private val comboMovementsMap = allComboMovements.map(e => e.id -> e).toMap
//  private val movementsAfter: Map[String, Vector[ComboMovement]] =
//    allComboMovements
//      .flatMap(e => e.after.toOption.getOrElse(Vector.empty).map(s => s -> ComboMovement(e.id)))
//      .groupMap(_._1)(_._2)
//
//  allComboMovements.map(_.id).foreach(k => assert(movementsAfter.contains(k), s"$k has no 'after' movements"))
//
//  private val openingMovements: Vector[ComboMovement] =
//    allComboMovements.filter(_.order.toOption.contains(1)).map(e => ComboMovement(e.id))
//
//  def generateTraining(profile: ComboProfile): Training =
//
//    val remainingDuration =
//      profile.trainingDuration - profile.warmupDuration - profile.closeExercises * profile.exerciseDuration
//
//    val comboDuration = profile.exerciseDuration * (profile.comboBuildUpLength + 1) * 2
//
//    val calisthenicsDuration = (profile.exerciseDuration * profile.calistenicsExercises)
//
//    val sections: Vector[SectionType] = Vector(SectionType.warmup, SectionType.filler) ++
//      Vector
//        .fill((remainingDuration / (comboDuration + calisthenicsDuration)).toInt)(
//          Vector(SectionType.combo, SectionType.calisthenics, SectionType.filler)
//        )
//        .flatten ++ Vector(SectionType.close)
//
//    def calculateMovementDuration(ms: Vector[ComboMovement]): FiniteDuration =
//      ms.map(m =>
//        comboMovementsMap(m.ref).bodyParts match
//          case p if p.exists(BodyParts.Arms.contains) => 500 millis
//          case p if p.exists(BodyParts.Legs.contains) => 1 second
//          case _ => 0 seconds // catch all, shouldn't happen because all combos are either legs or arms
//      ).foldLeft(0 seconds)(_ + _)
//
//    def elementToSimpleExercise(e: Element): Exercise =
//      SimpleExercise(
//        e.id,
//        duration = Present(profile.exerciseDuration),
//        reps = Absent
//      )
//
//    def makeExercises(
//        existingSections: Vector[TrainingSection],
//        sectionType: SectionType
//    ): Vector[Exercise] =
//
//      def makeCalisthenics =
//        shuffle(elements(SectionType.calisthenics))
//        .take(profile.calistenicsExercises)
//        .map(elementToSimpleExercise)
//
//      def makeWarmup =
//        val openingExercises = elements(SectionType.warmup)
//          .map(e => (e, e.order.toOption))
//          .filter(_._2.nonEmpty)
//          .sortBy(_._2.getOrElse(Int.MaxValue))
//          .map(_._1)
//
//        val otherExercises =
//          shuffle(elements(SectionType.warmup).filter(_.order.toOption.isEmpty))
//            .take(
//              ((profile.warmupDuration - (openingExercises.length * profile.exerciseDuration * 2)) /
//                (profile.exerciseDuration * 2)).toInt
//            )
//
//        (openingExercises ++ otherExercises).map(elementToSimpleExercise)
//
//      def makeCombo =
//        val comboLength = between(profile.comboMovementsMin, profile.comboMovementsMax)
//
//        @tailrec
//        @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
//        def pickMovements(acc: Vector[ComboMovement]): Vector[ComboMovement] =
//          if acc.sizeIs == comboLength then acc
//          else pickMovements(acc :+ pickOne(movementsAfter(acc.last.ref)))
//
//        val allCombo = pickMovements(Vector(pickOne(openingMovements)))
//
//        val comboParts =
//          split(allCombo, profile.comboBuildUpLength)
//            .foldLeft(Vector.empty[Vector[ComboMovement]]) { (acc, curr) =>
//              acc :+ (acc.lastOption.getOrElse(Vector.empty) ++ curr)
//            }
//
//        comboParts.map { ms =>
//          ComboExercise(
//            "combo-training",
//            Present(profile.exerciseDuration),
//            Absent,
//            ms
//          )
//        } :+ ComboExercise(
//          "combo-reps",
//          Present(calculateMovementDuration(allCombo)* profile.comboReps),
//          Present(profile.comboReps),
//          allCombo
//        )
//
//      def makeFiller = {
//        val existingExercises = existingSections.filter(_.`type` == SectionType.filler).flatMap(_.exercises)
//        val remainingExercises = elements(SectionType.filler).filterNot(existingExercises.contains)
//        shuffle(remainingExercises).take(1).map(elementToSimpleExercise)
//      }
//
//      def makeClose = shuffle(elements(SectionType.close)).take(1).map(elementToSimpleExercise)
//
//      sectionType match
//        case SectionType.warmup       => makeWarmup
//        case SectionType.combo        => makeCombo
//        case SectionType.calisthenics => makeCalisthenics
//        case SectionType.filler       => makeFiller
//        case SectionType.close        => makeClose
//
//    def sectionDuration(g: GroupType, e: Vector[Exercise]): FiniteDuration =
//      (g match
//          case GroupType.together => 1
//          case GroupType.split    => 2
//        ) * e.length * profile.exerciseDuration
//
//    def makeSections: Vector[TrainingSection] = sections.foldLeft(
//      Vector.empty[TrainingSection]
//    ) { (previousSections, sectionType) =>
//      val exercises = makeExercises(previousSections, sectionType)
//      val group = SectionType.group(sectionType)
//
//      previousSections :+ TrainingSection(
//        s"$sectionType",
//        sectionType,
//        group,
//        sectionDuration(group, exercises),
//        exercises
//      )
//    }
//
//    val s = {
//      val baseSections = makeSections
//      val durationSoFar = baseSections.map(_.duration).fold(0 seconds)(_ + _)
//      val moreFillers = (profile.trainingDuration - durationSoFar) / 2
//
//      val sectionsWithFillers = baseSections.map {
//        case s if s.`type` == SectionType.filler => s
//        case s                                   => s
//      }
//      sectionsWithFillers
//    }
//
//    Training(
//      s.map(_.duration).foldLeft(0 seconds)(_ + _),
//      s
//    )
//
//  def availableElements: Library = LibraryLoader.load("library.json")
//
//  def getLibrary(
//      respond: Resource.GetLibraryResponse.type
//  )(): F[Resource.GetLibraryResponse] = respond.Ok(availableElements).pure[F]
//
//  def getTraining(
//      respond: Resource.GetTrainingResponse.type
//  )(): F[Resource.GetTrainingResponse] = respond
//    .Ok(generateTraining(ComboProfile.Default))
//    .pure[F]
