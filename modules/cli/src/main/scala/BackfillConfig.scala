package fide.cli

import cats.data.Validated
import cats.effect.IO
import cats.syntax.all.*
import ciris.*
import ciris.http4s.*
import com.comcast.ip4s.*
import com.monovore.decline.*
import fide.broadcast.LichessConfig
import fide.db.PostgresConfig
import fide.types.PositiveInt
import io.github.iltotore.iron.*
import io.github.iltotore.iron.ciris.given
import io.github.iltotore.iron.constraint.all.*
import org.http4s.Uri
import org.http4s.implicits.*

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.*

/** Config + CLI args for `fide-cli live-ratings backfill` (design CLI §4). */
case class BackfillCliOpts(
    since: Option[LocalDate],
    tourId: Option[String],
    dryRun: Boolean
)

case class BackfillConfig(
    postgres: PostgresConfig,
    lichess: LichessConfig,
    cli: BackfillCliOpts
)

object BackfillConfig:

  /** `--since YYYY-MM-DD` is parsed as UTC midnight (decision CLI #15). */
  given Argument[LocalDate] =
    Argument.from("YYYY-MM-DD"): s =>
      Validated
        .catchNonFatal(LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE))
        .leftMap(e => s"invalid date '$s': ${e.getMessage}")
        .toValidatedNel

  private val sinceOpt =
    Opts
      .option[LocalDate](
        "since",
        "Override current-cycle cutoff (UTC midnight of the given date).",
        "s"
      )
      .orNone

  private val tourIdOpt =
    Opts
      .option[String](
        "tour-id",
        "Backfill a specific tour only. Ignores --since and ingests all finished+rated rounds of that tour."
      )
      .orNone

  private val dryRunOpt =
    Opts
      .flag(
        "dry-run",
        "Run Phase 1 + Phase 2 discovery and emit candidate round list without writing to DB."
      )
      .orFalse

  val opts: Opts[BackfillCliOpts] =
    (sinceOpt, tourIdOpt, dryRunOpt).mapN(BackfillCliOpts.apply)

  def load(cli: BackfillCliOpts): IO[BackfillConfig] =
    (postgresConfig, lichessConfig).tupled
      .load[IO]
      .map: (pg, lichess) =>
        BackfillConfig(postgres = pg, lichess = lichess, cli = cli)

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

  private def lichessConfig =
    val apiToken =
      env("LICHESS_API_TOKEN").or(prop("lichess.api.token")).as[String].secret
    val baseUri =
      env("LICHESS_BASE_URI").or(prop("lichess.base.uri")).as[Uri].default(uri"https://lichess.org")
    val timeoutSec =
      env("LICHESS_REQUEST_TIMEOUT").or(prop("lichess.request.timeout")).as[PositiveInt].default(30)
    (apiToken, baseUri, timeoutSec).parMapN: (token, uri, seconds) =>
      LichessConfig(
        baseUri = uri,
        apiToken = token.value,
        requestTimeout = seconds.seconds,
        maxConcurrentRounds = 2,
        retryMaxAttempts = 3,
        retryBaseDelay = 1.second,
        retryLoggingEnabled = true
      )
