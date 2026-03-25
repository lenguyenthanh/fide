package fide.cli

import cats.effect.IO
import cats.syntax.all.*
import ciris.*
import ciris.http4s.*
import com.comcast.ip4s.*
import fide.db.PostgresConfig
import fide.types.{ PositiveInt, YearMonth }
import io.github.iltotore.iron.*
import io.github.iltotore.iron.ciris.given
import io.github.iltotore.iron.constraint.all.*

import java.nio.file.Path

case class IngestConfig(
    csvDir: Path,
    startMonth: Option[YearMonth],
    endMonth: Option[YearMonth],
    postgres: PostgresConfig,
    historyChunkSize: PositiveInt
)

object IngestConfig:

  def parse(args: List[String]): IO[IngestConfig] =
    args match
      case Nil            => IO.raiseError(IllegalArgumentException("Missing required argument: <csvDir>"))
      case csvDir :: rest =>
        parseFlags(rest) match
          case Left(err)           => IO.raiseError(IllegalArgumentException(err))
          case Right((start, end)) =>
            (postgresConfig, historyChunkSize).tupled
              .load[IO]
              .map: (pg, historyChunkSize) =>
                IngestConfig(
                  csvDir = Path.of(csvDir),
                  startMonth = start,
                  endMonth = end,
                  postgres = pg,
                  historyChunkSize = historyChunkSize
                )

  private def parseFlags(
      args: List[String]
  ): Either[String, (Option[YearMonth], Option[YearMonth])] =
    def loop(
        remaining: List[String],
        start: Option[YearMonth],
        end: Option[YearMonth]
    ): Either[String, (Option[YearMonth], Option[YearMonth])] =
      remaining match
        case Nil                        => Right((start, end))
        case "--start" :: value :: tail =>
          YearMonth.fromString(value).flatMap(ym => loop(tail, Some(ym), end))
        case "--end" :: value :: tail =>
          YearMonth.fromString(value).flatMap(ym => loop(tail, start, Some(ym)))
        case unknown :: _ => Left(s"Unknown flag: $unknown")
    loop(args, None, None)

  private def postgresConfig =
    val host     = env("POSTGRES_HOST").or(prop("postgres.host")).as[Host].default(ip"0.0.0.0")
    val port     = env("POSTGRES_PORT").or(prop("postgres.port")).as[Port].default(port"5432")
    val user     = env("POSTGRES_USER").or(prop("postgres.user")).as[String]
    val password = env("POSTGRES_PASSWORD").or(prop("postgres.password")).as[String]
    val database = env("POSTGRES_DATABASE").or(prop("postgres.database")).as[String]
    val max      = env("POSTGRES_MAX").or(prop("postgres.max")).as[PositiveInt].default(10)
    val schema   = env("POSTGRES_SCHEMA").or(prop("postgres.schema")).as[String].default("fide")
    val debug    = env("POSTGRES_DEBUG").or(prop("postgres.debug")).as[Boolean].default(false)
    val ssl      = env("POSTGRES_SSL").or(prop("postgres.ssl")).as[Boolean].default(false)
    (host, port, user, password, database, max, schema, debug, ssl).parMapN(PostgresConfig.apply)

  private def historyChunkSize =
    env("HISTORY_INSERT_SIZE").or(prop("history.insert.size")).as[PositiveInt].default(100)
