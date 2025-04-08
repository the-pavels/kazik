package fr.simulator

import cats.effect.IO
import fr.domain.UserId
import io.circe.syntax.EncoderOps

import cats.effect.Ref
import cats.effect.std.Queue
import cats.syntax.all._
import fr.domain.Event.{OutgoingUserEvent => OUE}
import fr.domain.{UserAction => UA}
import io.circe.parser.{decode => jsonDecode}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.ws.{WebSocket, WebSocketFrame}

import java.util.concurrent.CancellationException
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait WebSocketClient {
  def close: IO[Unit]
  def addHandler(handler: OUE => IO[Unit]): IO[Unit]
  def expect[T <: OUE: ClassTag]: IO[T]
  def listenEvents: IO[Unit]
  def send(msg: UA): IO[Unit]
}

object WebSocketClient {
  val ExpectTimeout: FiniteDuration = 40.seconds
  val maxRetries                    = 100

  def make(uid: UserId, ws: WebSocket[IO]): IO[WebSocketClient] =
    (Queue.unbounded[IO, OUE], Ref.of[IO, List[OUE => IO[Unit]]](List.empty)).mapN {
      case (queue, handlersRef) =>
        new WebSocketClient {
          private val logger: SelfAwareStructuredLogger[IO] =
            Slf4jLogger.getLoggerFromName[IO](getClass.getSimpleName)

          def addHandler(handler: OUE => IO[Unit]): IO[Unit] =
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

          def expect[T <: OUE: ClassTag]: IO[T] = {
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

          def send(msg: UA): IO[Unit] = {
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
                jsonDecode[OUE](msg)
                  .liftTo[IO]
                  .flatTap(e => handlersRef.get.flatMap(_.traverse_(h => h(e))))
                  .flatMap(e => logger.info(s"IN $uid >>> $e") *> queue.offer(e))
                  .onError { e =>
                    logger.error(s"$uid Couldn't parse incoming message. Expected OUE, input: $msg Ex: $e") *> IO(throw e)
                  }
              } >> listenEvents
        }
    }
}
