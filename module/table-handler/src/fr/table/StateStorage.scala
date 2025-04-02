package fr.table

import cats.effect.IO
import cats.effect.kernel.Resource
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import fr.domain.{TableId, TableState}
import fr.redis.Transactor
import io.circe.parser.{decode => jsonDecode}
import io.circe.syntax.EncoderOps

import java.util.UUID

sealed trait StateStorage {
  def get(tid: TableId): IO[Option[TableState]]
  def put(tid: TableId, state: TableState): IO[Unit]
  def getAll: IO[List[TableId]]

  def updateState(tid: TableId)(f: TableState => TableState): IO[TableState]
  def updateStateF(tid: TableId)(f: TableState => IO[TableState]): IO[TableState]
}

object StateStorage {
  def mkKey(tid: TableId): String = s"table:${tid.value}"

  def make(client: RedisClient, transactor: Transactor): Resource[IO, StateStorage] = Redis[IO].fromClient(client, RedisCodec.Utf8).map { redis =>
    new StateStorage {
      override def get(tid: TableId): IO[Option[TableState]]      = redis.get(mkKey(tid)).map(_.flatMap(jsonDecode[TableState](_).toOption))
      override def put(tid: TableId, state: TableState): IO[Unit] = redis.set(mkKey(tid), state.asJson.noSpaces).void

      override def updateState(tid: TableId)(f: TableState => TableState): IO[TableState] = updateStateF(tid)(f.andThen(IO.pure))

      override def updateStateF(tid: TableId)(f: TableState => IO[TableState]): IO[TableState] = transactor.withTransaction(tid.asLock) {
        for {
          stateOpt <- get(tid)
          state = stateOpt.getOrElse(TableState(tid, List.empty, None))
          updatedState <- f(state)
          _            <- put(tid, updatedState)
        } yield updatedState
      }

      override def getAll: IO[List[TableId]] =
        redis
          .keys(s"table:*")
          .map(_.map { key =>
            val uuid = key.split(":").last
            TableId(UUID.fromString(uuid))
          })

      def withTransaction(tid: TableId, f: TableState => TableState): IO[TableState] = {
        val key = mkKey(tid)
        redis.watch(key) *> get(tid).flatMap { stateOpt =>
          val state    = stateOpt.getOrElse(TableState(tid, List.empty, None))
          val newState = f(state)
          put(tid, newState).as(newState)
        } *> redis.exec
      }
    }
  }
}
