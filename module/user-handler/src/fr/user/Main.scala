package fr.user

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.toShow
import cr.pulsar.{Subscription, Pulsar => PulsarClient}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import doobie.util.transactor.Transactor
import fr.adapter.pulsar.{AppTopic, LoggingConsumer, LoggingProducer, Pulsar}
import fr.adapter.redis.StateStorage
import fr.domain.UserId
import fr.domain.table.TableEvent
import fr.domain.user.UserEvent.UserEventEnvelope
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import fr.domain.user.{UserAction, UserState}
import fr.user.UserManager.Result

import java.util.UUID

object UserHandlerApp {
  def resource(transactor: Transactor[IO], pulsar: PulsarClient.Underlying, redis: RedisClient): Resource[IO, fs2.Stream[IO, Unit]] = {
    val stateStorage = StateStorage.postgres[UserId, UserState, Result]("users", transactor, UserState.empty)

    val userBroadcast = (uid: UserId) =>
      (e: UserEventEnvelope) => LoggingProducer.default[UserEventEnvelope](pulsar, AppTopic.ServerToClient(uid.value.show).make).use(_.send_(e))
    val tableBroadcast = (e: UserTableActionEnvelope) => LoggingProducer.sharded[UserTableActionEnvelope](pulsar, AppTopic.UserTableAction.make).use(_.send_(e))
    val dispatcher     = Dispatcher.make(userBroadcast, tableBroadcast)

    val userManager     = UserManager.make(stateStorage)
    val incomingHandler = IncomingHandler.make(userManager)

    val subscription1 = Subscription.Builder
      .withName(Subscription.Name(s"user-handler-${UUID.randomUUID().show}"))
      .withType(Subscription.Type.Failover) // TODO
      .withMode(Subscription.Mode.NonDurable)
      .build

    val subscription2 = Subscription.Builder // todo name
      .withName(Subscription.Name(s"table-user-handler-${UUID.randomUUID().show}"))
      .withType(Subscription.Type.Failover) // TODO
      .withMode(Subscription.Mode.NonDurable)
      .build

    for {
      userEvents  <- LoggingConsumer.make[UserAction](pulsar, AppTopic.ClientToServer.make, subscription1)
      tableEvents <- LoggingConsumer.make[TableEvent](pulsar, AppTopic.TableEvent.make, subscription2)

      userActionProcessor = userEvents.process(incomingHandler.fromUser)
      tableEventProcessor = tableEvents.process(incomingHandler.fromTable)
    } yield userActionProcessor.concurrently(tableEventProcessor).evalMap(dispatcher.dispatch)
  }
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    val app = for {
      cfg    <- Resource.eval(Config.load)
      pulsar <- Pulsar.default(cfg.pulsarURL)
      redis  <- RedisClient[IO].from(cfg.redisConfig.value)
      transactor = Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        cfg.postgresDb.url.value,
        cfg.postgresDb.user.value,
        cfg.postgresDb.password.value,
        None
      )

      stream <- UserHandlerApp.resource(transactor, pulsar, redis)
    } yield stream.compile.drain

    app.useForever
  }
}
