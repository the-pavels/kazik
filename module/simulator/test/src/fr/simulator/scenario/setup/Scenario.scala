package fr.simulator.scenario.setup

import cats.effect.{IO, Resource}
import fr.domain.{TableId, UserId}
import fr.simulator.WebSocketClient
import fr.simulator.user.UserClient

import java.util.UUID
import scala.concurrent.TimeoutException
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait Scenario extends IOSpec with HttpClientFixture {
  def mkUser(uid: UserId): Resource[IO, WebSocketClient] = UserClient.start(uid, client)
}

object Scenario {
  def genUserId: UserId   = UserId(UUID.randomUUID())
  def genTableId: TableId = TableId(UUID.randomUUID())

  def eventually(timeout: FiniteDuration)(condition: => IO[Boolean]): IO[Unit] = {
    def wait(): IO[Unit] =
      condition.ifM(
        IO.unit,
        IO.sleep(100.millis) *> wait()
      )

    IO.race(wait(), IO.sleep(timeout) *> IO.raiseError(new TimeoutException("Condition never met"))).void
  }
}
