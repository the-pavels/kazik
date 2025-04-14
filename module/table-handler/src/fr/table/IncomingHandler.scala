package fr.table

import cats.effect.IO
import cats.syntax.all._
import fr.domain.user.UserTableAction.UserTableActionEnvelope
import fr.domain.user.{UserTableAction => UTA}

sealed trait IncomingHandler {
  def handle(actions: List[UserTableActionEnvelope]): IO[Unit]
}

object IncomingHandler {

  def make(tableManager: TableManager, dispatcher: Dispatcher): IncomingHandler = new IncomingHandler {
    override def handle(actions: List[UserTableActionEnvelope]): IO[Unit] =
      actions
        .groupBy(_.action.tid)
        .toList
        .parTraverse_ {
          case (tid, actions) =>
            val joinTableUids    = actions.collect { case UserTableActionEnvelope(_, uid, UTA.JoinTable(_), _) => uid }.toSet
            val leaveTableUids   = actions.collect { case UserTableActionEnvelope(_, uid, UTA.LeaveTable(_), _) => uid }.toSet
            val placeBetActions  = actions.collect { case UserTableActionEnvelope(_, uid, UTA.PlaceBet(_, gid, bet), _) => (uid, gid, bet) }
            val removeBetActions = actions.collect { case UserTableActionEnvelope(_, uid, UTA.RemoveBets(_, gid), _) => (uid, gid) }

            val results = for {
              r1 <- if (joinTableUids.nonEmpty) tableManager.joinTable2(tid, joinTableUids).map(_.some) else IO.none
              r2 <- if (leaveTableUids.nonEmpty) tableManager.leaveTable2(tid, leaveTableUids).map(_.some) else IO.none
              r3 <- if (placeBetActions.nonEmpty) tableManager.placeBets(tid, placeBetActions).map(_.some) else IO.none
              r4 <- if (removeBetActions.nonEmpty) tableManager.removeBets(tid, removeBetActions).map(_.some) else IO.none
            } yield List(r1, r2, r3, r4).collect { case Some(res) => res }.flatMap(_.events)

            results.flatMap(dispatcher.dispatchAll)
        }
    // TODO parTraverse_ will fail the IO for the whole chunk if processing for *any* table fails.
    // If you need partial success (e.g., ack messages for successful tables, nack for failed ones),
    // you would need more complex error handling here, perhaps using .attempt within parTraverse_
    // and returning a more detailed result type instead of Unit.
  }
}
