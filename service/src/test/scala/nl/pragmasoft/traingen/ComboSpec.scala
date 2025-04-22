package nl.pragmasoft.traingen

import cats.effect.IO
import nl.pragmasoft.traingen.http.definitions.{ComboMovement, Element}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ComboSpec extends AnyFunSuite with Matchers:

  test("combos according to default rules") {
    val g = new Generator[IO]:
      val allExercises: Vector[Element] = LibraryLoader.load("exercises.json")
      val allMovements: Vector[ComboMovement] = LibraryLoader.load("combo-movements.json")
    val p = ComboProfile.Default
    for _ <- 1 to 100000 do
      val c = g.generateCombo(p)
      val movements = c.movements.map(m => g.comboMovementsMap(m.id))
      movements.length shouldEqual p.movements
      movements.sliding(p.doublesMax + 1).forall(v => v.headOption.forall(h => v.forall(_ === h))) should not be true
      movements.sliding(p.oneSideMovementsMax + 1).forall(_.forall(m => BodyPart.right(m.bodyPart))) should not be true
      movements.sliding(p.oneSideMovementsMax + 1).forall(_.forall(m => BodyPart.left(m.bodyPart))) should not be true
  }

  ignore("minimum rest between same movements") {
    val g = new Generator[IO]:
      val allExercises: Vector[Element] = LibraryLoader.load("exercises.json")
      val allMovements: Vector[ComboMovement] = LibraryLoader.load("combo-movements.json")
    val p = ComboProfile.Default
    for _ <- 1 to 1000 do
      val c = g.generateCombo(p)
      val movements = c.movements.map(m => g.comboMovementsMap(m.id))
      movements.sliding(2).forall(pair => pair.size != 2 || pair(0).id != pair(1).id) shouldBe true
  }

  ignore("follows after rules") {
    val g = new Generator[IO]:
      val allExercises: Vector[Element] = LibraryLoader.load("exercises.json")
      val allMovements: Vector[ComboMovement] = LibraryLoader.load("combo-movements.json")
    val p = ComboProfile.Default
    for _ <- 1 to 1000 do
      val c = g.generateCombo(p)
      val movements = c.movements.map(m => g.comboMovementsMap(m.id))
      movements.sliding(2).forall { pair =>
        pair.size != 2 || pair(0).after.exists(_.id == pair(1).id)
      } shouldBe true
  }

  test("respects excluded movements") {
    val g = new Generator[IO]:
      val allExercises: Vector[Element] = LibraryLoader.load("exercises.json")
      val allMovements: Vector[ComboMovement] = LibraryLoader.load("combo-movements.json")
    val p = ComboProfile.Default
    for _ <- 1 to 1000 do
      val c = g.generateCombo(p)
      val movements = c.movements.map(m => g.comboMovementsMap(m.id))
      movements.sliding(2).forall { pair =>
        pair.size != 2 || !pair(0).excludes.toOption.exists(_.contains(pair(1).id))
      } shouldBe true
  }
