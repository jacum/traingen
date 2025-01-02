import wartremover.Wart

object WartRemoverSettings {

  val wartExclusionsMain: Seq[Wart] = Seq(
    Wart.Any,
    Wart.Nothing,
    Wart.ImplicitParameter,
    Wart.DefaultArguments,
    Wart.FinalCaseClass,
    Wart.NonUnitStatements,
    Wart.Serializable, // All case classes extend Serializable => Inferred type containing Serializable
    Wart.JavaSerializable, // All case classes extend Serializable => Inferred type containing Serializable
    Wart.Product, // All case classes extend Product => Inferred type containing Product
  )

  val wartExclusionsTest: Seq[Wart] = wartExclusionsMain ++
    Seq(Wart.NonUnitStatements)
}
