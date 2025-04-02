package fr.domain

import cats.effect.IO
import cats.syntax.all._
import cats.{Eq, Show}
import fr.domain.Event.EventId
import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}
import io.estatico.newtype.macros.newtype

import java.time.Instant
import java.util.UUID

sealed trait Event {
  def id: EventId
  def ts: Instant
}

// todo that's a mess
object Event {
  @newtype
  case class EventId(value: UUID)

  object EventId {
    implicit val encoder: Encoder[EventId] =
      Encoder.encodeString.contramap[EventId](_.toString())
    implicit val decoder: Decoder[EventId] =
      Decoder.decodeString.map(s => EventId(UUID.fromString(s)))
    implicit val eqv: Eq[EventId]    = Eq.fromUniversalEquals
    implicit val show: Show[EventId] = Show.fromToString
  }

  sealed trait UserEvent extends Event {
    def uid: UserId
  }
  object UserEvent {
    case class SocketOpened(id: EventId, uid: UserId, ts: Instant)                           extends UserEvent
    case class UserActionReceived(id: EventId, uid: UserId, action: UserAction, ts: Instant) extends UserEvent
    case class SocketClosed(id: EventId, uid: UserId, ts: Instant)                           extends UserEvent

    implicit val codec: Codec[UserEvent] = deriveCodec
  }

  sealed trait OutgoingUserEvent extends Event {
    def uid: UserId
  }

  sealed trait UserTableEvent extends OutgoingUserEvent {
    def uid: UserId
    def tid: TableId
  }
  object UserTableEvent {
    case class JoinedTable(id: EventId, uid: UserId, tid: TableId, ts: Instant)                      extends UserTableEvent
    case class BetPlaced(id: EventId, uid: UserId, bet: Bet, tid: TableId, gid: GameId, ts: Instant) extends UserTableEvent
    case class BetRemoved(id: EventId, uid: UserId, tid: TableId, gid: GameId, ts: Instant)          extends UserTableEvent
    case class LeftTable(id: EventId, uid: UserId, tid: TableId, ts: Instant)                        extends UserTableEvent

    implicit val codec: Codec[UserTableEvent] = deriveCodec
  }

  object OutgoingUserEvent {
    case class BalanceUpdated(id: EventId, uid: UserId, balance: BigDecimal, ts: Instant)                    extends OutgoingUserEvent
    case class BetAccepted(id: EventId, uid: UserId, bet: Bet, tid: TableId, gid: GameId, ts: Instant)       extends OutgoingUserEvent
    case class BetRejected(id: EventId, uid: UserId, bet: Bet, tid: TableId, gid: GameId, ts: Instant)       extends OutgoingUserEvent
    case class BetsRemoved(id: EventId, uid: UserId, bet: List[Bet], tid: TableId, gid: GameId, ts: Instant) extends OutgoingUserEvent
    case class BetsOpened(id: EventId, uid: UserId, tid: TableId, gid: GameId, ts: Instant)                  extends OutgoingUserEvent
    case class BetsClosed(id: EventId, uid: UserId, tid: TableId, gid: GameId, ts: Instant)                  extends OutgoingUserEvent
    case class StateResponse(id: EventId, uid: UserId, state: UserState, ts: Instant)                        extends OutgoingUserEvent
    case class UserJoinedTable(id: EventId, uid: UserId, joined: List[UserId], ts: Instant)                  extends OutgoingUserEvent
    case class UserLeftTable(id: EventId, uid: UserId, left: List[UserId], ts: Instant)                      extends OutgoingUserEvent
    case class GameFinished(id: EventId, uid: UserId, tid: TableId, gid: GameId, result: Int, ts: Instant)   extends OutgoingUserEvent

    def make(f: (EventId, Instant) => OutgoingUserEvent): IO[OutgoingUserEvent] = (IO.randomUUID.map(EventId(_)), IO.realTimeInstant).mapN(f)

    implicit val codec: Codec[OutgoingUserEvent] = deriveCodec
  }

  sealed trait TableUserEvent extends Event {
    def tid: TableId
    def uid: UserId
  }
  object TableUserEvent {
    case class BetAccepted(id: EventId, tid: TableId, gid: GameId, uid: UserId, bet: Bet, ts: Instant)      extends TableUserEvent
    case class BetRejected(id: EventId, tid: TableId, gid: GameId, uid: UserId, bet: Bet, ts: Instant)      extends TableUserEvent
    case class BetRemoved(id: EventId, tid: TableId, gid: GameId, uid: UserId, bet: List[Bet], ts: Instant) extends TableUserEvent
    case class BetsOpened(id: EventId, tid: TableId, gid: GameId, uid: UserId, ts: Instant)                 extends TableUserEvent
    case class BetsClosed(id: EventId, tid: TableId, gid: GameId, uid: UserId, ts: Instant)                 extends TableUserEvent
    case class BetWon(id: EventId, tid: TableId, gid: GameId, uid: UserId, amount: Int, ts: Instant)        extends TableUserEvent
    case class GameFinished(id: EventId, tid: TableId, gid: GameId, uid: UserId, result: Int, ts: Instant)  extends TableUserEvent
    case class JoinedTable(id: EventId, tid: TableId, uid: UserId, users: List[UserId], ts: Instant)        extends TableUserEvent
    case class LeftTable(id: EventId, tid: TableId, uid: UserId, users: List[UserId], ts: Instant)          extends TableUserEvent

    implicit val codec: Codec[TableUserEvent] = deriveCodec
  }

}
