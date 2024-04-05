package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import com.comcast.ip4s.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

object Containers:

  private def parseConfig(cont: PostgreSQLContainer): IO[PostgresConfig] =
    IO:
      val jdbcUrl = java.net.URI.create(cont.jdbcUrl.substring(5))
      PostgresConfig(
        Host.fromString(jdbcUrl.getHost).get,
        Port.fromInt(jdbcUrl.getPort).get,
        cont.username,
        cont.password,
        cont.databaseName,
        10,
        "fide",
        false
      )

  private def postgresContainer: Resource[IO, PostgreSQLContainer] =
    val start = IO(
      PostgreSQLContainer(dockerImageNameOverride = DockerImageName.parse("postgres:16.2-alpine3.19"))
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
