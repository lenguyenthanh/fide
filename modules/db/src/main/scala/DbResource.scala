package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fs2.io.net.Network
import org.typelevel.log4cats.Logger
import skunk.*
import skunk.codec.text.*
import skunk.implicits.*

class DbResource private (val postgres: Resource[IO, Session[IO]])

object DbResource:

  given org.typelevel.otel4s.trace.Tracer[IO] = org.typelevel.otel4s.trace.Tracer.noop[IO]

  def instance(postgresConf: PostgresConfig)(using Logger[IO]): Resource[IO, DbResource] =

    def checkPostgresConnection(postgres: Resource[IO, Session[IO]]): IO[Unit] =
      postgres.use: session =>
        session
          .unique(sql"select version();".query(text))
          .flatMap: v =>
            Logger[IO].info(s"Connected to Postgres $v")

    def mkPostgresResource(c: PostgresConfig): Resource[IO, Resource[IO, Session[IO]]] =
      Session
        .Builder[IO]
        .withHost(c.host.toString())
        .withPort(c.port.value)
        .withUserAndPassword(c.user, c.password)
        .withDatabase(c.database)
        .withSSL(if c.ssl then SSL.Trusted else SSL.None)
        .withTypingStrategy(TypingStrategy.SearchPath)
        .withConnectionParameters(Map("search_path" -> c.schema) ++ Session.DefaultConnectionParameters)
        .pooled(c.max)
        .evalTap(checkPostgresConnection)

    Flyway(postgresConf).migrate.toResource *>
      mkPostgresResource(postgresConf).map(DbResource(_))
