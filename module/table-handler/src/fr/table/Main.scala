package fr.table

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.{toFoldableOps, toTraverseOps}
import cr.pulsar.Consumer.Message
import cr.pulsar.{Subscription, Pulsar => PulsarClient}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.adapter.http.HttpServer
import fr.adapter.pulsar.{AppTopic, LoggingConsumer, LoggingProducer, Pulsar}
import fr.adapter.redis.StateStorage
import fr.domain.TableId
import fr.domain.table.TableEvent.TableEventEnvelope
import fr.domain.table.TableState
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import fr.table.TableManager.Result
import org.apache.pulsar.client.api.MessageId
import org.http4s.HttpRoutes

object TableHandlerApp {
  def resource(pulsar: PulsarClient.Underlying, redis: RedisClient): Resource[IO, (fs2.Stream[IO, Unit], HttpRoutes[IO])] = {
    for {
      stateStorage <- StateStorage.redis[TableId, TableState, Result](redis, _.asKey, TableState.empty)

      tableEventBroadcast = (e: TableEventEnvelope) => LoggingProducer.sharded[TableEventEnvelope](pulsar, AppTopic.TableEvent.make).use(_.send_(e))
      dispatcher          = Dispatcher.make(tableEventBroadcast)

      tableManager    = TableManager.make(stateStorage)
      incomingHandler = IncomingHandler.make(tableManager, dispatcher)

      subscription = Subscription.Builder
        .withName(Subscription.Name(s"table-handler"))
        .withType(Subscription.Type.KeyShared)
        .withMode(Subscription.Mode.NonDurable)
        .build

      routes = Routes(tableManager, dispatcher).routes

      userEventConsumer <- LoggingConsumer.make[UserTableActionEnvelope](pulsar, AppTopic.UserTableAction.make, subscription)
      userEventsProcessor = userEventConsumer.subscribe
        .chunkN(32)
        .parEvalMap(12) { msgChunk: fs2.Chunk[Message[UserTableActionEnvelope]] =>
          val actionsChunk: fs2.Chunk[UserTableActionEnvelope] = msgChunk.map(_.payload)
          val messageIds: List[MessageId]                      = msgChunk.map(_.id).toList

          incomingHandler
            .handle(actionsChunk.toList)
            .attempt
            .flatMap {
              case Right(_) => messageIds.traverse_(userEventConsumer.ack)
              case Left(err) =>
                IO.println(s"Error processing chunk of ${messageIds.size} messages: ${err.getMessage}") *>
                  messageIds.traverse_(userEventConsumer.nack) *>
                  IO.raiseError(err)
            }
        }
    } yield (userEventsProcessor, routes)
  }
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    val app = for {
      cfg               <- Resource.eval(Config.load)
      pulsar            <- Pulsar.default(cfg.pulsarURL)
      redis             <- RedisClient[IO].from(cfg.redisConfig.value)
      (stream, httpApi) <- TableHandlerApp.resource(pulsar, redis)
    } yield stream.drain -> httpApi

    app.use {
      case (stream, httpApi) => HttpServer.build(Right(httpApi)).useForever *> stream.compile.drain
    }
  }
}
