package fr.user

import cats.effect.IO
import fr.adapter.redis.StateStorage
import fr.domain.game.roulette.Bet
import fr.domain.user.{UserEvent, UserState, UserTableAction}
import fr.domain.{GameId, TableId, UserId}
import fr.user.UserManager.Result
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

trait UserManager {
  def get(uid: UserId): IO[UserState]

  def joinTable(uid: UserId, tid: TableId): IO[Result]
  def placeBet(uid: UserId, bet: Bet, tid: TableId, gid: GameId): IO[Result]
  def removeBets(uid: UserId, tid: TableId, gid: GameId): IO[Result]
  def leaveTable(uid: UserId, tid: TableId): IO[Result]

  def betAccepted(uid: UserId, tid: TableId, gid: GameId, bet: Bet): IO[Result]
  def betsRemoved(uid: UserId, tid: TableId, gid: GameId, bets: List[Bet]): IO[Result]
  def betRejected(uid: UserId, tid: TableId, gid: GameId, bet: Bet): IO[Result]
  def betsClosed(uid: UserId, tid: TableId, gid: GameId): IO[Result]
  def betsOpened(uid: UserId, tid: TableId, gid: GameId): IO[Result]
  def joinedTable(uid: UserId, tid: TableId, users: List[UserId]): IO[Result]
  def leftTable(uid: UserId, tid: TableId, users: List[UserId]): IO[Result]
  def gameFinished(uid: UserId, tid: TableId, gid: GameId, result: Int): IO[Result]
  def betWon(uid: UserId, tid: TableId, gid: GameId, amount: BigDecimal): IO[Result]
}

object UserManager {
  case class Result(state: UserState, events: List[UserEvent] = List.empty, actions: List[UserTableAction] = List.empty)
  object Result {
    implicit val codec: Codec[Result] = deriveCodec[Result]
  }

  def make(stateStorage: StateStorage[UserId, UserState, Result]): UserManager = new UserManager {
    override def get(uid: UserId): IO[UserState] = stateStorage.get(uid)
    override def joinTable(uid: UserId, tid: TableId): IO[Result] =
      stateStorage.updateState(uid) {
        case UserState(uid, balance, blocked, tables) =>
          val newState = UserState(uid, balance, blocked, tables :+ tid)
          newState -> Result(newState, List.empty, List(UserTableAction.JoinTable(tid)))
      }

    override def placeBet(uid: UserId, bet: Bet, tid: TableId, gid: GameId): IO[Result] = stateStorage.updateState(uid) {
      case s @ UserState(uid, balance, blocked, tables) =>
        if (balance < bet.amount) {
          s -> Result(s, List(UserEvent.BetRejected(tid, gid, bet)))
        } else {
          val newBlocked = blocked.getOrElse(tid, BigDecimal(0)) + bet.amount
          val newState   = UserState(uid, balance - bet.amount, blocked + (tid -> newBlocked), tables)
          newState -> Result(newState, actions = List(UserTableAction.PlaceBet(tid, gid, bet)))
        }
    }

    override def removeBets(uid: UserId, tid: TableId, gid: GameId): IO[Result] = stateStorage.updateState(uid) { s =>
      s -> Result(s, actions = List(UserTableAction.RemoveBets(tid, gid)))
    }

    override def leaveTable(uid: UserId, tid: TableId): IO[Result] = stateStorage.updateState(uid) {
      case UserState(uid, balance, blocked, tables) =>
        val newState = UserState(uid, balance, blocked, tables.filterNot(_ == tid))
        newState -> Result(newState, List.empty, List(UserTableAction.LeaveTable(tid)))
    }

    override def betAccepted(uid: UserId, tid: TableId, gid: GameId, bet: Bet): IO[Result] = stateStorage.updateState(uid) { state =>
      val newBlocked = state.blockedBalance.getOrElse(tid, BigDecimal(0)) - bet.amount
      val newState   = state.copy(blockedBalance = state.blockedBalance + (tid -> newBlocked))
      newState -> Result(newState, List(UserEvent.BetAccepted(tid, gid, bet), UserEvent.BalanceUpdated(newState.balance)))
    }

    override def betsRemoved(uid: UserId, tid: TableId, gid: GameId, bets: List[Bet]): IO[Result] = stateStorage.updateState(uid) { state =>
      val blockedBalance = state.blockedBalance.getOrElse(tid, BigDecimal(0))
      val newState       = state.copy(balance = blockedBalance + state.balance, blockedBalance = state.blockedBalance - tid)
      newState -> Result(newState, List(UserEvent.BetsRemoved(tid, gid, bets), UserEvent.BalanceUpdated(newState.balance)))
    }

    override def betRejected(uid: UserId, tid: TableId, gid: GameId, bet: Bet): IO[Result] = stateStorage.updateState(uid) { state =>
      val blockedBalance = state.blockedBalance.getOrElse(tid, BigDecimal(0)) - bet.amount
      val newState       = state.copy(balance = state.balance + bet.amount, blockedBalance = state.blockedBalance + (tid -> blockedBalance))
      newState -> Result(newState, List(UserEvent.BetRejected(tid, gid, bet)))
    }

    override def betsClosed(uid: UserId, tid: TableId, gid: GameId): IO[Result] = stateStorage.get(uid).map { state =>
      Result(state, List(UserEvent.BetsClosed(tid, gid)))
    }

    override def betsOpened(uid: UserId, tid: TableId, gid: GameId): IO[Result] = stateStorage.get(uid).map { state =>
      Result(state, List(UserEvent.BetsOpened(tid, gid)))
    }

    override def joinedTable(uid: UserId, tid: TableId, users: List[UserId]): IO[Result] = stateStorage.get(uid).map { state =>
      Result(state, List(UserEvent.UsersJoinedTable(users)))
    }

    override def leftTable(uid: UserId, tid: TableId, users: List[UserId]): IO[Result] = stateStorage.get(uid).map { state =>
      Result(state, List(UserEvent.UsersLeftTable(users)))
    }

    override def gameFinished(uid: UserId, tid: TableId, gid: GameId, result: Int): IO[Result] = stateStorage.get(uid).map { state =>
      Result(state, List(UserEvent.GameFinished(tid, gid, result)))
    }

    override def betWon(uid: UserId, tid: TableId, gid: GameId, amount: BigDecimal): IO[Result] = stateStorage.updateState(uid) { state =>
      val newState = state.copy(balance = state.balance + amount)
      newState -> Result(newState, List(UserEvent.BalanceUpdated(newState.balance)))
    }
  }
}
