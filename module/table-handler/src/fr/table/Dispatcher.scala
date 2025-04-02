package fr.table

import cats.effect.IO
import cats.implicits.catsSyntaxTuple2Semigroupal
import fr.domain.Event.EventId
import fr.domain.Event.{TableUserEvent => TUE}

import java.time.Instant

trait Dispatcher {
  def dispatch(f: (EventId, Instant) => TUE): IO[Unit]
}

object Dispatcher {
  type UserBroadcast = TUE => IO[Unit]

  def make(userBroadcast: UserBroadcast): Dispatcher = new Dispatcher {
    private def newEvent = (IO.randomUUID.map(EventId(_)), IO.realTimeInstant)

    def dispatch(f: (EventId, Instant) => TUE): IO[Unit] =
      newEvent
        .mapN(f)
        .flatMap(userBroadcast)
  }
}
