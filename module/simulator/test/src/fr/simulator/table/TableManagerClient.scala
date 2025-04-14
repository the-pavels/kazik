package fr.simulator.table

import cats.effect.IO
import fr.domain.TableId
import fr.table.Routes.{CloseBetsRequest, CreateTableRequest, OpenBetsRequest, SetResultRequest}
import fr.simulator.HttpClient

case class TableManagerClient(client: HttpClient) {
  def createTable(tid: TableId): IO[Unit] =
    for {
      _ <- IO.println(s"Creating table $tid")
      _ <- client.postEntity(s"table/table", CreateTableRequest(tid))
      _ <- IO.println(s"Table $tid created")
    } yield ()

  def closeBets(tid: TableId): IO[Unit] =
    for {
      _ <- IO.println(s"Closing bets for table $tid")
      _ <- client.postEntity(s"table/closeBets", CloseBetsRequest(tid))
      _ <- IO.println(s"Bets closed for table $tid")
    } yield ()

  def openBets(tid: TableId): IO[Unit] =
    for {
      _ <- IO.println(s"Opening bets for table $tid")
      _ <- client.postEntity(s"table/openBets", OpenBetsRequest(tid))
      _ <- IO.println(s"Bets opened for table $tid")
    } yield ()

  def setResult(tid: TableId, result: Int): IO[Unit] =
    for {
      _ <- IO.println(s"Setting result for table $tid")
      _ <- client.postEntity(s"table/setResult", SetResultRequest(tid, result))
      _ <- IO.println(s"Result set for table $tid")
    } yield ()
}
