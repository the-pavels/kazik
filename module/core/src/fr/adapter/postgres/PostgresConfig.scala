package fr.adapter.postgres

import ciris.ConfigDecoder
import PostgresConfig._
import io.estatico.newtype.macros.newtype

case class PostgresConfig(
    db: PostgresDb,
    host: PostgresHost,
    port: PostgresPort,
    user: PostgresUser,
    password: PostgresPassword,
    maxConnections: PostgresMaxConnections,
    useTls: PostgresUseTLS
) {
  def url: PostgresUrl = PostgresUrl(s"jdbc:postgresql://${host.value}:${port.value}/${db.value}?targetServerType=primary")
}

object PostgresConfig {
  @newtype
  case class PostgresDb(value: String)
  object PostgresDb {
    implicit val configDecoder: ConfigDecoder[String, PostgresDb] =
      ConfigDecoder[String].map(PostgresDb(_))
  }

  @newtype
  case class PostgresHost(value: String)
  object PostgresHost {
    implicit val configDecoder: ConfigDecoder[String, PostgresHost] =
      ConfigDecoder[String].map(PostgresHost(_))
  }

  @newtype
  case class PostgresPort(value: Int)
  object PostgresPort {
    implicit val configDecoder: ConfigDecoder[String, PostgresPort] =
      ConfigDecoder[String].map(s => PostgresPort(s.toInt))
  }

  @newtype
  case class PostgresUser(value: String)
  object PostgresUser {
    implicit val configDecoder: ConfigDecoder[String, PostgresUser] =
      ConfigDecoder[String].map(PostgresUser(_))
  }

  @newtype
  case class PostgresPassword(value: String)
  object PostgresPassword {
    implicit val configDecoder: ConfigDecoder[String, PostgresPassword] =
      ConfigDecoder[String].map(PostgresPassword(_))
  }

  @newtype case class PostgresUrl(value: String)

  @newtype
  case class PostgresMaxConnections(value: Int)
  object PostgresMaxConnections {
    implicit val configDecoder: ConfigDecoder[String, PostgresMaxConnections] =
      ConfigDecoder[String].map(s => PostgresMaxConnections(s.toInt))
  }

  @newtype
  case class PostgresUseTLS(value: Boolean)
  object PostgresUseTLS {
    implicit val configDecoder: ConfigDecoder[String, PostgresUseTLS] =
      ConfigDecoder[String].map(s => PostgresUseTLS(s.toBoolean))
  }
}
