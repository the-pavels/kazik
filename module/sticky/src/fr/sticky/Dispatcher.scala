package fr.sticky

import cats.effect.IO
import cats.effect.kernel.Resource
import cr.pulsar.{Pulsar => PulsarClient}
import fr.domain.Event.UserEvent
import fr.pulsar.{AppTopic, LoggingProducer}

trait Dispatcher {
  def dispatch(e: UserEvent): IO[Unit]
}

object Dispatcher {
  def make(client: PulsarClient.Underlying): Resource[IO, Dispatcher] =
    LoggingProducer.sharded[UserEvent](client, AppTopic.ClientToServer.make).map { producer =>
      new Dispatcher {
        override def dispatch(e: UserEvent): IO[Unit] = producer.send_(e)
      }
    }
}
