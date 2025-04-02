package fr.user

import cats.effect.IO
import fr.domain.Event.{OutgoingUserEvent => OUE, TableUserEvent => TUE, UserEvent => IUE, UserTableEvent => UTE}
import fr.domain.{UserAction, UserState}
import fr.user.IncomingHandler.Incoming

sealed trait IncomingHandler {
  def process(incoming: Incoming): IO[Unit]
}

object IncomingHandler {
  sealed trait Incoming
  object Incoming {
    case class UserEvent(e: IUE)  extends Incoming
    case class TableEvent(e: TUE) extends Incoming
  }

  def make(userManager: UserManager, dispatcher: Dispatcher): IncomingHandler = new IncomingHandler {
    def process(incoming: Incoming): IO[Unit] = incoming match {
      case Incoming.UserEvent(e)  => userEvents(e)
      case Incoming.TableEvent(e) => tableEvents(e)
    }

    def userEvents(e: IUE): IO[Unit] = e match {
      case IUE.SocketOpened(_, uid, ts) =>
        for {
          _     <- IO.println(s"User $uid connected at $ts")
          state <- userManager.get(uid)
          _     <- dispatcher.dispatch(OUE.BalanceUpdated(_, uid, state.balance, _))
        } yield ()
      case IUE.UserActionReceived(_, uid, action, _) =>
        action match {
          case e: UserAction.JoinedTable =>
            for {
              _ <- IO.println(s"User $uid joined table")
              _ <- userManager.updateState(uid) {
                case UserState(uid, balance, blocked, tables) => UserState(uid, balance, blocked, tables :+ e.tid)
              }
              _ <- dispatcher.dispatch(UTE.JoinedTable(_, uid, e.tid, _))
            } yield ()
          case e: UserAction.BetPlaced =>
            for {
              _ <- IO.println(s"User $uid wants to place bet, amount: ${e.bet.amount}, ")
              _ <- userManager.updateState(uid) {
                case UserState(uid, balance, blocked, tables) => UserState(uid, balance - e.bet.amount, blocked + e.bet.amount, tables)
              }
              _ <- dispatcher.dispatch(UTE.BetPlaced(_, uid, e.bet, e.tid, e.gid, _))
            } yield ()
          case e: UserAction.BetRemoved =>
            for {
              _ <- IO.println(s"User $uid wants to remove bet")
              _ <- dispatcher.dispatch(UTE.BetRemoved(_, uid, e.tid, e.gid, _))
            } yield ()
          case e: UserAction.LeftTable =>
            for {
              _ <- IO.println(s"User $uid left table")
              _ <- userManager.updateState(uid) {
                case UserState(uid, balance, blocked, tables) => UserState(uid, balance + blocked, 0, tables.filterNot(_ == e.tid))
              }
              _ <- dispatcher.dispatch(UTE.LeftTable(_, uid, e.tid, _))
            } yield ()
          case UserAction.StateRequested =>
            for {
              _     <- IO.println(s"User $uid requested state")
              state <- userManager.get(uid)
              _     <- dispatcher.dispatch(OUE.StateResponse(_, uid, state, _))
            } yield ()
        }

      case IUE.SocketClosed(_, uid, _) => IO.println(s"User $uid disconnected")
    }

    def tableEvents(e: TUE): IO[Unit] = e match {
      case TUE.BetAccepted(_, tid, gid, uid, bet, _) =>
        for {
          _ <- IO.println(s"User $uid bet accepted, amount: ${bet.amount}, table: $tid, game: $gid")
          _ <- userManager.updateState(uid) {
            case UserState(uid, balance, blocked, tables) => UserState(uid, balance, blocked - bet.amount, tables)
          }
          _ <- dispatcher.dispatch(OUE.BetAccepted(_, uid, bet, tid, gid, _))
        } yield ()
      case TUE.BetRemoved(_, tid, gid, uid, bet, _) =>
        for {
          _ <- IO.println(s"User $uid removed bet, table: $tid, game: $gid")
          _ <- userManager.updateState(uid) {
            case UserState(uid, balance, blocked, tables) => UserState(uid, balance + blocked, 0, tables)
          }
          _ <- dispatcher.dispatch(OUE.BetsRemoved(_, uid, bet, tid, gid, _))
        } yield ()
      case TUE.BetRejected(_, tid, gid, uid, bet, _) =>
        for {
          _ <- IO.println(s"User $uid bet rejected, amount: ${bet.amount}, table: $tid, game: $gid")
          _ <- userManager.updateState(uid) {
            case UserState(uid, balance, blocked, tables) => UserState(uid, balance + bet.amount, blocked - bet.amount, tables)
          }
          _ <- dispatcher.dispatch(OUE.BetRejected(_, uid, bet, tid, gid, _))
        } yield ()
      case TUE.BetsClosed(_, tid, gid, uid, _) =>
        IO.println(s"[$uid] Table $tid, game $gid bets closed") *>
          dispatcher.dispatch(OUE.BetsClosed(_, uid, tid, gid, _))
      case TUE.BetsOpened(_, tid, gid, uid, _) =>
        IO.println(s"[$uid] Table $tid, game $gid bets opened") *>
          dispatcher.dispatch(OUE.BetsOpened(_, uid, tid, gid, _))
      case TUE.JoinedTable(_, tid, uid, users, _) =>
        IO.println(s"[$uid] Users joined table $tid: $users") *>
          dispatcher.dispatch(OUE.UserJoinedTable(_, uid, users, _))
      case TUE.LeftTable(_, tid, uid, users, _) =>
        IO.println(s"[$uid] Users left table $tid: $users") *>
          dispatcher.dispatch(OUE.UserLeftTable(_, uid, users, _))
      case TUE.GameFinished(_, tid, gid, uid, result, _) =>
        IO.println(s"[$uid] Table $tid, game $gid finished with result: $result") *>
          dispatcher.dispatch(OUE.GameFinished(_, uid, tid, gid, result, _))
      case TUE.BetWon(_, tid, gid, uid, amount, _) =>
        for {
          _ <- IO.println(s"[$uid] Table $tid, game $gid won $amount")
          _ <- userManager.updateState(uid) {
            case UserState(uid, balance, blocked, tables) => UserState(uid, balance + amount, blocked, tables)
          }
          _ <- dispatcher.dispatch(OUE.BalanceUpdated(_, uid, amount, _))
        } yield ()
    }
  }
}
