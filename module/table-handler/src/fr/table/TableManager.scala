package fr.table

import cats.effect.IO
import cats.implicits.toFoldableOps
import fr.domain.Event.{TableUserEvent => TUE}
import fr.domain.TableState.Game
import fr.domain.TableState.Game.GameState
import fr.domain.TableState.Game.GameState.BetsClosed
import fr.domain.{GameId, TableId, TableState, UserId}

import java.util.UUID

trait TableManager {
  def create(tid: TableId): IO[Unit]
  def getUsers(tid: TableId): IO[List[UserId]]
  def list: IO[List[TableId]]
  def closeBets(tid: TableId): IO[Unit]
  def startGame(tid: TableId): IO[Unit]
  def setResult(tid: TableId, result: Int): IO[Unit]
}

object TableManager {
  def make(stateStorage: StateStorage, dispatcher: Dispatcher): TableManager = new TableManager {
    def getUsers(tid: TableId): IO[List[UserId]] = stateStorage.get(tid).map(_.map(_.users).getOrElse(List.empty))
    def create(tid: TableId): IO[Unit]           = stateStorage.put(tid, TableState(tid, List.empty, None))
    def list: IO[List[TableId]]                  = stateStorage.getAll
    def closeBets(tid: TableId): IO[Unit] =
      stateStorage
        .updateState(tid) { ts =>
          ts.copy(game = ts.game.map(g => g.copy(state = BetsClosed)))
        }
        .flatMap { updatedState =>
          updatedState.game match {
            case Some(game) =>
              IO.println(s"Bets closed for game ${game.id} on table $tid") *>
                updatedState.users.traverse_ { user =>
                  dispatcher.dispatch(TUE.BetsClosed(_, tid, game.id, user, _))
                }
            case None =>
              IO.println(s"Couldn't close bets. No game on table $tid")
          }
        }

    def startGame(tid: TableId): IO[Unit] =
      stateStorage
        .updateState(tid) { ts =>
          ts.copy(game = Some(Game(GameId(UUID.randomUUID()), Map.empty, GameState.BetsOpen)))
        }
        .flatMap { updatedState =>
          updatedState.game match {
            case Some(game) =>
              IO.println(s"Game ${game.id} on table $tid is starting ${updatedState.users}") *>
                updatedState.users.traverse_ { user =>
                  dispatcher.dispatch(TUE.BetsOpened(_, tid, game.id, user, _))
                }
            case None =>
              IO.println(s"Couldn't start game. No game on table $tid")
          }
        }

    override def setResult(tid: TableId, result: Int): IO[Unit] =
      stateStorage
        .updateState(tid) { ts =>
          ts.copy(game = ts.game.map(_.copy(state = GameState.GameOver(result))))
        }
        .flatMap { updatedState =>
          updatedState.game match {
            case Some(game) =>
              val winnings = WinningCalculator.winnings(game.bets, result)

              winnings.toList.traverse_ {
                case (uid, winning) =>
                  dispatcher.dispatch(TUE.BetWon(_, tid, game.id, uid, winning, _))
              } *> IO.println(s"Game ${game.id} on table $tid is finished. Result: $result") *>
                updatedState.users.traverse_ { user =>
                  dispatcher.dispatch(TUE.GameFinished(_, tid, game.id, user, result, _))
                }
            case None =>
              IO.println(s"Couldn't set result. No game on table $tid")
          }
        }
  }
}
