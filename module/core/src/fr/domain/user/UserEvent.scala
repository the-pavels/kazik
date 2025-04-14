package fr.domain.user

import cats.effect.IO
import cats.implicits.catsSyntaxTuple2Semigroupal
import fr.domain.{EventId, GameId, TableId, UserId}
import fr.domain.game.roulette.Bet
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant

sealed trait UserEvent

object UserEvent {
  case class BalanceUpdated(balance: BigDecimal)                    extends UserEvent
  case class BetAccepted(tid: TableId, gid: GameId, bet: Bet)       extends UserEvent
  case class BetRejected(tid: TableId, gid: GameId, bet: Bet)       extends UserEvent
  case class BetsRemoved(tid: TableId, gid: GameId, bet: List[Bet]) extends UserEvent
  case class BetsOpened(tid: TableId, gid: GameId)                  extends UserEvent
  case class BetsClosed(tid: TableId, gid: GameId)                  extends UserEvent
  case class UserStateProvided(state: UserState)                    extends UserEvent
  case class UsersJoinedTable(joined: List[UserId])                 extends UserEvent
  case class UsersLeftTable(left: List[UserId])                     extends UserEvent
  case class GameFinished(tid: TableId, gid: GameId, result: Int)   extends UserEvent

  def make(f: (EventId, Instant) => UserEvent): IO[UserEvent] = (IO.randomUUID.map(EventId(_)), IO.realTimeInstant).mapN(f)

  implicit val codec: Codec[UserEvent] = deriveCodec

  case class UserEventEnvelope(id: EventId, uid: UserId, event: UserEvent, ts: Instant)
  object UserEventEnvelope {
    implicit val codec: Codec[UserEventEnvelope] = deriveCodec
  }
}
