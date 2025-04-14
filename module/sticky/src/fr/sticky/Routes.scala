package fr.sticky

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import cr.pulsar.{Consumer, Subscription, Pulsar => PulsarClient}
import fr.adapter.pulsar.LoggingConsumer
import fr.domain.UserId
import fr.sticky.Routes.UserIdQueryParam
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io.QueryParamDecoderMatcher
import org.http4s.server.websocket.WebSocketBuilder2

import java.util.UUID
import fr.adapter.pulsar.AppTopic
import fr.domain.user.UserEvent.UserEventEnvelope

case class Routes(wsHandler: WebSocketHandler, client: PulsarClient.Underlying, builder: WebSocketBuilder2[IO]) extends Http4sDsl[IO] {
  private def consume(uid: UserId): Resource[IO, Consumer[IO, UserEventEnvelope]] = {
    val subscription = Subscription.Builder
      .withName(Subscription.Name(s"outgoing-${uid.show}"))
      .withType(Subscription.Type.Failover)
      .withMode(Subscription.Mode.NonDurable)
      .build

    LoggingConsumer.make[UserEventEnvelope](client, AppTopic.UserEvent(uid.value.toString).make, subscription).onError {
      case ex =>
        Resource.eval(IO.println(s"Error while consuming messages for uid: ${uid.show}: ${ex.getMessage}"))
    }
  }

  private def openWs(uid: UserId): IO[Response[IO]] =
    consume(uid).allocated
      .flatMap {
        case (c, release) =>
          val (send, receive) = wsHandler.build(uid, c.autoSubscribe)
          builder
            .withOnClose(IO.println(s"Closed WS connection for uid: ${uid.show}") *> release)
            .build(send, receive)
      }

  val routes: HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root / "ws" :? UserIdQueryParam(uid) =>
      val result = for {
        _ <- IO.println(s"[$uid] Opening a WS connection")

        response <- openWs(uid)

        _ <- IO.println(s"[$uid] Opened WS connection")
      } yield response

      result.handleErrorWith { ex =>
        IO.println(s"Unexpected error: ${ex.getMessage}") *>
          IO(ex.printStackTrace()).as(Response(status = InternalServerError))
      }
  }
}

object Routes {
  implicit val queryParam: QueryParamDecoder[UserId] = QueryParamDecoder[String].map(s => UserId(UUID.fromString(s)))

  object UserIdQueryParam extends QueryParamDecoderMatcher[UserId]("userId")
}
