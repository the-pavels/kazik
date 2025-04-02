package fr.simulator.scenario

import fr.simulator.scenario.setup.Scenario
import fr.simulator.scenario.setup.Scenario._
import fr.simulator.table.AutomatedTableManager
import fr.simulator.user.AutomatedUser

class HappyPathSuite extends Scenario {
  test("User can open WS connection") {
    val uid = genUserId
    val tid = genTableId

    val tableManager = new AutomatedTableManager(client)
    val user         = new AutomatedUser(uid, client)

    tableManager.createTable(tid) *> (tableManager.start(tid) &> user.play(tid))
  }
}
