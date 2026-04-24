package fide

import cats.effect.IO
import cats.syntax.all.*
import ciris.*
import ciris.http4s.*
import com.comcast.ip4s.*
import fide.broadcast.LichessConfig
import fide.types.PositiveInt
import io.github.iltotore.iron.*
import io.github.iltotore.iron.ciris.given
import io.github.iltotore.iron.constraint.all.*
import org.http4s.Uri
import org.http4s.implicits.*

import scala.concurrent.duration.*

object AppConfig:

  def load: IO[AppConfig] = appConfig.load[IO]

  def appConfig = (
    HttpServerConfig.config,
    CrawlerConfig.config,
    CrawlerJobConfig.config,
    PostgresConfig.config,
    HistoryConfig.config,
    LichessConfigLoader.config,
    LiveRatingJobConfig.config
  ).parMapN(AppConfig.apply)

case class AppConfig(
    server: HttpServerConfig,
    crawler: fide.crawler.CrawlerConfig,
    crawlerJob: CrawlerJobConfig,
    postgres: db.PostgresConfig,
    history: HistoryConfig,
    lichess: LichessConfig,
    liveRatingJob: LiveRatingJobConfig
)

/** Loader for the live-rating job (design §12). Poll interval defaults to 5 minutes. */
case class LiveRatingJobConfig(
    delayInSeconds: Int :| Positive0,
    pollIntervalInMinutes: PositiveInt
)

object LiveRatingJobConfig:
  private def delay =
    env("LIVE_RATING_JOB_DELAY").or(prop("live.rating.job.delay")).as[Int :| Positive0].default(10)
  private def interval =
    env("LIVE_RATING_JOB_POLL_INTERVAL")
      .or(prop("live.rating.job.poll.interval"))
      .as[PositiveInt]
      .default(5)
  def config = (delay, interval).parMapN(LiveRatingJobConfig.apply)

/** Ciris loader for `LichessConfig` (design §12 / decision #49).
  *
  * Env vars (5):
  *   - `LICHESS_API_TOKEN`  — required, no default. Personal access token.
  *   - `LICHESS_BASE_URI`   — default `https://lichess.org`.
  *   - `LICHESS_REQUEST_TIMEOUT` — seconds, default 30.
  *
  * Other fields (user agent, max concurrent rounds, retry attempts/delay,
  * retry logging) are compile-time defaults — decision #44.
  */
object LichessConfigLoader:
  private def apiToken =
    env("LICHESS_API_TOKEN").or(prop("lichess.api.token")).as[String].secret
  private def baseUri =
    env("LICHESS_BASE_URI").or(prop("lichess.base.uri")).as[Uri].default(uri"https://lichess.org")
  private def requestTimeoutSeconds =
    env("LICHESS_REQUEST_TIMEOUT").or(prop("lichess.request.timeout")).as[PositiveInt].default(30)

  def config =
    (apiToken, baseUri, requestTimeoutSeconds).parMapN: (token, uri, timeoutSec) =>
      LichessConfig(
        baseUri = uri,
        apiToken = token.value,
        requestTimeout = timeoutSec.seconds,
        maxConcurrentRounds = 2,
        retryMaxAttempts = 3,
        retryBaseDelay = 1.second,
        retryLoggingEnabled = true
      )

case class HttpServerConfig(host: Host, port: Port, shutdownTimeout: PositiveInt)

object HttpServerConfig:
  private def host            = env("HTTP_HOST").or(prop("http.host")).as[Host].default(ip"0.0.0.0")
  private def port            = env("HTTP_PORT").or(prop("http.port")).as[Port].default(port"9669")
  private def shutdownTimeout =
    env("HTTP_SHUTDOWN_TIMEOUT").or(prop("http.shutdown.timeout")).as[PositiveInt].default(30)
  def config = (host, port, shutdownTimeout).parMapN(HttpServerConfig.apply)

case class CrawlerJobConfig(delayInSeconds: Int :| Positive0, intervalInMinutes: PositiveInt)
object CrawlerJobConfig:
  private def delayInSeconds =
    env("CRAWLER_JOB_DELAY").or(prop("crawler.job.delay")).as[Int :| Positive0].default(3)
  private def intervalInMinutes =
    env("CRAWLER_JOB_INTERVAL").or(prop("crawler.job.interval")).as[PositiveInt].default(60)
  def config = (delayInSeconds, intervalInMinutes).parMapN(CrawlerJobConfig.apply)

object CrawlerConfig:
  private def chunkSize =
    env("CRAWLER_CHUNK_SIZE").or(prop("crawler.chunk.size")).as[PositiveInt].default(100)
  private def concurrentUpsert =
    env("CRAWLER_CONCURRENT_UPSERT").or(prop("crawler.concurrent.upsert")).as[PositiveInt].default(10)
  def config = (chunkSize, concurrentUpsert).parMapN(crawler.CrawlerConfig.apply)

object PostgresConfig:

  private def host     = env("POSTGRES_HOST").or(prop("postgres.host")).as[Host].default(ip"0.0.0.0")
  private def port     = env("POSTGRES_PORT").or(prop("postgres.port")).as[Port].default(port"5432")
  private def user     = env("POSTGRES_USER").or(prop("postgres.user")).as[String]
  private def password = env("POSTGRES_PASSWORD").or(prop("postgres.password")).as[String]
  private def database = env("POSTGRES_DATABASE").or(prop("postgres.database")).as[String]
  // max concurrent sessions
  private def max    = env("POSTGRES_MAX").or(prop("postgres.max")).as[PositiveInt]
  private def schema = env("POSTGRES_SCHEMA").or(prop("postgres.schema")).as[String].default("fide")
  private def debug  = env("POSTGRES_DEBUG").or(prop("postgres.debug")).as[Boolean].default(false)
  private def ssl    = env("POSTGRES_SSL").or(prop("postgres.ssl")).as[Boolean].default(false)

  def config =
    (host, port, user, password, database, max, schema, debug, ssl).parMapN(db.PostgresConfig.apply)

case class HistoryConfig(insertSize: PositiveInt)
object HistoryConfig:
  private def insertSize =
    env("HISTORY_INSERT_SIZE").or(prop("history.insert.size")).as[PositiveInt].default(100)
  def config = insertSize.map(HistoryConfig.apply)
