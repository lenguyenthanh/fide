package fide

import cats.effect.IO
import cats.syntax.all.*
import ciris.*
import ciris.http4s.*
import com.comcast.ip4s.*

object AppConfig:

  def load: IO[AppConfig] = appConfig.load[IO]

  def appConfig = (
    HttpServerConfig.config,
    PostgresConfig.config
  ).parMapN(AppConfig.apply)

case class AppConfig(
    server: HttpServerConfig,
    postgres: db.PostgresConfig
)

case class HttpServerConfig(host: Host, port: Port, shutdownTimeout: Int)

object HttpServerConfig:
  def host            = env("HTTP_HOST").or(prop("http.host")).as[Host].default(ip"0.0.0.0")
  def port            = env("HTTP_PORT").or(prop("http.port")).as[Port].default(port"9669")
  def shutdownTimeout = env("HTTP_SHUTDOWN_TIMEOUT").or(prop("http.shutdown.timeout")).as[Int].default(30)
  def config          = (host, port, shutdownTimeout).parMapN(HttpServerConfig.apply)

object PostgresConfig:
  // max concurrent sessions
  private def max    = env("POSTGRES_MAX").or(prop("postgres.max")).as[Int]
  private def schema = env("POSTGRES_SCHEMA").or(prop("postgres.schema")).as[String].default("fide")
  private def debug  = env("POSTGRES_DEBUG").or(prop("postgres.debug")).as[Boolean].default(false)
  private def ssl    = env("POSTGRES_SSL").or(prop("postgres.ssl")).as[Boolean].default(false)

  def config =
    (PostgresDriverLoader.loadDriver, max, schema, debug, ssl).parMapN:
      case ((host, port, user, password, database), max, schema, debug, ssl) =>
        db.PostgresConfig(host, port, user, password, database, max, schema, debug, ssl)

type DriverUrl = (Host, Port, String, String, String)

object PostgresDriverLoader:
  private def url =
    env("DATABASE_URL")
      .or(prop("database.url"))
      .as[DriverUrl]

  private def host     = env("POSTGRES_HOST").or(prop("postgres.host")).as[Host].default(ip"128.0.0.1")
  private def port     = env("POSTGRES_PORT").or(prop("postgres.port")).as[Port].default(port"5432")
  private def user     = env("POSTGRES_USER").or(prop("postgres.user")).as[String]
  private def password = env("POSTGRES_PASSWORD").or(prop("postgres.password")).as[String]
  private def database = env("POSTGRES_DATABASE").or(prop("postgres.database")).as[String]
  val loadDriver       = url.or((host, port, user, password, database).tupled)

  given ConfigDecoder[String, DriverUrl] =
    ConfigDecoder.instance[String, DriverUrl]((_, url) => decode(url))

  def decode(url: String): Either[ConfigError, DriverUrl] =
    (for
      parsed   <- Either.catchNonFatal(java.net.URI(url)).toOption
      host     <- Host.fromString(parsed.getHost())
      port     <- Port.fromInt(parsed.getPort())
      userInfo <- Option(parsed.getUserInfo())
      dbName   <- Option(parsed.getPath().tail) // dropping the first slash
      userName <- Option(userInfo.split(":").apply(0))
      password <- Option(userInfo.split(":").apply(1))
    yield (host, port, userName, password, dbName))
      .toRight(ConfigError(s"Failed to parse DATABASE_URL $url"))
