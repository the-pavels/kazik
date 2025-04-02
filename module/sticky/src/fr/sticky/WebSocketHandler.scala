package fr.sticky

import cats.effect.IO
import cats.syntax.all._
import fr.domain.Event.UserEvent.UserActionReceived
import fr.domain.Event.{EventId, OutgoingUserEvent}
import fr.domain.{UserAction, UserId}
import fr.sticky.WebSocketHandler.WebSocketFlow
import fs2.{Pipe, Stream}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json, parser}
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}

trait WebSocketHandler {
  def build(uid: UserId, outgoing: fs2.Stream[IO, OutgoingUserEvent]): WebSocketFlow
}

object WebSocketHandler {
  type Send          = fs2.Stream[IO, WebSocketFrame]
  type Receive       = fs2.Pipe[IO, WebSocketFrame, Unit]
  type WebSocketFlow = (Send, Receive)

  def make(dispatcher: Dispatcher): WebSocketHandler =
    new WebSocketHandler {
      def log[A: Encoder](json: Json): Pipe[IO, A, A] =
        _.evalTap(e => IO.println(Json.obj("event" -> e.asJson).deepMerge(json).noSpaces))

      def decode: WebSocketFrame => IO[Option[UserAction]] = {
        case Text(msg, _) => IO.fromEither(parser.decode[UserAction](msg).map(Some(_)))
        case Close(_)     => IO.pure(None)
        case wsf          => IO.raiseError(new IllegalArgumentException(s"Unknown type: ${wsf.toString}"))
      }

      def encode: OutgoingUserEvent => IO[WebSocketFrame] =
        out => IO.pure(Text(out.asJson.noSpaces))

      def processIncoming(uid: UserId)(in: UserAction): IO[Unit] =
        for {
          ts  <- IO.realTimeInstant
          eid <- IO.randomUUID
          _   <- dispatcher.dispatch(UserActionReceived(EventId(eid), uid, in, ts))
        } yield ()

      def build(uid: UserId, outgoing: fs2.Stream[IO, OutgoingUserEvent]): WebSocketFlow = {
        val send: Stream[IO, WebSocketFrame] =
          outgoing
            .through(log(Json.obj("flow" -> "ws-out".asJson, "uid" -> uid.show.asJson)))
            .evalMap(encode)

        val receive: Pipe[IO, WebSocketFrame, Unit] =
          _.evalMapFilter(decode)
            .through(log(Json.obj("flow" -> "ws-in".asJson, "uid" -> uid.show.asJson)))
            .evalMap(processIncoming(uid))

        (send, receive)
      }
    }
}
