package fide.crawler

import cats.effect.IO
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait FideScraper:
  def lastUpdate: IO[Option[String]]

object FideScraper:
  def apply(client: Client[IO])(using Logger[IO]): FideScraper = new:

    def lastUpdate: IO[Option[String]] =
      client
        .run(request)
        .use(extract.andThen(IO.pure))
        .handleErrorWith(e => error"Failed to get last update date: $e".as(None))

  private val request = Request[IO](
    method = Method.HEAD,
    uri = uri"http://ratings.fide.com/download/players_list.zip"
  )

  private val extract: Response[IO] => Option[String] = response =>
    response.headers
      .get(CIString("Last-Modified"))
      .map(_.head.value)
