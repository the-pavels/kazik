package fr.sticky

import cats.effect.IO
import cats.effect.kernel.Resource
import cr.pulsar.{Pulsar => PulsarClient}
import fr.adapter.pulsar.{AppTopic, LoggingProducer}
import fr.domain.user.UserAction

trait Dispatcher {
  def dispatch(e: UserAction): IO[Unit]
}

object Dispatcher {
  def make(client: PulsarClient.Underlying): Resource[IO, Dispatcher] =
    LoggingProducer.sharded[UserAction](client, AppTopic.ClientToServer.make).map { producer =>
      new Dispatcher {
        override def dispatch(e: UserAction): IO[Unit] = producer.send_(e)
      }
    }
}
