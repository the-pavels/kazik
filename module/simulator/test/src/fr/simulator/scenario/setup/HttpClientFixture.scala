package fr.simulator.scenario.setup

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import fr.simulator.HttpClient

trait HttpClientFixture { this: FunSuite =>
  private val clientFixture = new Fixture[HttpClient]("httpClient") {
    private var internalClient: HttpClient = null
    def apply()                            = internalClient

    override def beforeEach(context: BeforeEach): Unit = {
      internalClient = new HttpClient(verifySslCerts = false)
    }
    override def afterEach(context: AfterEach): Unit = {
      internalClient.close.unsafeRunSync()
      internalClient = null
    }
    override def afterAll(): Unit = {
      if (internalClient != null) {
        internalClient.close.unsafeRunSync()
      }
    }
  }

  // alias to avoid putting empty brackets in test scenarios
  def client = clientFixture()

  override def munitFixtures = List(clientFixture)
}
