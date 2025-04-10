package fr.domain.user

import fr.domain.{EventId, GameId, TableId, UserId}
import fr.domain.game.roulette.Bet
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant

sealed trait UserTableAction {
  def tid: TableId
}
object UserTableAction {
  case class JoinTable(tid: TableId)                       extends UserTableAction
  case class PlaceBet(tid: TableId, gid: GameId, bet: Bet) extends UserTableAction
  case class RemoveBets(tid: TableId, gid: GameId)         extends UserTableAction
  case class LeaveTable(tid: TableId)                      extends UserTableAction

  implicit val codec: Codec[UserTableAction] = deriveCodec

  case class UserTableActionEnvelope(id: EventId, uid: UserId, event: UserTableAction, ts: Instant)
  object UserTableActionEnvelope {
    implicit val codec: Codec[UserTableActionEnvelope] = deriveCodec
  }
}
