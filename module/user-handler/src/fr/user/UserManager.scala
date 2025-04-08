package fr.user

import cats.effect.IO
import fr.domain.{UserId, UserState}
import fr.redis.StateStorage

trait UserManager {
  def get(uid: UserId): IO[UserState]
  def updateState(uid: UserId)(f: UserState => UserState): IO[UserState]
}

object UserManager {
  def make(stateStorage: StateStorage[UserId, UserState]): UserManager = new UserManager {
    override def updateState(uid: UserId)(f: UserState => UserState): IO[UserState] = stateStorage.updateState(uid)(f)

    override def get(uid: UserId): IO[UserState] = stateStorage.get(uid).map(_.getOrElse(UserState.empty(uid)))
  }
}
