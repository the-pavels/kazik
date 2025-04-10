package fr.domain.user

import fr.domain.game.roulette.Bet
import fr.domain.{GameId, TableId}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait UserInput

object UserInput {
  case class JoinTable(tid: TableId)                       extends UserInput
  case class PlaceBet(bet: Bet, tid: TableId, gid: GameId) extends UserInput
  case class RemoveBets(tid: TableId, gid: GameId)         extends UserInput
  case class LeaveTable(tid: TableId)                      extends UserInput
  case object RequestState                                 extends UserInput

  implicit val codec: Codec[UserInput] = deriveCodec
}
