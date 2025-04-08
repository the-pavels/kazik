package fr.table

import cats.effect.IO
import fr.domain.TableId
import fr.table.Routes.{CloseBetsRequest, CreateTableRequest, OpenBetsRequest, SetResultRequest}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.{HttpRoutes, Response}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl

case class Routes(tableManager: TableManager) extends Http4sDsl[IO] {
  val routes: HttpRoutes[IO] = HttpRoutes.of {
    case req @ POST -> Root / "table" =>
      val result = for {
        create   <- req.as[CreateTableRequest]
        _        <- tableManager.create(create.tid)
        response <- Created()
      } yield response

      result.handleErrorWith(errorHandler)

    case req @ POST -> Root / "closeBets" =>
      val result = for {
        closeBet <- req.as[CloseBetsRequest]
        _        <- tableManager.closeBets(closeBet.tid)
        response <- Ok()
      } yield response

      result.handleErrorWith(errorHandler)

    case req @ POST -> Root / "openBets" =>
      val result = for {
        openRequest <- req.as[OpenBetsRequest]
        _           <- tableManager.startGame(openRequest.tid)
        response    <- Ok()
      } yield response

      result.handleErrorWith(errorHandler)

    case req @ POST -> Root / "setResult" =>
      val result = for {
        resultRequest <- req.as[SetResultRequest]
        _             <- tableManager.setResult(resultRequest.tid, resultRequest.result)
        response      <- Ok()
      } yield response

      result.handleErrorWith(errorHandler)
  }

  private def errorHandler(e: Throwable): IO[Response[IO]] = IO.println(s"Table management error: ${e.getMessage}") *> InternalServerError()
}

object Routes {
  case class CreateTableRequest(tid: TableId)
  object CreateTableRequest {
    implicit val codec: Codec[CreateTableRequest] = deriveCodec
  }

  case class TableListResponse(tids: List[TableId])
  object TableListResponse {
    implicit val codec: Codec[TableListResponse] = deriveCodec
  }

  case class CloseBetsRequest(tid: TableId)
  object CloseBetsRequest {
    implicit val codec: Codec[CloseBetsRequest] = deriveCodec
  }

  case class OpenBetsRequest(tid: TableId)
  object OpenBetsRequest {
    implicit val codec: Codec[OpenBetsRequest] = deriveCodec
  }

  case class SetResultRequest(tid: TableId, result: Int)
  object SetResultRequest {
    implicit val codec: Codec[SetResultRequest] = deriveCodec
  }
}
