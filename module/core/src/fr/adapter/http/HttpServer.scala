package fr.adapter.http

import cats.effect.IO
import cats.effect.kernel.Resource
import ciris.{ConfigDecoder, env}
import com.comcast.ip4s.{Port, _}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.{HttpApp, HttpRoutes}

import scala.concurrent.duration.Duration

object HttpServer {
  implicit final val stringComcastPortDecoder: ConfigDecoder[String, Port] =
    ConfigDecoder[String].mapOption("com.comcast.ip4s.Port")(Port.fromString)

  def build(api: Either[WebSocketBuilder2[IO] => HttpApp[IO], HttpRoutes[IO]]): Resource[IO, Server] =
    for {
      port <- Resource.eval(env("SERVICE_PORT").as[Port].default(Port.fromInt(8080).get).load[IO])

      _ <- Resource.eval(IO.println(s"Starting HTTP server at port $port"))

      basicBuilder = EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port)

      server <- api match {
        case Left(wsApi)     => basicBuilder.withShutdownTimeout(Duration.Zero).withHttpWebSocketApp(wsApi).build
        case Right(otherApi) => basicBuilder.withHttpApp(otherApi.orNotFound).build
      }
    } yield server
}
