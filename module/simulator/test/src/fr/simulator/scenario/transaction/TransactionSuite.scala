package fr.simulator.scenario.transaction

import cats.effect.{IO, Resource}
import cats.syntax.all._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.adapter.redis.StateStorage
import fr.domain.table.TableState
import fr.domain.{TableId, UserId}
import fr.simulator.scenario.setup.IOSpec

import java.util.UUID

class TransactionSuite extends IOSpec {
  test("Transactor should take care of concurrent changes") {
    val number  = 1000
    val changes = (1 to number).toList
    val tid     = TableId(UUID.randomUUID())
    def uid     = UserId(UUID.randomUUID())
    val stateStorage = for {
      redis        <- RedisClient[IO].from("redis://localhost:6379")
      x            <- Redis[IO].fromClient(redis, RedisCodec.Utf8)
      _            <- Resource.eval(x.flushAll)
      stateStorage <- StateStorage.redis[TableId, TableState](redis, _.asKey, TableState.empty)
    } yield stateStorage

    stateStorage.use { stateStorage =>
      val updateState = stateStorage.updateState(tid) { state =>
        println("Number : " + state.users.size)
        state.copy(users = state.users :+ uid)
      }

      changes.parTraverse(_ => updateState) *> stateStorage.get(tid).map { latestState =>
        assertEquals(latestState.map(_.users.toSet.size), Some(number))
      }
    }
  }
}
