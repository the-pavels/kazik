package fr.adapter.redis

import ciris.ConfigDecoder
import io.estatico.newtype.macros.newtype

object Config {
  @newtype
  case class RedisURI(value: String)
  object RedisURI {
    implicit val configDecoder: ConfigDecoder[String, RedisURI] =
      ConfigDecoder[String].map(RedisURI(_))
  }
}
