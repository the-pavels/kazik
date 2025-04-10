package fr.domain.table

import doobie.Meta
import doobie.postgres.circe.jsonb.implicits.{pgDecoderGet, pgEncoderPut}
import fr.domain.game.roulette.Game
import fr.domain.{TableId, UserId}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class TableState(id: TableId, users: Set[UserId], game: Option[Game])

object TableState {
  implicit val codec: Codec[TableState] = deriveCodec
  implicit val meta: Meta[TableState]   = new Meta(pgDecoderGet, pgEncoderPut)

  def empty(tid: TableId): TableState = TableState(tid, Set.empty, None)
}
