package fr.domain.user

import doobie.Meta
import doobie.postgres.circe.jsonb.implicits._
import fr.domain.TableId
import fr.domain.UserId
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class UserState(id: UserId, balance: BigDecimal, blockedBalance: Map[TableId, BigDecimal], tables: List[TableId])
object UserState {
  implicit val codec: Codec[UserState] = deriveCodec
  implicit val meta: Meta[UserState]   = new Meta(pgDecoderGet, pgEncoderPut)

  def empty(uid: UserId): UserState = UserState(uid, 100000, Map.empty, List.empty)
}
