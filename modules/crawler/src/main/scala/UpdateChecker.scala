package fide
package crawler

import cats.effect.IO
import cats.syntax.all.*
import fide.db.Store
import fs2.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait UpdateChecker:
  def shouldUpdate: IO[Boolean]

object UpdateChecker:
  val fideLastUpdateKey = "fide_last_upate_key"

  def instance(store: Store, client: Client[IO])(using Logger[IO]): UpdateChecker =
    UpdateChecker(store, FideScraper(client))

  def apply(store: Store, scraper: FideScraper)(using Logger[IO]): UpdateChecker = new:
    def shouldUpdate: IO[Boolean] =
      (lastLocalUpdate, scraper.lastUpdate).parFlatMapN: (local, remote) =>
        (local, remote) match
          case (Some(l), Some(r)) if l == r => IO(false)
          case (Some(l), Some(r))           => updateLastUpdate(r).as(true)
          case (None, Some(r))              => updateLastUpdate(r).as(true)
          case (_, None)                    => error"Failed to get remote update time".as(true)

    private def lastLocalUpdate: IO[Option[String]] =
      store.get(fideLastUpdateKey)

    private def updateLastUpdate(value: String): IO[Unit] =
      store.put(fideLastUpdateKey, value)

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

  private val preDateString =
    """<i class="fa   fa-download" style="color: #50618d; padding-right: 10px;"></i> <a href=http://ratings.fide.com/download/players_list.zip class=tur>TXT format</a> <small>("""

  private val downloadUrl = "http://ratings.fide.com/download/players_list.zip"

  private val request = Request[IO](
    method = Method.GET,
    uri = uri"https://ratings.fide.com/download_lists.phtml"
  )

  private def extract(res: Response[IO]): IO[Option[String]] =
    res.bodyText
      .map(_.linesIterator.filter(_.contains(downloadUrl)))
      .filter(_.nonEmpty)
      .map(_.nextOption)
      .map(_.map(_.drop(preDateString.length).takeWhile(_ != ',')))
      .compile
      .last
      .map(_.flatten)
