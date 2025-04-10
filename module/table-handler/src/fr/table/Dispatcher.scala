package fr.table

import cats.effect.IO
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFoldableOps}
import fr.domain.EventId
import fr.domain.table.TableEvent
import fr.domain.table.TableEvent.TableEventEnvelope

import java.time.Instant

trait Dispatcher {
  def dispatchAll(e: List[TableEvent]): IO[Unit]
}

object Dispatcher {
  type UserBroadcast = TableEventEnvelope => IO[Unit]

  def make(userBroadcast: UserBroadcast): Dispatcher = new Dispatcher {
    private def newEvent: (IO[EventId], IO[Instant]) = (IO.randomUUID.map(EventId(_)), IO.realTimeInstant)

    def dispatch(e: TableEvent): IO[Unit] =
      newEvent
        .mapN {
          case (id, ts) => TableEventEnvelope(id, ts, e)
        }
        .flatMap(userBroadcast)

    override def dispatchAll(es: List[TableEvent]): IO[Unit] = es.traverse_(dispatch)
  }
}
