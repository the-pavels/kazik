package fr.user

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fr.domain.table.{TableEvent => TE}
import fr.domain.table.TableEvent.{TableEventEnvelope => TEE}
import fr.domain.user.{UserEvent, UserInput, UserAction => UA}
import fr.user.UserManager.Result

import java.time.Instant

sealed trait IncomingHandler {
  def fromTable(e: TEE): IO[Result]
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
          case e: UserInput.RemoveBets => userManager.removeBets(uid, e.tid, e.gid)
          case e: UserInput.LeaveTable => userManager.leaveTable(uid, e.tid)
          case UserInput.RequestState  => userManager.get(uid).map(state => Result(state, List(UserEvent.UserStateProvided(state))))
        }
    }

    override def fromTable(e: TEE): IO[Result] = e.event match {
      case TE.BetAccepted(tid, gid, uid, bet)     => userManager.betAccepted(uid, tid, gid, bet)
      case TE.BetRemoved(tid, gid, uid, bets)     => userManager.betsRemoved(uid, tid, gid, bets)
      case TE.BetRejected(tid, gid, uid, bet)     => userManager.betRejected(uid, tid, gid, bet)
      case TE.BetsClosed(tid, gid, uid)           => userManager.betsClosed(uid, tid, gid)
      case TE.BetsOpened(tid, gid, uid)           => userManager.betsOpened(uid, tid, gid)
      case TE.JoinedTable(tid, uid, users)        => userManager.joinedTable(uid, tid, users)
      case TE.LeftTable(tid, uid, users)          => userManager.leftTable(uid, tid, users)
      case TE.GameFinished(tid, gid, uid, result) => userManager.gameFinished(uid, tid, gid, result)
      case TE.BetWon(tid, gid, uid, amount)       => userManager.betWon(uid, tid, gid, amount)
    }
  }
}
