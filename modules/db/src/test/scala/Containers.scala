package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

object Containers:

  private def parseConfig(cont: PostgreSQLContainer): IO[PostgresConfig] =
    IO:
      val jdbcUrl = java.net.URI.create(cont.jdbcUrl.substring(5)).nn
      PostgresConfig(
        jdbcUrl.getHost.nn,
        jdbcUrl.getPort.nn,
        cont.username.nn,
        cont.password.nn,
        cont.databaseName.nn,
        10
      )

  private def postgresContainer: Resource[IO, PostgreSQLContainer] =
    val start = IO(
      PostgreSQLContainer(dockerImageNameOverride = DockerImageName.parse("postgres:15.3").nn)
    ).flatTap(cont => IO(cont.start()))

    Resource.make(start)(cont => IO(cont.stop()))

  def createDb: Resource[IO, Db] =

    given Logger[IO] = NoOpLogger[IO]

    postgresContainer
      .evalMap(parseConfig)
      .flatMap: config =>
        DbResource
          .instance(config)
          .map(x => Db(x.postgres))
