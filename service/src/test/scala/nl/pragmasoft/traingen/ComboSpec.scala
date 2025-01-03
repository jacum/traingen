package nl.pragmasoft.traingen

import cats.effect.IO
import nl.pragmasoft.traingen.http.definitions.ComboMovement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ComboSpec extends AnyFunSuite with Matchers:

  test("combos according to default rules") {
    val g = new ComboGenerator[IO]:
      val allMovements: Vector[ComboMovement] = LibraryLoader.load("combo-movements.json")
    val p = ComboProfile.Default
    for _ <- 1 to 100000 do
      val c = g.generateCombo(p)
      val movements = c.movements.map(m => g.comboMovementsMap(m.id))
      movements.length should equal(p.movementsMin +- (p.movementsMax - p.movementsMin))
      movements.sliding(p.doublesMax + 1).forall(v => v.headOption.forall(h => v.forall(_ === h))) should not be true
      movements.sliding(p.oneSideMovementsMax + 1).forall(_.forall(m => BodyPart.right(m.bodyPart))) should not be true
      movements.sliding(p.oneSideMovementsMax + 1).forall(_.forall(m => BodyPart.left(m.bodyPart))) should not be true
  }
