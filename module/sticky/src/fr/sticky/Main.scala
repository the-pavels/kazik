package fr.sticky

import cats.effect._
import cr.pulsar.{Pulsar => PulsarClient}
import fr.adapter.http.HttpServer
import fr.adapter.pulsar.Pulsar
import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2

object StickyApp {
  def resource(pulsar: PulsarClient.Underlying): Resource[IO, WebSocketBuilder2[IO] => HttpRoutes[IO]] =
    for {
      dispatcher <- Dispatcher.make(pulsar)
      wsHandler = WebSocketHandler.make(dispatcher)

      routes = (webSocketBuilder: WebSocketBuilder2[IO]) => Routes(wsHandler, pulsar, webSocketBuilder).routes
    } yield routes
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    val app = for {
      cfg    <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.default(cfg.pulsarURL)
      wsApi  <- StickyApp.resource(pulsar)
      http   <- HttpServer.build(Left(wsApi(_).orNotFound))
    } yield http

    app.useForever
  }
}
