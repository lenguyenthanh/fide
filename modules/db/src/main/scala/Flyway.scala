package fide
package db

import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import fly4s.*
import fly4s.data.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait Flyway:
  def migrate: IO[Unit]

object Flyway:

  def instance(fly4s: Fly4s[IO])(using Logger[IO]): Flyway = new:

    def migrate =
      info"Running Flyway migration" *>
        fly4s.migrate.flatMap: x =>
          info"Flyway migration result: $x"

  def module(config: FlywayConfig)(using Logger[IO]): Resource[IO, Flyway] =
    Fly4s
      .make[IO](
        url = config.url,
        user = config.user,
        password = config.password,
        config = Fly4sConfig(
          table = config.migrationsTable,
          locations = Locations(config.migrationsLocations),
          schemaNames = NonEmptyList.of(config.schema).some
        )
      )
      .map(instance)
