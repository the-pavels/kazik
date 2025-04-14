package fr.adapter.postgres

import cats.effect.IO
import cats.implicits.catsSyntaxTuple6Parallel
import ciris._
import fr.domain.ConfigOps
import PostgresConfig._

object DBConfigReader {
  def get(name: String): ConfigValue[IO, PostgresConfig] =
    (
      env(s"DB_HOST").as[PostgresHost].withDefault("localhost"),
      env(s"DB_PORT").as[PostgresPort].withDefault(5432),
      env(s"DB_USER").as[PostgresUser].withDefault("fr"),
      env(s"DB_PASSWORD").as[PostgresPassword].withDefault("mysecretpassword"),
      env(s"DB_MAX_CONNECTIONS").as[PostgresMaxConnections].withDefault(10),
      env(s"DB_USE_TLS").as[PostgresUseTLS].withDefault(false)
    ).parMapN(
      PostgresConfig(
        PostgresDb(name),
        _,
        _,
        _,
        _,
        _,
        _
      )
    )

}
