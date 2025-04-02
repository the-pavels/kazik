package fr.user

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.toShow
import cr.pulsar.{Subscription, Pulsar => PulsarClient}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.domain.Event.{OutgoingUserEvent, TableUserEvent, UserEvent, UserTableEvent}
import fr.domain.UserId
import fr.pulsar.{AppTopic, LoggingConsumer, LoggingProducer, Pulsar}
import fr.redis.{LockStore, Transactor}
import fr.user.IncomingHandler.Incoming

import java.util.UUID

object UserHandlerApp {
  def resource(pulsar: PulsarClient.Underlying, redis: RedisClient): Resource[IO, fs2.Stream[IO, Unit]] =
    for {
      stateStorage <- StateStorage.make(redis)

      userBroadcast = (uid: UserId) =>
        (e: OutgoingUserEvent) => LoggingProducer.default[OutgoingUserEvent](pulsar, AppTopic.UserOutbox(uid.value.show).make).use(_.send_(e))
      tableBroadcast = (e: UserTableEvent) => LoggingProducer.sharded[UserTableEvent](pulsar, AppTopic.UserTable.make).use(_.send_(e))
      dispatcher     = Dispatcher.make(userBroadcast, tableBroadcast)

      lockStore <- LockStore.make(redis)
      transactor  = Transactor.make(lockStore)
      userManager = UserManager.make(stateStorage, transactor)

      incomingHandler = IncomingHandler.make(userManager, dispatcher)

      subscription1 = Subscription.Builder
        .withName(Subscription.Name(s"user-handler-${UUID.randomUUID().show}"))
        .withType(Subscription.Type.Failover) // TODO
        .withMode(Subscription.Mode.NonDurable)
        .build

      subscription2 = Subscription.Builder // todo name
        .withName(Subscription.Name(s"user-handler-${UUID.randomUUID().show}"))
        .withType(Subscription.Type.Failover) // TODO
        .withMode(Subscription.Mode.NonDurable)
        .build

      userEvents  <- LoggingConsumer.make[UserEvent](pulsar, AppTopic.UserInbox.make, subscription1)
      tableEvents <- LoggingConsumer.make[TableUserEvent](pulsar, AppTopic.TableUser.make, subscription2)

      userEventsProcessor     = userEvents.process(e => incomingHandler.process(Incoming.UserEvent(e)))
      userTableEventProcessor = tableEvents.process(e => incomingHandler.process(Incoming.TableEvent(e)))
    } yield userEventsProcessor.concurrently(userTableEventProcessor)
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    val app = for {
      cfg    <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.default(cfg.pulsarURL)
      redis  <- RedisClient[IO].from(cfg.redisConfig.value)
      stream <- UserHandlerApp.resource(pulsar, redis)
    } yield stream.compile.drain

    app.useForever
  }
}
