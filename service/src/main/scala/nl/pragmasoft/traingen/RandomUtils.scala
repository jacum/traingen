package nl.pragmasoft.traingen

import scala.util.Random

object RandomUtils:

  private val random = new Random()

  def shuffle[A](vector: Vector[A]): Vector[A] = random.shuffle(vector)

  def between(minInc: Int, maxExc: Int): Int = random.between(minInc, maxExc)

  def chance(v: Double): Boolean = v > random.nextDouble()

  @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
  def pickOne[A](v: Vector[A]) =
    v(random.between(0, v.size))

  @SuppressWarnings(Array("org.wartremover.warts.SeqApply", "org.wartremover.warts.Throw"))
  def pickOneWithChance[A](values: Vector[(A, Double)]): A =
    require(values.nonEmpty, "The vector must not be empty.")
    require(values.forall(_._2 > 0), "All chances must be greater than 0.")

    val totalWeight = values.map(_._2).sum
    val randomPoint = Random.nextDouble() * totalWeight

    values
      .scanLeft((None: Option[A], 0.0)) { case ((_, acc), (value, weight)) =>
        (Some(value), acc + weight)
      }
      .collectFirst {
        case (Some(value), cumulativeWeight) if randomPoint < cumulativeWeight => value
      }
      .getOrElse(throw new RuntimeException("Unexpected error during selection."))

  @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
  def pickWithRepetitions[A](n: Int, v: Vector[A]): Vector[A] =
    if v.isEmpty then Vector.empty[A]
    else
      def randomIndex = random.nextInt(v.size)
      Vector.fill(n)(v(randomIndex))

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def split[A](vector: Vector[A], numSplits: Int): Vector[Vector[A]] =
    if numSplits <= 0 then throw new IllegalArgumentException("Number of splits must be greater than 0")
    else if numSplits == 1 then Vector(vector)
    else
      val chunkSize = math.ceil(vector.size.toDouble / numSplits).toInt
      vector.grouped(chunkSize).toVector
