package fr.domain.table

import fr.domain.game.roulette.Bet
import fr.domain.{EventId, GameId, TableId, UserId}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant

sealed trait TableEvent {
  def tid: TableId
  def uid: UserId
}
object TableEvent {
  case class BetAccepted(tid: TableId, gid: GameId, uid: UserId, bet: Bet)      extends TableEvent
  case class BetRejected(tid: TableId, gid: GameId, uid: UserId, bet: Bet)      extends TableEvent
  case class BetRemoved(tid: TableId, gid: GameId, uid: UserId, bet: List[Bet]) extends TableEvent
  case class BetsOpened(tid: TableId, gid: GameId, uid: UserId)                 extends TableEvent
  case class BetsClosed(tid: TableId, gid: GameId, uid: UserId)                 extends TableEvent
  case class BetWon(tid: TableId, gid: GameId, uid: UserId, amount: Int)        extends TableEvent
  case class GameFinished(tid: TableId, gid: GameId, uid: UserId, result: Int)  extends TableEvent
  case class JoinedTable(tid: TableId, uid: UserId, users: Set[UserId])         extends TableEvent
  case class LeftTable(tid: TableId, uid: UserId, users: Set[UserId])           extends TableEvent

  implicit val codec: Codec[TableEvent] = deriveCodec

  case class TableEventEnvelope(id: EventId, ts: Instant, event: TableEvent)
  object TableEventEnvelope {
    implicit val codec: Codec[TableEventEnvelope] = deriveCodec
  }
}
