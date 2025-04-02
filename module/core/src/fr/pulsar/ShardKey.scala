package fr.pulsar

import cats.syntax.all._
import cr.pulsar.{ShardKey => SK}
import fr.domain.Event.{TableUserEvent, UserEvent, UserTableEvent}

import java.nio.charset.StandardCharsets
import java.util.UUID

trait ShardKey[A] {
  def shardKey: A => SK
}

object ShardKey extends ShardKeySyntax {

  @inline def apply[A](implicit ev: ShardKey[A]): ShardKey[A] = ev

  private val shardBy: UUID => SK = s => SK.Of(s.show.getBytes(StandardCharsets.UTF_8))

  implicit val incomingEventShardKey: ShardKey[UserEvent] =
    new ShardKey[UserEvent] {
      def shardKey: UserEvent => SK = e => shardBy(e.uid.value)
    }

  implicit val outgoingUserEvent: ShardKey[UserTableEvent] =
    new ShardKey[UserTableEvent] {
      def shardKey: UserTableEvent => SK = e => shardBy(e.tid.value)
    }

  implicit val tableEvent: ShardKey[TableUserEvent] = new ShardKey[TableUserEvent] {
    override def shardKey: TableUserEvent => SK = e => shardBy(e.uid.value)
  }
}

trait ShardKeySyntax {

  implicit class SyntaxOps[A: ShardKey](a: A) {
    def shardKey: SK = ShardKey[A].shardKey(a)
  }

}
