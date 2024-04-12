package fide.crawler

import cats.effect.IO
import fs2.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait FideScraper:
  def lastUpdate: IO[Option[String]]

// We need to find this line and extract the date "02 Apr 2024"
// <i class="fa   fa-download" style="color: #50618d; padding-right: 10px;"></i> <a href=http://ratings.fide.com/download/players_list.zip class=tur>TXT format</a> <small>(02 Apr 2024, Sz: 30.83 MB)</small> <br><br>
object FideScraper:
  // TODO: this is a terrible implementation, should probably use regex to get the date
  def apply(client: Client[IO])(using Logger[IO]): FideScraper = new:

    def lastUpdate: IO[Option[String]] =
      client
        .run(request)
        .use(extract)
        .handleErrorWith(e => error"Failed to get last update date: $e".as(None))

  private val preDateString =
    """<i class="fa   fa-download" style="color: #50618d; padding-right: 10px;"></i> <a href=http://ratings.fide.com/download/players_list.zip class=tur>TXT format</a> <small>("""

  private val downloadUrl = "http://ratings.fide.com/download/players_list.zip"

  private val request = Request[IO](
    method = Method.GET,
    uri = uri"https://ratings.fide.com/download_lists.phtml"
  )

  private val extract: Response[IO] => IO[Option[String]] =
    _.bodyText
      .map(_.linesIterator.filter(_.contains(downloadUrl)))
      .filter(_.nonEmpty)
      .map(_.next) // WARNING: This is fine only because this is placed after filter(_.nonEmpty)
      .map(_.drop(preDateString.length).takeWhile(_ != ','))
      .compile
      .last
