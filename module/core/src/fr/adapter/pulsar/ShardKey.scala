package fr.adapter.pulsar

import cats.syntax.all._
import cr.pulsar.{ShardKey => SK}
import fr.domain.table.TableEvent.TableEventEnvelope
import fr.domain.user.UserAction
import fr.domain.user.UserTableAction.UserTableActionEnvelope

import java.nio.charset.StandardCharsets
import java.util.UUID

trait ShardKey[A] {
  def shardKey: A => SK
}

object ShardKey extends ShardKeySyntax {

  @inline def apply[A](implicit ev: ShardKey[A]): ShardKey[A] = ev

  private val shardBy: UUID => SK = s => SK.Of(s.show.getBytes(StandardCharsets.UTF_8))

  implicit val userActionShardKey: ShardKey[UserAction] =
    new ShardKey[UserAction] {
      def shardKey: UserAction => SK = e => shardBy(e.uid.value)
    }

  implicit val userTableActionShardKey: ShardKey[UserTableActionEnvelope] =
    new ShardKey[UserTableActionEnvelope] {
      def shardKey: UserTableActionEnvelope => SK = e => shardBy(e.event.tid.value)
    }

  implicit val tableEvent: ShardKey[TableEventEnvelope] = new ShardKey[TableEventEnvelope] {
    override def shardKey: TableEventEnvelope => SK = e => shardBy(e.event.uid.value)
  }
}

trait ShardKeySyntax {

  implicit class SyntaxOps[A: ShardKey](a: A) {
    def shardKey: SK = ShardKey[A].shardKey(a)
  }

}
