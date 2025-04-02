package fr.simulator.user

import cats.effect.{IO, Resource}
import fr.domain.UserId
import fr.simulator.{HttpClient, WebSocketClient}

object UserClient {
  def start(uid: UserId, client: HttpClient): Resource[IO, WebSocketClient] =
    client.openWs("ws://localhost:8080/sticky/ws?userId=" + uid.value, uid)
}
