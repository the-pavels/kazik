package fr.user

import cats.effect.IO
import ciris._
import cr.pulsar.Pulsar.PulsarURL
import fr.adapter.postgres.{DBConfigReader, PostgresConfig}
import fr.adapter.pulsar.cfgDecoderURL
import fr.domain.ConfigOps
import fr.adapter.redis.Config.RedisURI

case class Config(
    pulsarURL: PulsarURL,
    postgresDb: PostgresConfig,
    redisConfig: RedisURI
)

object Config {
  def load: IO[Config] =
    (for {
      pulsarConfig <- env("PULSAR_SERVICE_URL").as[PulsarURL].withDefault("pulsar://localhost:6650")
      redisUri     <- env("REDIS_URI").as[RedisURI].withDefault("redis://localhost:6379")
      db           <- DBConfigReader.get("roulette")
      cfg = Config(pulsarConfig, db, redisUri)
    } yield cfg).load

}
