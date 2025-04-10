package fr.simulator.user

import cats.effect.{IO, Ref}
import fr.domain.Event.{OutgoingUserEvent => OUE}
import fr.domain.game.roulette.Bet
import fr.domain.user.UserInput
import fr.domain.{BetId, GameId, TableId, UserId}
import fr.simulator.user.AutomatedUser.BetState
import fr.simulator.{HttpClient, WebSocketClient}

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random

class AutomatedUser(uid: UserId, client: HttpClient) {
  def play(tid: TableId): IO[Unit] = {
    def joinTable(user: WebSocketClient): IO[Unit] =
      for {
        _ <- user.send(UserInput.RequestState)
        _ <- user.expect[OUE.StateBroadcasted]

        _ <- user.send(UserInput.JoinTable(tid))
        _ <- user.expect[OUE.UserJoinedTable]
      } yield ()

    def randomBet: IO[Bet] = IO(Bet(BetId(UUID.randomUUID()), Random.nextInt(37), 1))

    def placeBets(user: WebSocketClient, betState: Ref[IO, BetState], balance: Ref[IO, Int]): IO[Unit] = betState.get.flatMap {
      case BetState.Closed => IO.sleep(500.millis) *> placeBets(user, betState, balance)
      case BetState.Open(gid) =>
        for {
          bet <- randomBet
          randomDelay = (Random.nextInt(1000) + 150).millis
          _ <- IO.sleep(randomDelay)
          _ <- user.send(UserInput.PlaceBet(bet, tid, gid))
//          amount <- IO(Random.nextInt(1000))
          _ <- placeBets(user, betState, balance)
        } yield ()
    }

    def run(user: WebSocketClient): IO[Unit] = {
      def betsOpenedListener(ref: Ref[IO, BetState], userBets: Ref[IO, List[Int]]): IO[Unit] =
        user.addHandler {
          case e: OUE.BetsOpened =>
            userBets.set(List.empty) *> ref.set(BetState.Open(e.gid)) *> IO.println(s"[$uid] Bets open")
          case _ => IO.unit
        }

      def betsClosedListener(ref: Ref[IO, BetState]): IO[Unit] =
        user.addHandler {
          case _: OUE.BetsClosed =>
            ref.set(BetState.Closed) *> IO.println(s"[$uid] Bets closed")
          case _ => IO.unit
        }

      def resultListener(ref: Ref[IO, List[Int]], balance: Ref[IO, Int]): IO[Unit] =
        user.addHandler {
          case e: OUE.GameFinished =>
            ref.get.flatMap { bets =>
              if (bets.isEmpty) IO.unit
              else {
                val winnings = bets.map { bet =>
                  if (bet == e.result) 36 else 0
                }.sum

                balance.update(_ + winnings)
              }
            }
          case _ => IO.unit
        }

      def balanceListener(balance: Ref[IO, Int]): IO[Unit] =
        user.addHandler {
          case e: OUE.BalanceUpdated =>
            balance.get.map { b =>
              if (e.balance != b) {
                println(s"[$uid] Balance updated: ${e.balance} != $b")
              }
              assert(e.balance == b)
            }
          case _ => IO.unit
        }

      def betAccepted(bets: Ref[IO, List[Int]], value: Ref[IO, Int]): IO[Unit] =
        user.addHandler {
          case e: OUE.BetAccepted =>
            bets.update(_ :+ e.bet.number) *> value.update(_ - 1) *> IO.println(s"[$uid] Bet accepted: ${e.bet.number}")
          case _ => IO.unit
        }

      for {
        _               <- joinTable(user)
        betsState       <- Ref.of[IO, BetState](BetState.Closed)
        userBets        <- Ref.of[IO, List[Int]](List.empty)
        expectedBalance <- Ref.of[IO, Int](100000)
        _               <- betsOpenedListener(betsState, userBets)
        _               <- betsClosedListener(betsState)
        _               <- resultListener(userBets, expectedBalance)
        _               <- balanceListener(expectedBalance)
        _               <- betAccepted(userBets, expectedBalance)
        _               <- placeBets(user, betsState, expectedBalance)
      } yield ()
    }

    client
      .openWs("ws://localhost:8080/sticky/ws?userId=" + uid.value, uid)
      .use(run)
  }
}

object AutomatedUser {
  sealed trait BetState
  object BetState {
    case class Open(gid: GameId) extends BetState
    case object Closed           extends BetState
  }
}
