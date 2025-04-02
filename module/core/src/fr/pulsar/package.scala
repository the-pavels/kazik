package fr
import cats._
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import ciris.{ConfigDecoder, env}
import cr.pulsar.Pulsar.Options.{ConnectionTimeout, OperationTimeout}
import cr.pulsar.Pulsar.PulsarURL
import cr.pulsar.Topic.URL
import cr.pulsar.{Pulsar => PulsarClient, ShardKey => _, _}
import fr.domain._
import fr.pulsar.ShardKey.SyntaxOps
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import org.apache.pulsar.client.api.DeadLetterPolicy

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration._

package object pulsar {
  implicit val cfgDecoderURL: ConfigDecoder[String, PulsarURL] =
    ConfigDecoder[String].map(PulsarURL(_))

  implicit def circeBytesInject[T: Encoder: Decoder]: Inject[T, Array[Byte]] =
    new Inject[T, Array[Byte]] {
      val inj: T => Array[Byte] =
        _.asJson.noSpaces.getBytes(UTF_8)

      val prj: Array[Byte] => Option[T] =
        bytes => decode[T](new String(bytes, UTF_8)).toOption
    }

  implicit val cfgDecoderConnectionTimeout: ConfigDecoder[String, ConnectionTimeout] =
    ConfigDecoder[String, FiniteDuration].map(ConnectionTimeout(_))

  implicit val cfgDecoderOperationTimeout: ConfigDecoder[String, OperationTimeout] =
    ConfigDecoder[String, FiniteDuration].map(OperationTimeout(_))

  def dummyLogger[E](direction: String): Logger[IO, E] = new Logger[IO, E] {
    override def log(topic: URL, e: E): IO[Unit] = IO.println(s"[$topic] $direction: $e")
  }

  object Pulsar {
    def default(url: PulsarURL): Resource[IO, PulsarClient.Underlying] = {
      val opts = (
        env("PULSAR_CONNECTION_TIMEOUT").as[ConnectionTimeout].withDefault(5.seconds),
        env("PULSAR_OPERATION_TIMEOUT").as[OperationTimeout].withDefault(5.seconds)
      ).mapN {
          case (connectionTimeout, operationTimeout) =>
            PulsarClient
              .Options()
              .withConnectionTimeout(connectionTimeout)
              .withOperationTimeout(operationTimeout)
        }
        .load[IO]

      Resource
        .eval(opts)
        .flatMap(PulsarClient.make[IO](url, _))
    }
  }

  object LoggingConsumer {
    def make[E: Decoder: Encoder](
        client: PulsarClient.Underlying,
        topic: Topic,
        sub: Subscription
    ): Resource[IO, Consumer[IO, E]] = {
      val dlp = DeadLetterPolicy
        .builder()
        .maxRedeliverCount(10)
        .deadLetterTopic("cr-dead-letter-queue")
        .build()

      val opts = Consumer
        .Options[IO, E]()
        .withLogger(dummyLogger[E]("in"))
        .withDeadLetterPolicy(dlp)
      Consumer.make[IO, E](client, topic, sub, opts)
    }
  }

  object LoggingProducer {
    def default[
        E: Decoder: Encoder
    ](
        client: PulsarClient.Underlying,
        topic: Topic.Single
    ): Resource[IO, Producer[IO, E]] = {
      val opts = Producer
        .Options[IO, E]()
        .withLogger(dummyLogger[E]("out"))
      Producer.make[IO, E](client, topic, opts)
    }

    def sharded[
        E: Decoder: Encoder: ShardKey
    ](
        client: PulsarClient.Underlying,
        topic: Topic.Single
    ): Resource[IO, Producer[IO, E]] = {
      val opts = Producer
        .Options[IO, E]()
        .withShardKey(_.shardKey)
        .withLogger(dummyLogger[E]("out"))
      Producer.make[IO, E](client, topic, opts)
    }
  }

}
