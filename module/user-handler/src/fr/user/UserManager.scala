package fr.user

import cats.effect.IO
import fr.domain.{UserId, UserState}
import fr.redis.Transactor

trait UserManager {
  def get(uid: UserId): IO[UserState]
  def updateState(uid: UserId)(f: UserState => UserState): IO[UserState]
  def updateStateF(uid: UserId)(f: UserState => IO[UserState]): IO[UserState]
}

object UserManager {
  def make(stateStorage: StateStorage, transactor: Transactor): UserManager = new UserManager {
    override def updateState(uid: UserId)(f: UserState => UserState): IO[UserState] = updateStateF(uid)(f.andThen(IO.pure))

    override def updateStateF(uid: UserId)(f: UserState => IO[UserState]): IO[UserState] = transactor.withTransaction(uid.asLock) {
      for {
        state        <- get(uid)
        updatedState <- f(state)
        _            <- stateStorage.put(uid, updatedState)
      } yield updatedState
    }

    override def get(uid: UserId): IO[UserState] = stateStorage.get(uid).map(_.getOrElse(UserState.default(uid)))
  }
}
