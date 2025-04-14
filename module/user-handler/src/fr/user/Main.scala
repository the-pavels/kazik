package fr.user

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.toShow
import cr.pulsar.{Subscription, Pulsar => PulsarClient}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.adapter.pulsar.{AppTopic, LoggingConsumer, LoggingProducer, Pulsar}
import fr.adapter.redis.StateStorage
import fr.domain.UserId
import fr.domain.table.TableEvent.TableEventEnvelope
import fr.domain.user.UserEvent.UserEventEnvelope
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import fr.domain.user.{UserAction, UserState}
import fr.user.UserManager.Result

object UserHandlerApp {
  def resource(pulsar: PulsarClient.Underlying, redis: RedisClient): Resource[IO, fs2.Stream[IO, Unit]] = {
    for {
      stateStorage <- StateStorage.redis[UserId, UserState, Result](redis, _.asKey, UserState.empty)

      userBroadcast = (uid: UserId) =>
        (e: UserEventEnvelope) => LoggingProducer.default[UserEventEnvelope](pulsar, AppTopic.UserEvent(uid.value.show).make).use(_.send_(e))
      tableBroadcast = (e: UserTableActionEnvelope) => LoggingProducer.sharded[UserTableActionEnvelope](pulsar, AppTopic.UserTableAction.make).use(_.send_(e))
      dispatcher     = Dispatcher.make(userBroadcast, tableBroadcast)

      userManager     = UserManager.make(stateStorage)
      incomingHandler = IncomingHandler.make(userManager)

      userActionSubscription = Subscription.Builder
        .withName(Subscription.Name(s"user-handler"))
        .withType(Subscription.Type.KeyShared)
        .withMode(Subscription.Mode.NonDurable)
        .build

      tableEventSubscription = Subscription.Builder
        .withName(Subscription.Name(s"table-event-handler"))
        .withType(Subscription.Type.KeyShared)
        .withMode(Subscription.Mode.NonDurable)
        .build
      userActionConsumer <- LoggingConsumer.make[UserAction](pulsar, AppTopic.UserAction.make, userActionSubscription)
      tableEventConsumer <- LoggingConsumer.make[TableEventEnvelope](pulsar, AppTopic.TableEvent.make, tableEventSubscription)

      userActionProcessor = userActionConsumer.subscribe.parEvalMap(8) { msg =>
        incomingHandler.fromUser(msg.payload).onError { e =>
          IO.println(s"Couldn't handle ${msg.payload}: ${e.getMessage}") *> userActionConsumer.nack(msg.id)
        } <* userActionConsumer.ack(msg.id)
      }
      tableEventProcessor = tableEventConsumer.subscribe.parEvalMap(8) { msg =>
        incomingHandler.fromTable(msg.payload).onError { e =>
          IO.println(s"Couldn't handle ${msg.payload}: ${e.getMessage}") *> tableEventConsumer.nack(msg.id)
        } <* tableEventConsumer.ack(msg.id)
      }
    } yield fs2.Stream(userActionProcessor, tableEventProcessor).parJoinUnbounded.evalMap(dispatcher.dispatch)
  }
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    val app = for {
      cfg    <- Resource.eval(Config.load)
      pulsar <- Pulsar.default(cfg.pulsarURL)
      redis  <- RedisClient[IO].from(cfg.redisConfig.value)
      stream <- UserHandlerApp.resource(pulsar, redis)
    } yield stream.compile.drain

    app.useForever
  }
}
