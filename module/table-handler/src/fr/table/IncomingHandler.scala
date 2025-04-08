package fr.table

import cats.effect.IO
import cats.implicits.toTraverseOps
import fr.domain.Event.{TableUserEvent => TUE, UserTableEvent => UTE}
import fr.domain.TableState

sealed trait IncomingHandler[F[_]] {
  def handleUser(e: UTE): F[Unit]
}

object IncomingHandler {
  def make(tableManager: TableManager, dispatcher: Dispatcher) = new IncomingHandler[IO] {
    def handleUser(e: UTE): IO[Unit] =
      e match {
        case UTE.JoinedTable(_, uid, tid, _) =>
          for {
            state <- tableManager.updateState(tid) {
              case TableState(tid, users, game) => TableState(tid, users :+ uid, game)
            }

            _ <- state.users.filterNot(_ == uid).traverse { sittingUser =>
              dispatcher.dispatch(TUE.JoinedTable(_, tid, sittingUser, List(uid), _))
            }
            _ <- dispatcher.dispatch(TUE.JoinedTable(_, tid, uid, state.users, _))

            _ <- IO.println(s"User $uid joined table $tid")
          } yield ()
        case UTE.LeftTable(_, uid, tid, _) =>
          for {
            state <- tableManager.updateState(tid) {
              case TableState(tid, users, game) => TableState(tid, users.filterNot(_ == uid), game)
            }

            _ <- state.users.traverse { sittingUser =>
              dispatcher.dispatch(TUE.LeftTable(_, tid, sittingUser, List(uid), _))
            }
            _ <- dispatcher.dispatch(TUE.LeftTable(_, tid, uid, state.users, _))

            _ <- IO.println(s"User $uid left table $tid")
          } yield ()
        case UTE.BetPlaced(_, uid, bet, tid, gid, _) =>
          for {
            _ <- tableManager.updateState(tid) {
              case s @ TableState(tid, users, Some(game)) if gid == game.id && game.betsOpen =>
                if (game.betExists(uid, bet.id)) s
                else {
                  val userBets    = game.bets.getOrElse(uid, List.empty) :+ bet
                  val updatedGame = game.copy(bets = game.bets + (uid -> userBets))
                  TableState(tid, users, Some(updatedGame))
                }
              case s @ TableState(_, _, Some(game)) =>
                IO.println(s"User $uid placed bet, but it got rejected: ${game.state}") *>
                  dispatcher.dispatch(TUE.BetRejected(_, tid, gid, uid, bet, _)).as(s)
              case s => IO.pure(s)
            }
          } yield ()
        case UTE.BetRemoved(_, uid, tid, gid, _) =>
          for {
            _ <- tableManager.updateStateOpt(tid) {
              case TableState(tid, users, Some(game)) =>
                val bet          = game.bets.getOrElse(uid, List.empty)
                val updatedGame  = game.copy(bets = game.bets - uid)
                val updatedState = TableState(tid, users, Some(updatedGame))
                dispatcher.dispatch(TUE.BetRemoved(_, tid, gid, uid, bet, _)).as(updatedState)
              case _ => ???
            }
            _ <- IO.println(s"User $uid removed bet, table: $tid, game: $gid")
          } yield ()
      }
  }
}
