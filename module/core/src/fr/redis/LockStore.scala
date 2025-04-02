package fr.redis

import cats.Eq
import cats.effect.IO
import cats.effect.kernel.Resource
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import dev.profunktor.redis4cats.effects.SetArg.{Existence, Ttl}
import dev.profunktor.redis4cats.effects.SetArgs
import fr.redis.LockStore.{Lock, LockKey}
import io.circe.parser.{decode => jsonDecode}
import io.circe.syntax._
import io.estatico.newtype.macros.newtype

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

trait LockStore {
  def setValue(key: LockKey, value: UUID, exp: FiniteDuration): IO[Lock]
  def del(key: LockKey): IO[Unit]
  def get(key: LockKey): IO[Option[UUID]]
  def getDel(key: LockKey): IO[Option[UUID]]
  def ttl(key: LockKey): IO[Option[FiniteDuration]]
}

object LockStore {
  @newtype case class LockKey(value: String)

  sealed trait Lock
  object Lock {
    case object AlreadySet extends Lock
    case object Set        extends Lock

    implicit val eqv: Eq[Lock] = Eq.fromUniversalEquals
  }

  def make(client: RedisClient): Resource[IO, LockStore] =
    Redis[IO].fromClient(client, RedisCodec.Utf8).map { redis =>
      new LockStore {
        def buildKey(key: LockKey): String = key.value

        override def setValue(key: LockKey, value: UUID, exp: FiniteDuration): IO[Lock] =
          redis
            .set(buildKey(key), value.asJson.noSpaces, SetArgs(Existence.Nx, Ttl.Ex(exp)))
            .map {
              case false => Lock.AlreadySet
              case true  => Lock.Set
            }

        override def ttl(key: LockKey): IO[Option[FiniteDuration]] =
          redis.ttl(buildKey(key))

        override def del(key: LockKey): IO[Unit] =
          redis.del(buildKey(key)).void

        override def get(key: LockKey): IO[Option[UUID]] =
          redis.get(buildKey(key)).map(_.flatMap(jsonDecode[UUID](_).toOption))

        override def getDel(key: LockKey): IO[Option[UUID]] = {
          def _getDel(keyString: String) = redis.unsafe(_.getdel(keyString)).map(Option(_))
          _getDel(buildKey(key)).map(_.flatMap(jsonDecode[UUID](_).toOption))
        }
      }
    }
}
