package fr.user

import cats.effect.IO
import fr.domain.table.{TableEvent => TE}
import fr.domain.user.{UserEvent, UserInput, UserAction => UA}
import fr.user.UserManager.Result

sealed trait IncomingHandler {
  def fromTable(e: TE): IO[Result]
  def fromUser(e: UA): IO[Result]
}

object IncomingHandler {
  def make(userManager: UserManager): IncomingHandler = new IncomingHandler {
    override def fromUser(e: UA): IO[Result] = e match {
      case UA.SocketOpened(_, uid, _) => userManager.get(uid).map(Result(_))
      case UA.SocketClosed(_, uid, _) => userManager.get(uid).map(Result(_))
      case UA.UserActionReceived(_, uid, action, _) =>
        action match {
          case e: UserInput.JoinTable  => userManager.joinTable(uid, e.tid)
          case e: UserInput.PlaceBet   => userManager.placeBet(uid, e.bet, e.tid, e.gid)
          case e: UserInput.RemoveBets => userManager.removeBet(uid, e.tid, e.gid)
          case e: UserInput.LeaveTable => userManager.leaveTable(uid, e.tid)
          case UserInput.RequestState  => userManager.get(uid).map(state => Result(state, List(UserEvent.UserStateProvided(state))))
        }
    }

    override def fromTable(e: TE): IO[Result] = ???
//      e match {
//      case TE.BetAccepted(tid, gid, uid, bet) =>
//        for {
//          _ <- IO.println(s"User $uid bet accepted, amount: ${bet.amount}, table: $tid, game: $gid")
//          state <- userManager.updateState(uid) {
//            case UserState(uid, balance, blocked, tables) => UserState(uid, balance, blocked - bet.amount, tables)
//          }
//          _ <- dispatcher.dispatch(OUA.BetAccepted(_, uid, bet, tid, gid, _))
//          _ <- dispatcher.dispatch(OUA.BalanceUpdated(_, uid, state.balance, _))
//        } yield ()
//      case TE.BetRemoved(tid, gid, uid, bet) =>
//        for {
//          _ <- IO.println(s"User $uid removed bet, table: $tid, game: $gid")
//          _ <- userManager.updateState(uid) {
//            case UserState(uid, balance, blocked, tables) => UserState(uid, balance + blocked, 0, tables)
//          }
//          _ <- dispatcher.dispatch(OUA.BetsRemoved(_, uid, bet, tid, gid, _))
//        } yield ()
//      case TE.BetRejected(tid, gid, uid, bet) =>
//        for {
//          _ <- IO.println(s"User $uid bet rejected, amount: ${bet.amount}, table: $tid, game: $gid")
//          _ <- userManager.updateState(uid) {
//            case UserState(uid, balance, blocked, tables) => UserState(uid, balance + bet.amount, blocked - bet.amount, tables)
//          }
//          _ <- dispatcher.dispatch(OUA.BetRejected(_, uid, bet, tid, gid, _))
//        } yield ()
//      case TE.BetsClosed(tid, gid, uid) =>
//        IO.println(s"[$uid] Table $tid, game $gid bets closed") *>
//          dispatcher.dispatch(OUA.BetsClosed(_, uid, tid, gid, _))
//      case TE.BetsOpened(tid, gid, uid) =>
//        IO.println(s"[$uid] Table $tid, game $gid bets opened") *>
//          dispatcher.dispatch(OUA.BetsOpened(_, uid, tid, gid, _))
//      case TE.JoinedTable(tid, uid, users) =>
//        IO.println(s"[$uid] Users joined table $tid: $users") *>
//          dispatcher.dispatch(OUA.UserJoinedTable(_, uid, users, _))
//      case TE.LeftTable(tid, uid, users) =>
//        IO.println(s"[$uid] Users left table $tid: $users") *>
//          dispatcher.dispatch(OUA.UserLeftTable(_, uid, users, _))
//      case TE.GameFinished(tid, gid, uid, result) =>
//        IO.println(s"[$uid] Table $tid, game $gid finished with result: $result") *>
//          dispatcher.dispatch(OUA.GameFinished(_, uid, tid, gid, result, _))
//      case TE.BetWon(tid, gid, uid, amount) =>
//        for {
//          _ <- IO.println(s"[$uid] Table $tid, game $gid won $amount")
//          state <- userManager.updateState(uid) {
//            case UserState(uid, balance, blocked, tables) => UserState(uid, balance + amount, blocked, tables)
//          }
//          _ <- dispatcher.dispatch(OUA.BalanceUpdated(_, uid, state.balance, _))
//        } yield ()
//    }
  }
}
