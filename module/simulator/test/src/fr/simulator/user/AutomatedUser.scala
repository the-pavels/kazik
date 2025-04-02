package fr.simulator.user

import cats.effect.{IO, Ref}
import fr.domain.Event.{OutgoingUserEvent => OUE}
import fr.domain.{Bet, BetId, GameId, TableId, UserAction, UserId}
import fr.simulator.user.AutomatedUser.BetState
import fr.simulator.{HttpClient, WebSocketClient}

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random

class AutomatedUser(uid: UserId, client: HttpClient) {
  def play(tid: TableId): IO[Unit] = {
    def joinTable(user: WebSocketClient): IO[Unit] =
      for {
        _ <- user.send(UserAction.StateRequested)
        _ <- user.expect[OUE.StateResponse]

        _ <- user.send(UserAction.JoinedTable(tid))
        _ <- user.expect[OUE.UserJoinedTable]
      } yield ()

    def randomBet: IO[Bet] = IO(Bet(BetId(UUID.randomUUID()), Random.nextInt(37), 1))

    def placeBets(user: WebSocketClient, betState: Ref[IO, BetState]): IO[Unit] = betState.get.flatMap {
      case BetState.Closed => IO.sleep(500.millis) *> placeBets(user, betState)
      case BetState.Open(gid) =>
        for {
          _   <- IO.println(s"[$uid] Will place a bet now")
          bet <- randomBet
          _   <- IO.sleep(3000.millis)
          _   <- user.send(UserAction.BetPlaced(bet, tid, gid))
//          amount <- IO(Random.nextInt(1000))
          _ <- placeBets(user, betState)
        } yield ()
    }

    def run(user: WebSocketClient): IO[Unit] = {
      def betsOpenedListener(ref: Ref[IO, BetState]): IO[Unit] =
        user.addHandler {
          case e: OUE.BetsOpened =>
            ref.set(BetState.Open(e.gid)) *> IO.println(s"[$uid] Bets open")
          case _ => IO.unit
        }

      def betsClosedListener(ref: Ref[IO, BetState]): IO[Unit] =
        user.addHandler {
          case _: OUE.BetsClosed =>
            ref.set(BetState.Closed) *> IO.println(s"[$uid] Bets closed")
          case _ => IO.unit
        }

      for {
        _         <- joinTable(user)
        betsState <- Ref.of[IO, BetState](BetState.Closed)
        _         <- betsOpenedListener(betsState)
        _         <- betsClosedListener(betsState)
        _         <- placeBets(user, betsState)
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
