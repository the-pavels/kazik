package fr.table

import cats.effect.IO
import fr.adapter.redis.StateStorage
import fr.domain.game.roulette.Game.GameState
import fr.domain.game.roulette.Game.GameState.BetsClosed
import fr.domain.game.roulette.{Bet, Game}
import fr.domain.table.{TableState, TableEvent => TE}
import fr.domain.{GameId, TableId, UserId}
import fr.table.TableManager.Result
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.util.UUID

trait TableManager {
  def create(tid: TableId): IO[Unit]
  def getUsers(tid: TableId): IO[Set[UserId]]
  def closeBets(tid: TableId): IO[Result]
  def startGame(tid: TableId): IO[Result]
  def setResult(tid: TableId, result: Int): IO[Result]

  def joinTable(tid: TableId, uid: UserId): IO[Result]
  def leaveTable(tid: TableId, uid: UserId): IO[Result]
  def placeBet(tid: TableId, gid: GameId, uid: UserId, bet: Bet): IO[Result]
  def removeBet(tid: TableId, gid: GameId, uid: UserId): IO[Result]

  def joinTable2(tid: TableId, uid: Set[UserId]): IO[Result]
  def leaveTable2(tid: TableId, uid: Set[UserId]): IO[Result]
  def placeBets(tid: TableId, uid: List[(UserId, GameId, Bet)]): IO[Result]
  def removeBets(tid: TableId, uid: List[(UserId, GameId)]): IO[Result]
}

object TableManager {
  case class Result(state: TableState, events: List[TE] = List.empty)

  def make(stateStorage: StateStorage[TableId, TableState, Result]): TableManager = new TableManager {
    override def getUsers(tid: TableId): IO[Set[UserId]] = stateStorage.get(tid).map(_.users)
    override def create(tid: TableId): IO[Unit]          = stateStorage.put(tid, TableState(tid, Set.empty, None))
    override def closeBets(tid: TableId): IO[Result] =
      stateStorage.updateState(tid) {
        case s @ TableState(_, users, Some(game)) =>
          val updatedGame  = game.copy(state = BetsClosed)
          val updatedState = s.copy(game = Some(updatedGame))
          val events       = users.toList.map(user => TE.BetsClosed(tid, game.id, user))
          updatedState -> Result(updatedState, events)
        case s => s -> Result(s, List.empty)
      }

    override def startGame(tid: TableId): IO[Result] =
      stateStorage
        .updateState(tid) { ts =>
          val newGame      = Game(GameId(UUID.randomUUID()), Map.empty, GameState.BetsOpen)
          val updatedState = ts.copy(game = Some(newGame))
          val events       = ts.users.toList.map(user => TE.BetsOpened(tid, newGame.id, user))
          updatedState -> Result(updatedState, events)
        }

    override def setResult(tid: TableId, result: Int): IO[Result] =
      stateStorage
        .updateState(tid) {
          case s @ TableState(id, users, Some(game)) =>
            val updatedState = s.copy(game = Some(game.copy(state = GameState.GameOver(result))))
            val winnings     = WinningCalculator.winnings(game.bets, result)

            val betWonEvents = winnings.toList.map {
              case (uid, winning) => TE.BetWon(id, game.id, uid, winning)
            }
            val gameFinishedEvents = users.map { user =>
              TE.GameFinished(id, game.id, user, result)
            }
            updatedState -> Result(updatedState, betWonEvents ++ gameFinishedEvents)
          case s => s -> Result(s, List.empty)
        }

    override def joinTable(tid: TableId, uid: UserId): IO[Result] =
      stateStorage
        .updateState(tid) { s: TableState =>
          val updatedState = s.copy(users = s.users + uid)
          val events = updatedState.users.toList.map { sittingUser =>
            TE.JoinedTable(tid, sittingUser, Set(uid))
          }

          updatedState -> Result(updatedState, events)
        }

    override def leaveTable(tid: TableId, uid: UserId): IO[Result] =
      stateStorage
        .updateState(tid) { s: TableState =>
          val updatedState = s.copy(users = s.users - uid)
          val events = updatedState.users.toList.map { sittingUser =>
            TE.LeftTable(tid, sittingUser, Set(uid))
          }

          updatedState -> Result(updatedState, events)
        }

    override def placeBet(tid: TableId, gid: GameId, uid: UserId, bet: Bet): IO[Result] =
      stateStorage
        .updateState(tid) {
          case s @ TableState(tid, users, Some(game)) if gid == game.id && game.betsOpen =>
            if (game.betExists(uid, bet.id)) s -> Result(s)
            else {
              val userBets     = game.bets.getOrElse(uid, List.empty) :+ bet
              val updatedGame  = game.copy(bets = game.bets + (uid -> userBets))
              val updatedState = TableState(tid, users, Some(updatedGame))
              val event        = TE.BetAccepted(tid, gid, uid, bet)

              updatedState -> Result(updatedState, List(event))
            }
          case s => s -> Result(s, List(TE.BetRejected(tid, gid, uid, bet)))
        }

    override def removeBet(tid: TableId, gid: GameId, uid: UserId): IO[Result] =
      stateStorage
        .updateState(tid) {
          case TableState(tid, users, Some(game)) if gid == game.id =>
            val userBets     = game.bets.getOrElse(uid, List.empty)
            val updatedGame  = game.copy(bets = game.bets - uid)
            val updatedState = TableState(tid, users, Some(updatedGame))
            val event        = TE.BetRemoved(tid, gid, uid, userBets)

            updatedState -> Result(updatedState, List(event))
          case s => s -> Result(s)
        }

    override def joinTable2(tid: TableId, uid: Set[UserId]): IO[Result] =
      stateStorage
        .updateState(tid) { s: TableState =>
          val updatedState = s.copy(users = s.users ++ uid)
          val events = updatedState.users.toList.map { sittingUser =>
            TE.JoinedTable(tid, sittingUser, uid)
          }

          updatedState -> Result(updatedState, events)
        }

    override def leaveTable2(tid: TableId, uid: Set[UserId]): IO[Result] =
      stateStorage
        .updateState(tid) { s: TableState =>
          val updatedState = s.copy(users = s.users -- uid)
          val events = updatedState.users.toList.map { sittingUser =>
            TE.LeftTable(tid, sittingUser, uid)
          }

          updatedState -> Result(updatedState, events)
        }

    override def placeBets(tid: TableId, uid: List[(UserId, GameId, Bet)]): IO[Result] =
      stateStorage
        .updateState(tid) {
          case TableState(tid, users, Some(game)) if game.betsOpen =>
            val updatedGame = uid.foldLeft(game) {
              case (g, (userId, gid, bet)) =>
                if (g.id == gid && !g.betExists(userId, bet.id)) {
                  val userBets = g.bets.getOrElse(userId, List.empty) :+ bet
                  g.copy(bets = g.bets + (userId -> userBets))
                } else g
            }
            val updatedState = TableState(tid, users, Some(updatedGame))
            val events       = uid.map { case (userId, gid, bet) => TE.BetAccepted(tid, gid, userId, bet) }

            updatedState -> Result(updatedState, events)
          case s => s -> Result(s, List.empty)
        }

    override def removeBets(tid: TableId, uid: List[(UserId, GameId)]): IO[Result] =
      stateStorage
        .updateState(tid) {
          case TableState(tid, users, Some(game)) =>
            val updatedGame = uid.foldLeft(game) {
              case (g, (userId, gid)) if g.id == gid =>
                g.copy(bets = g.bets - userId)
              case (g, _) => g
            }
            val updatedState = TableState(tid, users, Some(updatedGame))
            val events       = uid.map { case (userId, gid) => TE.BetRemoved(tid, gid, userId, List.empty) }

            updatedState -> Result(updatedState, events)
          case s => s -> Result(s, List.empty)
        }
  }
}
