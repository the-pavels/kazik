package fr.adapter.redis

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import dev.profunktor.redis4cats.effects.ScriptOutputType
import doobie._
import doobie.implicits._
import io.circe.Codec
import io.circe.parser.{decode => jsonDecode}
import io.circe.syntax.EncoderOps

import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

sealed trait StateStorage[K, S, R] {
  def get(key: K): IO[S]
  def put(key: K, state: S): IO[Unit]
  def updateState(key: K)(f: S => (S, R)): IO[R]
}

object StateStorage {
  final val CAS_SCRIPT_CONTENT =
    """
      |local current_val = redis.call('GET', KEYS[1])
      |local expected_val = ARGV[1]
      |local new_val = ARGV[2]
      |local nil_marker = '__EXPECT_NIL__' -- Choose a marker unlikely to be real data
      |
      |if current_val == false then -- Key doesn't exist
      |  if expected_val == nil_marker then
      |    redis.call('SET', KEYS[1], new_val)
      |    return 1
      |  else
      |    return 0 -- Expected something else, but key was nil
      |  end
      |elseif current_val == expected_val then
      |  redis.call('SET', KEYS[1], new_val)
      |  return 1
      |else
      |  return 0 -- Current value didn't match expected
      |end
      """.stripMargin

  final val CAS_SCRIPT_NIL_MARKER = "__EXPECT_NIL__"

  def redis[K, S: Codec, R](client: RedisClient, mkKey: K => String, default: K => S): Resource[IO, StateStorage[K, S, R]] =
    for {
      redisDefault  <- Redis[IO].fromClient(client, RedisCodec.Utf8)
      casScriptSha1 <- Resource.eval(redisDefault.scriptLoad(CAS_SCRIPT_CONTENT))
    } yield new StateStorage[K, S, R] {
      override def get(key: K): IO[S] =
        redisDefault
          .get(mkKey(key))
          .map(_.flatMap(jsonDecode[S](_).toOption).getOrElse(default(key)))

      override def put(key: K, state: S): IO[Unit] =
        redisDefault.set(mkKey(key), state.asJson.noSpaces).void

      override def updateState(key: K)(f: S => (S, R)): IO[R] = {
        val k          = mkKey(key)
        val maxRetries = 1000

        def attempt(tries: Int): IO[R] = {
          if (tries >= maxRetries) {
            IO.raiseError(new RuntimeException(s"Max Lua CAS retries ($maxRetries) reached for key $k"))
          } else {
            // 1. Read current value (outside transaction/Lua)
            redisDefault
              .get(k)
              .flatMap { currentJsonOpt =>
                val currentState = currentJsonOpt
                  .flatMap(jsonDecode[S](_).toOption)
                  .getOrElse(default(key))
                val expectedJson = currentJsonOpt.getOrElse(CAS_SCRIPT_NIL_MARKER)

                // 2. Compute new state using the user function
                val (updatedState, result) = f(currentState)
                val updatedJson            = updatedState.asJson.noSpaces

                // 3. Attempt atomic CAS using evalSha with the pre-loaded script digest
                redisDefault
                  .evalSha(
                    casScriptSha1,
                    output = ScriptOutputType.Integer[String],
                    keys = List(k),
                    values = List(expectedJson, updatedJson)
                  )
                  .flatMap {
                    case 1L =>
                      IO.pure(result)
                    case 0L =>
                      IO(Random.nextInt(10)).flatMap(s => IO.sleep(s.millis)) *> attempt(tries + 1)
                    case other =>
                      IO.raiseError(new RuntimeException(s"Unexpected Lua script return value: $other for key $k"))
                  }
              }
              .recoverWith {
                case NonFatal(e) =>
                  IO.println(s"WARN: Error during update attempt $tries for key $k: ${e.getMessage}") *>
                    IO.raiseError(new RuntimeException(s"Error during update attempt for key $k after $tries tries: ${e.getMessage}", e))
              }
          }
        }

        attempt(tries = 0)
      }
    }

  def postgres[K: Meta, S: Meta, R](table: String, transactor: Transactor[IO], default: K => S): StateStorage[K, S, R] = new StateStorage[K, S, R] {
    private def _get(key: K): ConnectionIO[Option[S]] = {
      (fr"SELECT state FROM " ++ Fragment.const(table) ++ fr" WHERE id = $key")
        .query[S]
        .option
    }
    private def _put(key: K, state: S): Update0 =
      (fr"INSERT INTO " ++
        Fragment.const(table) ++
        fr" (id, state) VALUES ($key, $state) ON CONFLICT (id) DO UPDATE SET state = $state").update

    override def get(key: K): IO[S]              = _get(key).transact(transactor).map(_.getOrElse(default(key)))
    override def put(key: K, state: S): IO[Unit] = _put(key, state).run.transact(transactor).void
    override def updateState(key: K)(f: S => (S, R)): IO[R] = {
      val update = for {
        currentState <- _get(key)
        state              = currentState.getOrElse(default(key))
        (newState, result) = f(state)
        _ <- _put(key, newState).run
      } yield result

      update.transact(transactor)
    }
  }
}
