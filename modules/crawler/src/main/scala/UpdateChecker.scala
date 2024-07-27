package fide.crawler

import cats.effect.IO
import org.http4s.*
import org.http4s.client.Client
import org.http4s.headers.`Last-Modified`
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger

trait UpdateChecker:
  def lastUpdate: IO[Option[String]]

object UpdateChecker:
  def apply(client: Client[IO])(using Logger[IO]): UpdateChecker = new:

    def lastUpdate: IO[Option[String]] =
      client
        .run(request)
        .use(extract.andThen(IO.pure))
        .handleErrorWith(e => Logger[IO].error(e)("Failed to get last update date: $e").as(None))

  private val request = Request[IO](
    method = Method.HEAD,
    uri = uri"http://ratings.fide.com/download/players_list.zip"
  )

  private val extract: Response[IO] => Option[String] =
    _.headers.get[`Last-Modified`].map(_.date.toString)
