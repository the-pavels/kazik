package fr.simulator.scenario

import fr.simulator.scenario.setup.Scenario
import fr.simulator.scenario.setup.Scenario._
import fr.simulator.table.AutomatedTableManager
import fr.simulator.user.AutomatedUser
import cats.syntax.all._

class HappyPathSuite extends Scenario {
  test("User can open WS connection") {
    def uid = genUserId
    val tid = genTableId

    val tableManager = new AutomatedTableManager(client)
    val users        = (1 to 100).map(_ => new AutomatedUser(uid, client)).toList

    tableManager.start(tid) &> users.parTraverse(_.play(tid))
  }
}
