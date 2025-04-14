package fr.simulator.user

import cats.effect.{IO, Ref}
import fr.domain.game.roulette.Bet
import fr.domain.user.{UserEvent, UserInput, UserState}
import fr.domain.{BetId, GameId, TableId, UserId}
import fr.simulator.user.AutomatedUser.{BetState, ExpectedUserState}
import fr.simulator.{HttpClient, WebSocketClient}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random

class AutomatedUser(uid: UserId, client: HttpClient) {
  def play(tid: TableId): IO[Unit] = {
    def joinTable(user: WebSocketClient): IO[UserState] =
      for {
        _ <- user.send(UserInput.RequestState)
        e <- user.expect[UserEvent.UserStateProvided]

        _ <- user.send(UserInput.JoinTable(tid))
        _ <- user.expect[UserEvent.UsersJoinedTable]
        ts = Instant.now().toString
        _ <- IO.println(s"[$ts] [$uid] OLOLO Joined table $tid")
      } yield e.state

    def randomBet: IO[Bet] = IO(Bet(BetId(UUID.randomUUID()), Random.nextInt(37), 1))

    def placeBets(user: WebSocketClient, betState: Ref[IO, BetState], expected: Ref[IO, ExpectedUserState]): IO[Unit] = betState.get.flatMap {
      case BetState.Closed => IO.sleep(500.millis) *> placeBets(user, betState, expected)
      case BetState.Open(gid) =>
        for {
          bet <- randomBet
          randomDelay = (Random.nextInt(1000) + 150).millis
          _ <- IO.sleep(randomDelay)
          _ <- user.send(UserInput.PlaceBet(bet, tid, gid))
          _ <- expected.update(s => s.copy(balance = s.balance - bet.amount, blockedBalance = s.blockedBalance + bet.amount))
          _ <- placeBets(user, betState, expected)
        } yield ()
    }

    def run(user: WebSocketClient): IO[Unit] = {
      def betsOpenedListener(ref: Ref[IO, BetState], userBets: Ref[IO, List[Int]]): IO[Unit] =
        user.addHandler {
          case e: UserEvent.BetsOpened =>
            userBets.set(List.empty) *> ref.set(BetState.Open(e.gid)) *> IO.println(s"[$uid] Bets open")
          case _ => IO.unit
        }

      def betsClosedListener(ref: Ref[IO, BetState], expectedBalance: Ref[IO, ExpectedUserState], actualBalance: Ref[IO, BigDecimal]): IO[Unit] =
        user.addHandler {
          case _: UserEvent.BetsClosed =>
            def compareBalances(expected: ExpectedUserState, actual: BigDecimal) =
              if (expected.blockedBalance == 0 && expected.balance != actual)
                IO.println(s"[$uid] Balance mismatch: expected $expected, got $actual") *> IO.raiseError(new Exception("Balance mismatch"))
              else
                IO.println(s"[$uid] Balance matches: $expected")

            for {
              expected <- expectedBalance.get
              actual   <- actualBalance.get
              _        <- ref.set(BetState.Closed)
              _        <- IO.println(s"[$uid] Bets closed")
              _        <- compareBalances(expected, actual)
            } yield ()
          case _ => IO.unit
        }

      def resultListener(ref: Ref[IO, List[Int]], expected: Ref[IO, ExpectedUserState]): IO[Unit] =
        user.addHandler {
          case e: UserEvent.GameFinished =>
            ref.get.flatMap { bets =>
              if (bets.isEmpty) IO.unit
              else {
                val winnings = bets.map { bet =>
                  if (bet == e.result) 36 else 0
                }.sum

                expected.update(s => s.copy(balance = s.balance + winnings))
              }
            }
          case _ => IO.unit
        }

      def balanceListener(balance: Ref[IO, BigDecimal]): IO[Unit] =
        user.addHandler {
          case e: UserEvent.BalanceUpdated => balance.set(e.balance)
          case _                           => IO.unit
        }

      def betResultListener(bets: Ref[IO, List[Int]], expected: Ref[IO, ExpectedUserState]): IO[Unit] =
        user.addHandler {
          case e: UserEvent.BetAccepted =>
            bets.update(_ :+ e.bet.number) *>
              expected.update(s => s.copy(balance = s.balance - e.bet.amount, blockedBalance = s.blockedBalance - e.bet.amount)) *>
              IO.println(s"[$uid] Bet accepted: ${e.bet.number}")
          case e: UserEvent.BetRejected =>
            expected.update(s => s.copy(balance = s.balance + e.bet.amount, blockedBalance = s.blockedBalance - e.bet.amount)) *>
              IO.println(s"[$uid] Bet rejected: ${e.bet.number}")
          case _ => IO.unit
        }

      for {
        state         <- joinTable(user)
        betsState     <- Ref.of[IO, BetState](BetState.Closed)
        userBets      <- Ref.of[IO, List[Int]](List.empty)
        expectedState <- Ref.of[IO, ExpectedUserState](ExpectedUserState(state.balance, 0))
        actualBalance <- Ref.of[IO, BigDecimal](state.balance)
        _             <- betsOpenedListener(betsState, userBets)
        _             <- betsClosedListener(betsState, expectedState, actualBalance)
        _             <- resultListener(userBets, expectedState)
        _             <- balanceListener(actualBalance)
        _             <- betResultListener(userBets, expectedState)
        _             <- placeBets(user, betsState, expectedState)
      } yield ()
    }

    client
      .openWs("ws://localhost:8080/sticky/ws?userId=" + uid.value, uid)
      .use(run)
  }
}

object AutomatedUser {
  case class ExpectedUserState(balance: BigDecimal, blockedBalance: BigDecimal)

  sealed trait BetState
  object BetState {
    case class Open(gid: GameId) extends BetState
    case object Closed           extends BetState
  }
}
