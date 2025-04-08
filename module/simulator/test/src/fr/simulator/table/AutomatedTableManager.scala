package fr.simulator.table

import cats.effect.{IO, Ref}
import fr.domain.TableId
import fr.simulator.HttpClient
import fr.simulator.scenario.setup.Scenario.eventually

import scala.concurrent.duration.DurationInt
import scala.util.Random

class AutomatedTableManager(client: HttpClient) {
  private val tableManager = TableManagerClient(client)
  private val killSwitch   = Ref.unsafe[IO, Boolean](true)

  def createTable(tid: TableId): IO[Unit] = {
    for {
      _ <- tableManager.createTable(tid)
      _ <- eventually(10.seconds) {
        tableManager.getTables.map(_.contains(tid))
      }
    } yield ()
  }

  def stop: IO[Unit] = killSwitch.set(false)

  def start(tid: TableId): IO[Unit] = {
    def run(i: Int): IO[Unit] =
      for {
        continue <- killSwitch.get
        _ <- if (!continue) IO.unit
        else
          for {
            _      <- IO.println(s"Running round $i on table $tid")
            _      <- tableManager.openBets(tid)
            _      <- IO.sleep(5.seconds)
            _      <- tableManager.closeBets(tid)
            _      <- IO.sleep(3.seconds)
            result <- IO(Random.nextInt(37))
            _      <- tableManager.setResult(tid, result)
            _      <- IO.sleep(2.seconds)
            _      <- run(i + 1)
          } yield ()
      } yield ()

    run(1)
  }

}
