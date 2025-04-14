package fr.simulator

import cats.effect.IO
import fr.domain.UserId
import io.circe.syntax.EncoderOps
import cats.effect.Ref
import cats.effect.std.Queue
import cats.syntax.all._
import fr.domain.user.UserEvent
import fr.domain.user.UserEvent.UserEventEnvelope
import fr.domain.user.UserInput
import io.circe.parser.{decode => jsonDecode}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.ws.{WebSocket, WebSocketFrame}

import java.util.concurrent.CancellationException
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait WebSocketClient {
  def close: IO[Unit]
  def addHandler(handler: UserEvent => IO[Unit]): IO[Unit]
  def expect[T <: UserEvent: ClassTag]: IO[T]
  def expectEither[T1 <: UserEvent: ClassTag, T2 <: UserEvent: ClassTag](andThen: Either[T1, T2] => IO[Unit]): IO[Unit]
  def listenEvents: IO[Unit]
  def send(msg: UserInput): IO[Unit]
}

object WebSocketClient {
  val ExpectTimeout: FiniteDuration = 40.seconds
  val maxRetries                    = 100

  def make(uid: UserId, ws: WebSocket[IO]): IO[WebSocketClient] =
    (Queue.unbounded[IO, UserEvent], Ref.of[IO, List[UserEvent => IO[Unit]]](List.empty)).mapN {
      case (queue, handlersRef) =>
        new WebSocketClient {
          private val logger: SelfAwareStructuredLogger[IO] =
            Slf4jLogger.getLoggerFromName[IO](getClass.getSimpleName)

          def addHandler(handler: UserEvent => IO[Unit]): IO[Unit] =
            handlersRef.update(_ :+ handler)

          def raceTimeout[T: ClassTag](
              fa: IO[T],
              after: FiniteDuration
          ): IO[T] =
            IO.race(fa, IO.sleep(after))
              .flatMap {
                case Left(a) => IO.pure(a)
                case Right(_) =>
                  throw new CancellationException(s"Failed to receive expected message $expectedClass in $after")
              }

          def expectedClass[T: ClassTag]: String = implicitly[ClassTag[T]].runtimeClass.getSimpleName

          def expect[T <: UserEvent: ClassTag]: IO[T] = {
            def parseIncoming(retries: Int): IO[T] =
              queue.take
                .flatMap {
                  case t: T =>
                    logger.info(s"$uid Received expected ${t.getClass}") *>
                      IO.pure(t)
                  case msg if retries == maxRetries =>
                    Left(
                      new IllegalArgumentException(
                        s"$uid Didn't receive expected $expectedClass, input: $msg"
                      )
                    ).liftTo[IO]
                  case _ =>
                    parseIncoming(retries + 1)
                }

            raceTimeout(parseIncoming(0), ExpectTimeout)
          }

          def send(msg: UserInput): IO[Unit] = {
            val m = msg.asJson.noSpaces
            logger.info(s"OUT $uid <<< $m") *> ws.send(WebSocketFrame.text(m))
          }

          def expectConnectionClosed: IO[Unit] = {
            def expectClose: IO[Unit] =
              ws.isOpen().ifM(expectClose, IO.unit)

            raceTimeout(expectClose, ExpectTimeout)
          }

          def close: IO[Unit] = ws.close() *> expectConnectionClosed

          def listenEvents: IO[Unit] =
            ws.receiveText()
              .flatMap { msg =>
                jsonDecode[UserEvent](msg)
                  .liftTo[IO]
                  .flatTap(e => handlersRef.get.flatMap(_.traverse_(h => h(e))))
                  .flatMap(e => logger.info(s"IN $uid >>> $e") *> queue.offer(e))
                  .onError { e =>
                    logger.error(s"$uid Couldn't parse incoming message. Expected UserEvent, input: $msg Ex: $e") *> IO(throw e)
                  }
              } >> listenEvents

          override def expectEither[T1 <: UserEvent: ClassTag, T2 <: UserEvent: ClassTag](andThen: Either[T1, T2] => IO[Unit]): IO[Unit] =
            for {
              oneOf <- IO.race(expect[T1], expect[T2])
              _     <- andThen(oneOf)
            } yield ()
        }
    }
}
