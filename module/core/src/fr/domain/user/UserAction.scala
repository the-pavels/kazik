package fr.domain.user

import fr.domain.{EventId, UserId}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant

sealed trait UserAction {
  def uid: UserId
}
object UserAction {
  case class SocketOpened(id: EventId, uid: UserId, ts: Instant)                          extends UserAction
  case class UserActionReceived(id: EventId, uid: UserId, action: UserInput, ts: Instant) extends UserAction
  case class SocketClosed(id: EventId, uid: UserId, ts: Instant)                          extends UserAction

  implicit val codec: Codec[UserAction] = deriveCodec
}
