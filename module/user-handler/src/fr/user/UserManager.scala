package fr.user

import cats.effect.IO
import fr.adapter.redis.StateStorage
import fr.domain.game.roulette.Bet
import fr.domain.user.{UserEvent, UserState, UserTableAction}
import fr.domain.{GameId, TableId, UserId}
import fr.user.UserManager.Result

trait UserManager {
  def get(uid: UserId): IO[UserState]

  def joinTable(uid: UserId, tid: TableId): IO[Result]
  def placeBet(uid: UserId, bet: Bet, tid: TableId, gid: GameId): IO[Result]
  def removeBet(uid: UserId, tid: TableId, gid: GameId): IO[Result]
  def leaveTable(uid: UserId, tid: TableId): IO[Result]

}

object UserManager {
  case class Result(state: UserState, events: List[UserEvent] = List.empty, actions: List[UserTableAction] = List.empty)

  def make(stateStorage: StateStorage[UserId, UserState, Result]): UserManager = new UserManager {
    override def get(uid: UserId): IO[UserState] = stateStorage.get(uid).map(_.getOrElse(UserState.empty(uid)))
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
          val newState = UserState(uid, balance - bet.amount, blocked + bet.amount, tables)
          newState -> Result(newState, actions = List(UserTableAction.PlaceBet(tid, gid, bet)))
        }
    }

    override def removeBet(uid: UserId, tid: TableId, gid: GameId): IO[Result] = ???

    override def leaveTable(uid: UserId, tid: TableId): IO[Result] = ???
  }
}
