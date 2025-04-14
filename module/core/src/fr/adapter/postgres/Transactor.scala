package fr.adapter.postgres

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor

object Transactor {
  def make(postgresConfig: PostgresConfig): Resource[IO, HikariTransactor[IO]] = {
    val config = new HikariConfig()

    config.setDriverClassName("org.postgresql.Driver")
    config.setJdbcUrl(postgresConfig.url.value)
    config.setUsername(postgresConfig.user.value)
    config.setPassword(postgresConfig.password.value)

    config.setPoolName(s"glancy-db-pool")
    // The load is split between 2 service instances
    config.setMaximumPoolSize(25)
    config.setMinimumIdle(7)

    config.setIdleTimeout(300000)
    config.setMaxLifetime(1800000)
    config.setConnectionTimeout(30000)

    config.setAutoCommit(false)
    config.addDataSourceProperty("reWriteBatchedInserts", "true")
    config.addDataSourceProperty("tcpKeepAlive", "true")

    config.setValidationTimeout(10000)
    config.setLeakDetectionThreshold(15000)

    HikariTransactor.fromHikariConfig[IO](config)
  }
}
