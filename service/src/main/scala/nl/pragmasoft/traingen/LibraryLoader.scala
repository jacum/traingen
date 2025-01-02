package nl.pragmasoft.traingen

import scala.io.Codec.defaultCharsetCodec
import scala.io.Source

import io.circe.*
import io.circe.parser.*

object LibraryLoader:

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def load[A](path: String)(using decoder: Decoder[A]): A =
    val source =
      Source.fromResource(path)(defaultCharsetCodec).getLines().mkString
    decode[A](source) match
      case Right(l) => l
      case Left(e) =>
        println(e)
        throw new IllegalArgumentException(e.getMessage)
