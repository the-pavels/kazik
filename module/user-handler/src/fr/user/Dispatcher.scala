package fr.user

import cats.effect.IO
import cats.syntax.all._
import fr.domain.user.UserEvent.UserEventEnvelope
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import fr.domain.{EventId, UserId}
import fr.user.UserManager.Result

sealed trait Dispatcher {
  def dispatch(r: Result): IO[Unit]
}

object Dispatcher {
  private type UserOutgoingEventBroadcast = UserId => UserEventEnvelope => IO[Unit]
  private type UserTableEventBroadcast    = UserTableActionEnvelope => IO[Unit]

  def make(userBroadcast: UserOutgoingEventBroadcast, userTableBroadcast: UserTableEventBroadcast): Dispatcher = new Dispatcher {
    private def newEvent = (IO.randomUUID.map(EventId(_)), IO.realTimeInstant)

    def dispatch(r: Result): IO[Unit] = {
      r.actions.traverse { action =>
        newEvent.flatMapN {
          case (eid, ts) => userTableBroadcast(UserTableActionEnvelope(eid, r.state.id, action, ts))
        }
      } *> r.events.traverse { event =>
        newEvent.flatMapN {
          case (eid, ts) => userBroadcast(r.state.id)(UserEventEnvelope(eid, r.state.id, event, ts))
        }
      }.void
    }
  }
}
