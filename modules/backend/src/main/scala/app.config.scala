package fide

import cats.effect.IO
import cats.syntax.all.*
import ciris.*
import ciris.http4s.*
import com.comcast.ip4s.*
import fide.types.PositiveInt
import io.github.iltotore.iron.*
import io.github.iltotore.iron.ciris.given
import io.github.iltotore.iron.constraint.all.*

object AppConfig:

  def load: IO[AppConfig] = appConfig.load[IO]

  def appConfig = (
    HttpServerConfig.config,
    CrawlerConfig.config,
    CrawlerJobConfig.config,
    PostgresConfig.config
  ).parMapN(AppConfig.apply)

case class AppConfig(
    server: HttpServerConfig,
    crawler: fide.crawler.CrawlerConfig,
    crawlerJob: CrawlerJobConfig,
    postgres: db.PostgresConfig
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
    env("CRAWLER_CONCURRENT_UPSERT").or(prop("crawler.concurrent.upsert")).as[PositiveInt].default(40)
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
