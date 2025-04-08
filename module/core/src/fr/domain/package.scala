package fr

import cats.implicits.toShow
import cats.{Eq, Show}
import ciris.ConfigValue
import io.circe._
import io.estatico.newtype.Coercible
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._

import java.util.UUID

package object domain {
  implicit class ConfigOps[F[_], A](cv: ConfigValue[F, A]) {
    // Same as `default` but it allows you to use the underlying type of the newtype
    def withDefault[T](value: T)(implicit ev: Coercible[T, A]): ConfigValue[F, A] =
      cv.default(value.coerce[A])
  }

  @newtype
  case class UserId(value: UUID) {
    def asKey: String = s"user:$value"
  }
  object UserId {
    implicit val codec: Codec[UserId] = Codec.from(Decoder.decodeString.map(s => UserId(UUID.fromString(s))), Encoder.encodeString.contramap[UserId](_.show))
    implicit val keyEncoder: KeyEncoder[UserId] =
      KeyEncoder.encodeKeyString.contramap[UserId](_.toString())
    implicit val keyDecoder: KeyDecoder[UserId] = KeyDecoder.decodeKeyString.map(s => UserId(UUID.fromString(s)))
    implicit val eqv: Eq[UserId]                = Eq.fromUniversalEquals
    implicit val show: Show[UserId]             = Show.fromToString
  }

  @newtype
  case class BetId(value: UUID)
  object BetId {
    implicit val encoder: Encoder[BetId] =
      Encoder.encodeString.contramap[BetId](_.toString())
    implicit val decoder: Decoder[BetId] =
      Decoder.decodeString.map(s => BetId(UUID.fromString(s)))
    implicit val eqv: Eq[BetId]    = Eq.fromUniversalEquals
    implicit val show: Show[BetId] = Show.fromToString
  }

  @newtype
  case class TableId(value: UUID) {
    def asKey: String = s"table:$value"
  }
  object TableId {
    implicit val encoder: Encoder[TableId] =
      Encoder.encodeString.contramap[TableId](_.toString())
    implicit val decoder: Decoder[TableId] =
      Decoder.decodeString.map(s => TableId(UUID.fromString(s)))
    implicit val eqv: Eq[TableId]    = Eq.fromUniversalEquals
    implicit val show: Show[TableId] = Show.fromToString
  }
  @newtype
  case class GameId(value: UUID)
  object GameId {
    implicit val encoder: Encoder[GameId] =
      Encoder.encodeString.contramap[GameId](_.toString())
    implicit val decoder: Decoder[GameId] =
      Decoder.decodeString.map(s => GameId(UUID.fromString(s)))
    implicit val eqv: Eq[GameId]    = Eq.fromUniversalEquals
    implicit val show: Show[GameId] = Show.fromToString
  }
}
