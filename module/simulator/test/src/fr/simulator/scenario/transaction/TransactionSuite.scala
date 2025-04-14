package fr.simulator.scenario.transaction

import cats.effect.{IO, Resource}
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.adapter.postgres.{DBConfigReader, Transactor}
import fr.adapter.redis.StateStorage
import fr.domain.table.TableState
import fr.domain.{TableId, UserId}
import fr.simulator.scenario.setup.IOSpec
import fr.table.TableManager.Result

import java.util.UUID

class TransactionSuite extends IOSpec {
  val number  = 100
  val changes = (1 to number).toList
  val tid     = TableId(UUID.randomUUID())
  def uid     = UserId(UUID.randomUUID())

  test("Postgres transactor should take care of concurrent changes") {
    val cfg = Resource.eval(DBConfigReader.get("roulette").load[IO])

    cfg.flatMap(Transactor.make).use { transactor =>
      val stateStorage = StateStorage.postgres[TableId, TableState, Result]("tables", transactor, TableState.empty)

      val updateState = stateStorage.updateState(tid) { state =>
        val newState = state.copy(users = state.users + uid)
        newState -> Result(newState)
      }

      changes.parTraverse(_ => updateState) *> stateStorage.get(tid).map { latestState =>
        assertEquals(latestState.users.size, number)
      }
    }
  }

  test("Redis transactor should take care of concurrent changes") {
    val number  = 300
    val changes = (1 to number).toList
    val tid     = TableId(UUID.randomUUID())
    def uid     = UserId(UUID.randomUUID())
    val stateStorage = for {
      redis        <- RedisClient[IO].from("redis://localhost:6379")
      x            <- Redis[IO].fromClient(redis, RedisCodec.Utf8)
      _            <- Resource.eval(x.flushAll)
      stateStorage <- StateStorage.redis[TableId, TableState, Result](redis, _.asKey, TableState.empty)
    } yield stateStorage

    stateStorage.use { stateStorage =>
      val updateState = stateStorage.updateState(tid) { state =>
        val newState = state.copy(users = state.users + uid)
        newState -> Result(newState)
      }

      changes.parTraverse(_ => updateState) *> stateStorage.get(tid).map { latestState =>
        assertEquals(latestState.users.size, number)
      }
    }
  }
}
