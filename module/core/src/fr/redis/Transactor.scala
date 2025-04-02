package fr.redis

import cats.effect.IO
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.redis.LockStore.{Lock, LockKey}
import io.circe.Decoder
import io.circe.parser.{decode => jsonDecode}

import java.util.UUID
import scala.concurrent.duration.{DurationDouble, DurationInt, FiniteDuration}

trait Transactor {
  def withTransaction[T: Decoder](key: LockKey)(f: IO[T]): IO[T]
}

// TODO that's not a production ready solution, just a simple way to implement transactions with redis. consider wrapping redisson
object Transactor {
  def make(
      lockStore: LockStore,
      maxDelay: FiniteDuration = 50.millis,
      baseDelay: FiniteDuration = 5.millis,
      exponentialFactor: Double = 2.0,
      maxAttempts: Int = 10000000,
      lockTTL: FiniteDuration = 1.minute
  ): Transactor = new Transactor {
    val maxDelayMillis  = maxDelay.toMillis.toDouble
    val baseDelayMillis = baseDelay.toMillis.toDouble

    override def withTransaction[T: Decoder](key: LockKey)(f: IO[T]): IO[T] = {
      def run(attempt: Int, lockValue: UUID): IO[T] = {
        if (attempt > maxAttempts) {
          IO.raiseError(new RuntimeException(s"Failed to acquire lock after $maxAttempts attempts"))
        } else {
          val delay =
            math.min(maxDelayMillis, baseDelayMillis * math.pow(exponentialFactor, attempt - 1.0)).millis

          for {
            lock <- lockStore.setValue(key, lockValue, lockTTL)
            res <- lock match {
              case Lock.AlreadySet => IO.sleep(delay) >> run(attempt + 1, lockValue)
              case Lock.Set =>
                f.flatMap { r =>
                    lockStore.get(key).flatMap {
                      case Some(retrievedValue) if retrievedValue == lockValue => lockStore.del(key).as(r)
                      case _                                                   => IO.unit.as(r)
                    }
                  }
                  .handleErrorWith { err =>
                    lockStore.del(key) >> IO.raiseError(err)
                  }
            }
          } yield res
        }
      }

      IO(UUID.randomUUID()).flatMap(run(1, _))
    }
  }
}
