package fr.domain

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UserState(id: UserId, balance: BigDecimal, blockedBalance: BigDecimal, tables: List[TableId])
object UserState {
  def empty(uid: UserId): UserState  = UserState(uid, 100000, 0, List.empty)
  implicit val codec: Codec[UserState] = deriveCodec
}
