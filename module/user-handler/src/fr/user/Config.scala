package fr.user

import cats.effect.kernel.Async
import ciris._
import cr.pulsar.Pulsar.PulsarURL
import fr.domain.ConfigOps
import fr.pulsar._
import fr.redis.Config.RedisURI

case class Config(
    pulsarURL: PulsarURL,
    redisConfig: RedisURI
)

object Config {
  def value[F[_]]: ConfigValue[F, Config] =
    for {
      pulsarConfig <- env("PULSAR_SERVICE_URL").as[PulsarURL].withDefault("pulsar://localhost:6650")
      redisUri     <- env("REDIS_URI").as[RedisURI].withDefault("redis://localhost:6379")
      cfg = Config(pulsarConfig, redisUri)
    } yield cfg

  def load[F[_]: Async]: F[Config] = value[F].load

}
