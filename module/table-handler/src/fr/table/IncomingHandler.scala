package fr.table

import cats.effect.IO
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import fr.domain.user.{UserTableAction => UTA}

import java.time.Instant

sealed trait IncomingHandler[F[_]] {
  def handleUser(e: UserTableActionEnvelope): F[Unit]
}

object IncomingHandler {
  def make(tableManager: TableManager, dispatcher: Dispatcher): IncomingHandler[IO] = new IncomingHandler[IO] {
    def handleUser(e: UserTableActionEnvelope): IO[Unit] = {
      val result = e.action match {
        case UTA.JoinTable(tid) =>
          IO(Instant.now).flatMap { ts =>
            IO.println(s"[$ts] [$e.uid] OLOLO Joined table $tid") *>
              tableManager.joinTable(tid, e.uid)
          }
        case UTA.LeaveTable(tid)         => tableManager.leaveTable(tid, e.uid)
        case UTA.PlaceBet(tid, gid, bet) => tableManager.placeBet(tid, gid, e.uid, bet)
        case UTA.RemoveBets(tid, gid)    => tableManager.removeBet(tid, gid, e.uid)
      }

      result
        .flatMap { result =>
          dispatcher.dispatchAll(result.events) *> IO.println(s"User ${e.uid} performed action ${e.action} on table ${e.action.tid}")
        }
        .handleErrorWith { error =>
          IO.println(s"Error handling user event: ${error.getMessage}")
        }
    }
  }
}
