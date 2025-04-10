package fr.domain.game.roulette

import fr.domain.game.roulette.Game.GameState
import fr.domain.{BetId, GameId, UserId}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Game(id: GameId, bets: Map[UserId, List[Bet]], state: GameState) {
  def betsOpen: Boolean = state == GameState.BetsOpen

  def userBets(uid: UserId): List[Bet]            = bets.getOrElse(uid, List.empty)
  def betExists(uid: UserId, bid: BetId): Boolean = userBets(uid).exists(_.id == bid)
}
object Game {
  sealed trait GameState
  object GameState {
    case object BetsOpen             extends GameState
    case object BetsClosed           extends GameState
    case class GameOver(result: Int) extends GameState
    implicit val codec: Codec[GameState] = deriveCodec
  }

  implicit val codec: Codec[Game] = deriveCodec
}
