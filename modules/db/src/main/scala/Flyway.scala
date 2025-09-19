package fide
package db

import cats.effect.*
import cats.syntax.all.*
import dumbo.logging.{ LogLevel, Logger as DumboLogger }
import dumbo.{ ConnectionConfig, Dumbo }
import org.typelevel.log4cats.Logger
import org.typelevel.otel4s.trace.Tracer
trait Flyway:
  def migrate: IO[Unit]

object Flyway:

  given Logger[IO] => DumboLogger[IO]:
    override def apply(level: LogLevel, msg: => String): IO[Unit] =
      level match
        case LogLevel.Info => Logger[IO].info(msg)
        case LogLevel.Warn => Logger[IO].warn(msg)

  def apply(config: PostgresConfig)(using Tracer[IO], Logger[IO]): Flyway = new:
    def migrate =
      Dumbo
        .withResourcesIn[IO]("db/migration")
        .apply(
          connection = ConnectionConfig(
            host = config.host.toString(),
            port = config.port.value,
            user = config.user,
            database = config.database,
            password = config.password.some,
            ssl = if config.ssl then ConnectionConfig.SSL.Trusted else ConnectionConfig.SSL.None
          ),
          defaultSchema = config.schema
        )
        .runMigration
        .flatMap: result =>
          Logger[IO].info(s"Migration completed with ${result.migrationsExecuted} migrations")
