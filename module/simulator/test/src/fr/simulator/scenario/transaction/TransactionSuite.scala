package fr.simulator.scenario.transaction

import cats.effect.IO
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.domain.{TableId, UserId}
import fr.redis.{LockStore, Transactor}
import fr.simulator.scenario.setup.IOSpec
import fr.table.StateStorage
import cats.syntax.all._

import java.util.UUID
import scala.util.Random

class TransactionSuite extends IOSpec {
  test("Transactor should take care of concurrent changes") {
    val changes = (1 to 100).toList
    val tid     = TableId(UUID.randomUUID())
    val uid     = UserId(UUID.randomUUID())
    val stateStorage = for {
      redis     <- RedisClient[IO].from("redis://localhost:6379")
      lockStore <- LockStore.make(redis)
      transactor = Transactor.make(lockStore)
      stateStorage <- StateStorage.make(redis, transactor)
    } yield stateStorage

    stateStorage.use { stateStorage =>
      val updateState = stateStorage.updateState(tid) { state =>
        state.copy(users = state.users :+ uid)
      }

      changes.parTraverse(_ => updateState) *> stateStorage.get(tid).map { latestState =>
        assertEquals(latestState.map(_.users.size), Some(100))
      }
    }
  }
}
