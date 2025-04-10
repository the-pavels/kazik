package fr.devapp

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.implicits.toSemigroupKOps
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import doobie.util.transactor.Transactor
import fr.adapter.http.HttpServer
import fr.adapter.pulsar.Pulsar
import org.http4s.HttpApp
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object Main extends IOApp.Simple {
  implicit val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("cr.devapp.Logger")

  override def run: IO[Unit] = {
    val resources: Resource[IO, (fs2.Stream[IO, Unit], WebSocketBuilder2[IO] => HttpApp[IO])] = for {
      cfg    <- Resource.eval(Config.load)
      pulsar <- Pulsar.default(cfg.pulsarURL)
      redis  <- RedisClient[IO].from(cfg.redisConfig.value)
      transactor = Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        cfg.postgresDb.url.value,
        cfg.postgresDb.user.value,
        cfg.postgresDb.password.value,
        None
      )

      userEventProcessing              <- fr.user.UserHandlerApp.resource(transactor, pulsar, redis)
      (tableEventProcessing, tableApi) <- fr.table.TableHandlerApp.resource(transactor, pulsar, redis)
      stickyApi                        <- fr.sticky.StickyApp.resource(pulsar)
    } yield {
      val api: WebSocketBuilder2[IO] => HttpApp[IO] = (webSocketBuilder: WebSocketBuilder2[IO]) =>
        (
          Router[IO]("/sticky"  -> stickyApi(webSocketBuilder)) <+>
            Router[IO]("/table" -> tableApi)
        ).orNotFound

      val eventProcessing = userEventProcessing.concurrently(tableEventProcessing)

      eventProcessing -> api
    }

    resources.use {
      case (eventStream, api) => eventStream.compile.drain &> HttpServer.build(Left(api)).useForever
    }
  }
}
