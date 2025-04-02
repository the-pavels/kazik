package fr.table

import fr.domain.{Bet, UserId}

object WinningCalculator {
  def winnings(bets: Map[UserId, List[Bet]], result: Int): Map[UserId, Int] = {
    bets.map {
      case (uid, bets) =>
        val winnings = bets.map {
          case Bet(_, number, amount) if number == result => amount * 36
          case _                                          => 0
        }.sum
        uid -> winnings
    }
  }
}
