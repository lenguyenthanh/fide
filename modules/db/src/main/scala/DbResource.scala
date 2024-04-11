package fide
package db

import cats.*
import cats.effect.*
import cats.syntax.all.*
import fs2.io.net.Network
import natchez.Trace.Implicits.noop
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*
import skunk.*
import skunk.codec.text.*
import skunk.implicits.*

class DbResource private (val postgres: Resource[IO, Session[IO]])

object DbResource:

  def instance(postgresConf: PostgresConfig)(using Logger[IO]): Resource[IO, DbResource] =

    def checkPostgresConnection(postgres: Resource[IO, Session[IO]]): IO[Unit] =
      postgres.use: session =>
        session
          .unique(sql"select version();".query(text))
          .flatMap: v =>
            info"Connected to Postgres $v"

    def mkPostgresResource(c: PostgresConfig): SessionPool[IO] =
      Session
        .pooled[IO](
          host = c.host.toString(),
          port = c.port.value,
          user = c.user,
          password = c.password.some,
          database = c.database,
          max = c.max,
          ssl = if c.ssl then SSL.Trusted else SSL.None,
          strategy = Strategy.SearchPath,
          parameters = Map("search_path" -> c.schema) ++ Session.DefaultConnectionParameters
        )
        .evalTap(checkPostgresConnection)

    Flyway
      .module(postgresConf.toFlywayConfig)
      .evalTap(_.migrate)
      .flatMap(_ => mkPostgresResource(postgresConf).map(DbResource(_)))
