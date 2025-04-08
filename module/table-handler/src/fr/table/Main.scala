package fr.table

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.toShow
import cr.pulsar.{Subscription, Pulsar => PulsarClient}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.domain.Event.{TableUserEvent, UserTableEvent}
import fr.domain.{TableId, TableState}
import fr.http.HttpServer
import fr.pulsar.{AppTopic, LoggingConsumer, LoggingProducer, Pulsar}
import fr.redis.StateStorage
import org.http4s.HttpRoutes

import java.util.UUID

object TableHandlerApp {
  def resource(pulsar: PulsarClient.Underlying, redis: RedisClient): Resource[IO, (fs2.Stream[IO, Unit], HttpRoutes[IO])] =
    for {
      stateStorage <- StateStorage.redis[TableId, TableState](redis, _.asKey, TableState.empty)

      userBroadcast = (e: TableUserEvent) => LoggingProducer.sharded[TableUserEvent](pulsar, AppTopic.TableUser.make).use(_.send_(e))
      dispatcher    = Dispatcher.make(userBroadcast)

      tableManager    = TableManager.make(stateStorage, dispatcher)
      incomingHandler = IncomingHandler.make(tableManager, dispatcher)

      subscription = Subscription.Builder
        .withName(Subscription.Name(s"table-handler-${UUID.randomUUID().show}"))
        .withType(Subscription.Type.KeyShared)
        .withMode(Subscription.Mode.NonDurable)
        .build

      userEvents <- LoggingConsumer.make[UserTableEvent](pulsar, AppTopic.UserTable.make, subscription)

      routes = Routes(tableManager).routes

      userEventsProcessor = userEvents.process(incomingHandler.handleUser)
    } yield (userEventsProcessor, routes)
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    val app = for {
      cfg               <- Resource.eval(Config.load[IO])
      pulsar            <- Pulsar.default(cfg.pulsarURL)
      redis             <- RedisClient[IO].from(cfg.redisConfig.value)
      (stream, httpApi) <- TableHandlerApp.resource(pulsar, redis)
    } yield stream.drain -> httpApi

    app.use {
      case (stream, httpApi) => HttpServer.build(Right(httpApi)).useForever *> stream.compile.drain
    }
  }
}
