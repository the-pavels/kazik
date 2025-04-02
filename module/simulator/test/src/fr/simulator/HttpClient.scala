package fr.simulator

import cats.effect.{IO, Resource}
import cats.syntax.all._
import fr.domain.UserId
import HttpClient.{host, port}
import io.circe.{Decoder, Encoder}
import io.circe.syntax.EncoderOps
import io.circe.parser.decode
import io.netty.handler.ssl.SslContextBuilder
import org.asynchttpclient.{DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.client3.{Response, SttpBackend, asWebSocketAlwaysUnsafe, basicRequest}
import sttp.model.Uri

import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class HttpClient(verifySslCerts: Boolean) {
  private val logger = Slf4jLogger.getLoggerFromName[IO](getClass.getSimpleName)

  private val clientConfig =
    if (verifySslCerts) {
      new DefaultAsyncHttpClientConfig.Builder().build()
    } else {
      class TrustAllX509TrustManager extends X509TrustManager {
        def getAcceptedIssuers = new Array[X509Certificate](0)
        def checkClientTrusted(
            certs: Array[X509Certificate],
            authType: String
        ): Unit = {}
        def checkServerTrusted(
            certs: Array[X509Certificate],
            authType: String
        ): Unit = {}
      }

      val sslCtx =
        SslContextBuilder.forClient
          .trustManager(new TrustAllX509TrustManager)
          .build

      new DefaultAsyncHttpClientConfig.Builder().setSslContext(sslCtx).build
    }

  val client: DefaultAsyncHttpClient =
    new DefaultAsyncHttpClient(clientConfig)

  val httpBackend: SttpBackend[IO, Any] =
    AsyncHttpClientCatsBackend.usingClient[IO](client)

  def openWs(url: String, uid: UserId): Resource[IO, WebSocketClient] = {
    def uri(url: String): IO[Uri] =
      Uri
        .parse(url)
        .leftMap(e => new IllegalArgumentException(s"Couldn't parse URL $url: $e"))
        .liftTo[IO]

    def wsClient(wsBackend: SttpBackend[IO, Fs2Streams[IO] with WebSockets]): Resource[IO, WebSocketClient] = {
      val client = logger.info(s"Opened WS connection [${uid.show}]") >>
        uri(url).flatMap { url =>
          basicRequest
            .response(asWebSocketAlwaysUnsafe[IO])
            .get(url)
            .send(wsBackend)
            .flatMap(resp => WebSocketClient.make(uid, resp.body))
        }
      Resource.make(client)(ws => logger.info(s"Closed WS connection [${uid.show}]") >> ws.close)
    }

    for {
      wsBackend <- AsyncHttpClientFs2Backend.resourceUsingConfig[IO](clientConfig)
      ws        <- wsClient(wsBackend)
      _         <- ws.listenEvents.background
    } yield ws
  }

  def get[T: Decoder](
      url: String
  ): IO[T] =
    basicRequest
      .get(Uri(host, port, url.split("/").toList))
      .send(httpBackend)
      .map(_.body.toOption.flatMap(b => decode[T](b).toOption).getOrElse(throw new Exception("Failed to decode response")))

  def post(
      url: String
  ): IO[Response[Either[String, String]]] =
    basicRequest
      .post(Uri(host, port, url.split("/").toList))
      .send(httpBackend)

  def postEntity[A: Encoder](
      url: String,
      body: A
  ): IO[Response[Either[String, String]]] =
    basicRequest
      .body(body.asJson.noSpaces)
      .post(Uri(host, port, url.split("/").toList))
      .send(httpBackend)

  def close: IO[Unit] =
    logger.info("Closed HTTP connection") >> httpBackend.close() >> IO(client.close())
}

object HttpClient {
  val host = "localhost"
  val port = 8080
}
