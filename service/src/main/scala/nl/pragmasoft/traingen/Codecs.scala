package nl.pragmasoft.traingen

import java.util.Locale
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import io.circe.Decoder
import io.circe.Encoder

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
object Codecs:

  import scala.compiletime.summonAll
  import scala.deriving.Mirror

  private inline def stringEnumDecoder[T](using
      m: Mirror.SumOf[T]
  ): Decoder[T] =
    val elemInstances =
      summonAll[Tuple.Map[m.MirroredElemTypes, ValueOf]].productIterator
        .asInstanceOf[Iterator[ValueOf[T]]]
        .map(_.value)
    val elemNames =
      summonAll[Tuple.Map[m.MirroredElemLabels, ValueOf]].productIterator
        .asInstanceOf[Iterator[ValueOf[String]]]
        .map(_.value)
    val mapping = elemNames.zip(elemInstances).toMap
    Decoder[String].emap { name =>
      mapping.get(name).fold(Left(s"Name $name is invalid value"))(Right(_))
    }

  private inline def stringEnumEncoder[T](using
      m: Mirror.SumOf[T]
  ): Encoder[T] =
    val elemInstances =
      summonAll[Tuple.Map[m.MirroredElemTypes, ValueOf]].productIterator
        .asInstanceOf[Iterator[ValueOf[T]]]
        .map(_.value)
    val elemNames =
      summonAll[Tuple.Map[m.MirroredElemLabels, ValueOf]].productIterator
        .asInstanceOf[Iterator[ValueOf[String]]]
        .map(_.value)
    val mapping = elemInstances.zip(elemNames).toMap
    Encoder[String].contramap[T](mapping.apply)

  given Encoder[BodyPart] = stringEnumEncoder
  given Decoder[BodyPart] = stringEnumDecoder

  given Encoder[SectionType] = stringEnumEncoder
  given Decoder[SectionType] = stringEnumDecoder

  given Encoder[GroupType] = stringEnumEncoder
  given Decoder[GroupType] = stringEnumDecoder

  given Encoder[FiniteDuration] = Encoder.encodeString.contramap { fd =>
    val seconds = fd.toUnit(TimeUnit.SECONDS).toInt
    s"$seconds seconds"
  }

  given Decoder[FiniteDuration] = Decoder.decodeString.emap { str =>
    val parts = str.split(" ", 2).map(_.trim)
    if parts.length == 2 then
      val length = parts(0)
      val unit = parts(1)

      try
        val duration = FiniteDuration(length.toLong, unit)
        Right(duration)
      catch
        case _: NumberFormatException    => Left(s"Invalid length in: $str")
        case _: IllegalArgumentException => Left(s"Invalid unit in: $str")
    else Left(s"Invalid duration format: $str")
  }
