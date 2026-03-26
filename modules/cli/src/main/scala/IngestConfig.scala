package fide.cli

import cats.data.Validated
import cats.effect.IO
import cats.syntax.all.*
import ciris.*
import ciris.http4s.*
import com.comcast.ip4s.*
import com.monovore.decline.*
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

/** CLI-only arguments parsed by decline (no env vars). */
case class IngestCliOpts(
    csvDir: Path,
    startMonth: Option[YearMonth],
    endMonth: Option[YearMonth]
)

object IngestConfig:

  given Argument[YearMonth] =
    Argument.from("yyyy-MM"): s =>
      YearMonth.fromString(s).fold(Validated.invalidNel(_), Validated.valid)

  private val csvDirOpt =
    Opts.argument[String]("csvDir").map(Path.of(_))

  private val startOpt =
    Opts.option[YearMonth]("start", "Only ingest files from this month onwards", "s").orNone

  private val endOpt =
    Opts.option[YearMonth]("end", "Only ingest files up to this month", "e").orNone

  val opts: Opts[IngestCliOpts] =
    (csvDirOpt, startOpt, endOpt).mapN(IngestCliOpts.apply)

  def load(cli: IngestCliOpts): IO[IngestConfig] =
    (postgresConfig, historyChunkSize).tupled
      .load[IO]
      .map: (pg, chunkSize) =>
        IngestConfig(
          csvDir = cli.csvDir,
          startMonth = cli.startMonth,
          endMonth = cli.endMonth,
          postgres = pg,
          historyChunkSize = chunkSize
        )

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
