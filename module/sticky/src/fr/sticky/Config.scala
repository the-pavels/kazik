package fr.sticky

import cats.effect.kernel.Async
import ciris._
import cr.pulsar.Pulsar.PulsarURL
import fr.adapter.pulsar.cfgDecoderURL
import fr.domain.ConfigOps

case class Config(
    pulsarURL: PulsarURL
)

object Config {
  def value[F[_]]: ConfigValue[F, Config] =
    for {
      pulsarConfig <- env("PULSAR_SERVICE_URL").as[PulsarURL].withDefault("pulsar://localhost:6650")
      cfg = Config(pulsarConfig)
    } yield cfg

  def load[F[_]: Async]: F[Config] = value[F].load

}
