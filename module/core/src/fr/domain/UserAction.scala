package fr.domain

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

sealed trait UserAction

object UserAction {
  case class JoinedTable(tid: TableId)                      extends UserAction
  case class BetPlaced(bet: Bet, tid: TableId, gid: GameId) extends UserAction
  case class BetRemoved(tid: TableId, gid: GameId)          extends UserAction
  case class LeftTable(tid: TableId)                        extends UserAction
  case object StateRequested                                extends UserAction

  implicit val codec: Codec[UserAction] = deriveCodec
}
