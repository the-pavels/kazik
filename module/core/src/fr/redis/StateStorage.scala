package fr.redis

import cats.Show
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import dev.profunktor.redis4cats.effects.ScriptOutputType
import doobie.Transactor
import io.circe.Codec
import io.circe.parser.{decode => jsonDecode}
import io.circe.syntax.EncoderOps

import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal
import cats.syntax.all._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

sealed trait StateStorage[K, S] {
  def get(key: K): IO[Option[S]]
  def put(key: K, state: S): IO[Unit]
  def updateState(key: K)(f: S => S): IO[S]
  def updateStateF(key: K)(f: S => IO[S]): IO[S]
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

  def redis[K, S: Codec](client: RedisClient, mkKey: K => String, default: K => S): Resource[IO, StateStorage[K, S]] =
    for {
      redisDefault  <- Redis[IO].fromClient(client, RedisCodec.Utf8)
      casScriptSha1 <- Resource.eval(redisDefault.scriptLoad(CAS_SCRIPT_CONTENT))
      _             <- Resource.eval(IO.println(s"Loaded CAS Lua script with SHA1: $casScriptSha1")) // Optional: Log success
    } yield new StateStorage[K, S] {
      override def get(key: K): IO[Option[S]] =
        redisDefault
          .get(mkKey(key))
          .map(_.flatMap(jsonDecode[S](_).toOption))

      override def put(key: K, state: S): IO[Unit] =
        redisDefault.set(mkKey(key), state.asJson.noSpaces).void

      override def updateState(key: K)(f: S => S): IO[S] =
        updateStateF(key)(s => IO.pure(f(s)))

      override def updateStateF(key: K)(f: S => IO[S]): IO[S] = {
        val k          = mkKey(key)
        val maxRetries = 1000

        def attempt(tries: Int): IO[S] = {
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
                f(currentState).flatMap { updatedState =>
                  val updatedJson = updatedState.asJson.noSpaces

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
                        IO.pure(updatedState)
                      case 0L =>
                        IO(Random.nextInt(10)).flatMap(s => IO.sleep(s.millis)) *> attempt(tries + 1)
                      case other =>
                        IO.raiseError(new RuntimeException(s"Unexpected Lua script return value: $other for key $k"))
                    }
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

//  def postgres[K: Show, S: Codec](table: String, transactor: Transactor[IO], default: K => S): StateStorage[K, S] = new StateStorage[K, S] {
//    def _get(key: K): ConnectionIO[Option[S]] = sql"""SELECT * FROM $table WHERE id = $key""".query[S].option
//    def _put(key: K, state: S): Update0       = sql"""INSERT INTO $table (id, state) VALUES ($key, $state) ON CONFLICT (id) DO UPDATE SET state = $state""".update
//
//    override def get(key: K): IO[Option[S]]            = _get(key).transact(transactor)
//    override def put(key: K, state: S): IO[Unit]       = _put(key, state).run.transact(transactor).void
//    override def updateState(key: K)(f: S => S): IO[S] = updateStateF(key)(s => IO.pure(f(s)))
//    override def updateStateF(key: K)(f: S => IO[S]): IO[S] =
//      for {
//        currentState <- _get(key)
//        state        = currentState.getOrElse(default(key))
//      } yield {
//        updatedState
//      }
//  }
}
