package fr.table

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.toShow
import cr.pulsar.{Subscription, Pulsar => PulsarClient}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import doobie.util.transactor.Transactor
import fr.adapter.http.HttpServer
import fr.adapter.pulsar.{AppTopic, LoggingConsumer, LoggingProducer, Pulsar}
import fr.adapter.redis.StateStorage
import fr.domain.TableId
import fr.domain.table.TableEvent.TableEventEnvelope
import fr.domain.table.{TableState}
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import org.http4s.HttpRoutes

import java.util.UUID
import fr.table.TableManager.Result

object TableHandlerApp {
  def resource(transactor: Transactor[IO], pulsar: PulsarClient.Underlying, redis: RedisClient): Resource[IO, (fs2.Stream[IO, Unit], HttpRoutes[IO])] = {
    val stateStorage = StateStorage.postgres[TableId, TableState, Result]("tables", transactor, TableState.empty)

    val tableEventBroadcast = (e: TableEventEnvelope) => LoggingProducer.sharded[TableEventEnvelope](pulsar, AppTopic.TableEvent.make).use(_.send_(e))
    val dispatcher          = Dispatcher.make(tableEventBroadcast)

    val tableManager    = TableManager.make(stateStorage)
    val incomingHandler = IncomingHandler.make(tableManager, dispatcher)

    val subscription = Subscription.Builder
      .withName(Subscription.Name(s"table-handler"))
      .withType(Subscription.Type.KeyShared)
      .withMode(Subscription.Mode.NonDurable)
      .build

    val routes = Routes(tableManager, dispatcher).routes

    for {
      userEventConsumer <- LoggingConsumer.make[UserTableActionEnvelope](pulsar, AppTopic.UserTableAction.make, subscription)
      userEventsProcessor = userEventConsumer.subscribe.parEvalMap(8) { msg =>
        incomingHandler.handleUser(msg.payload).onError { e =>
          IO.println(s"Couldn't handle ${msg.payload}: ${e.getMessage}") *> userEventConsumer.nack(msg.id)
        } <* userEventConsumer.ack(msg.id)
      }
    } yield (userEventsProcessor, routes)
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
      (stream, httpApi) <- TableHandlerApp.resource(transactor, pulsar, redis)
    } yield stream.drain -> httpApi

    app.use {
      case (stream, httpApi) => HttpServer.build(Right(httpApi)).useForever *> stream.compile.drain
    }
  }
}
