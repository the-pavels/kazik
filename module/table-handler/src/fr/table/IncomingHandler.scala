package fr.table

import cats.effect.IO
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import fr.domain.user.{UserTableAction => UTA}

sealed trait IncomingHandler[F[_]] {
  def handleUser(e: UserTableActionEnvelope): F[Unit]
}

object IncomingHandler {
  def make(tableManager: TableManager, dispatcher: Dispatcher): IncomingHandler[IO] = new IncomingHandler[IO] {
    def handleUser(e: UserTableActionEnvelope): IO[Unit] = {
      val result = e.event match {
        case UTA.JoinTable(tid)          => tableManager.joinTable(tid, e.uid)
        case UTA.LeaveTable(tid)         => tableManager.leaveTable(tid, e.uid)
        case UTA.PlaceBet(tid, gid, bet) => tableManager.placeBet(tid, gid, e.uid, bet)
        case UTA.RemoveBets(tid, gid)    => tableManager.removeBet(tid, gid, e.uid)
      }

      result
        .flatMap {
          case result => dispatcher.dispatchAll(result.events) *> IO.println(s"User ${e.uid} performed action ${e.event} on table ${e.event.tid}")
        }
        .handleErrorWith { error =>
          IO.println(s"Error handling user event: ${error.getMessage}")
        }
    }
  }
}
