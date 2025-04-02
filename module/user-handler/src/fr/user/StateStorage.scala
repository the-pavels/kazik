package fr.user

import cats.effect.IO
import cats.effect.kernel.Resource
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.domain.{UserId, UserState}
import io.circe.parser.{decode => jsonDecode}
import io.circe.syntax.EncoderOps

import scala.concurrent.duration.{DurationInt, FiniteDuration}

sealed trait StateStorage {
  def get(uid: UserId): IO[Option[UserState]]
  def put(uid: UserId, state: UserState): IO[Unit]
}

object StateStorage {
  private val Expiration: FiniteDuration = 8.hours // TODO quite random

  def mkKey(uid: UserId): String = s"user:${uid.value}"

  def make(client: RedisClient): Resource[IO, StateStorage] = Redis[IO].fromClient(client, RedisCodec.Utf8).map { redis =>
    new StateStorage {
      override def get(uid: UserId): IO[Option[UserState]] = redis.get(mkKey(uid)).map(_.flatMap(jsonDecode[UserState](_).toOption))

      override def put(uid: UserId, state: UserState): IO[Unit] = redis.setEx(mkKey(uid), state.asJson.noSpaces, Expiration).void
    }
  }
}
