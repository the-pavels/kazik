package fr.domain

import cats.{Eq, Show}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Bet(id: BetId, number: Int, amount: Int)
object Bet {
  implicit val encoder: Codec[Bet] = deriveCodec
  implicit val eqv: Eq[Bet]        = Eq.fromUniversalEquals
  implicit val show: Show[Bet]     = Show.fromToString
}
