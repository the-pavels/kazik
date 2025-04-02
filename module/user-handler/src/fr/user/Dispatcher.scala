package fr.user

import cats.effect.IO
import cats.implicits.catsSyntaxTuple2Semigroupal
import fr.domain.Event.{EventId, OutgoingUserEvent => OUE, UserTableEvent => UTE}
import fr.domain.UserId

import java.time.Instant

sealed trait Dispatcher {
  def dispatch(f: (EventId, Instant) => OUE): IO[Unit]
}

object Dispatcher {
  private type UserOutgoingEventBroadcast = UserId => OUE => IO[Unit]
  private type UserTableEventBroadcast    = UTE => IO[Unit]

  def make(userBroadcast: UserOutgoingEventBroadcast, userTableBroadcast: UserTableEventBroadcast): Dispatcher = new Dispatcher {
    private def newEvent = (IO.randomUUID.map(EventId(_)), IO.realTimeInstant)

    def dispatch(f: (EventId, Instant) => OUE): IO[Unit] =
      newEvent
        .mapN(f)
        .flatMap {
          case e: UTE => userTableBroadcast(e)
          case e      => userBroadcast(e.uid)(e)
        }
  }
}
