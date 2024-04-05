package fide
package crawler

import cats.effect.IO
import cats.syntax.all.*
import fide.db.Db
import fide.domain.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait Crawler:
  def crawl: IO[Unit]

object Crawler:

  def apply(db: Db, client: Client[IO])(using Logger[IO]): Crawler = new:
    def crawl: IO[Unit] = Crawler.crawl(db, client) *> info"Finished crawling"

  val uri = uri"http://ratings.fide.com/download/players_list.zip"

  lazy val request = Request[IO](
    method = Method.GET,
    uri = uri
  )

  private def crawl(db: Db, client: Client[IO]) =
    client
      .stream(request)
      .switchMap(_.body)
      .through(Decompressor.decompress)
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .drop(1) // first line is header
      // .evalTap(IO.println)
      .map(Crawler.parseLine)
      // .evalTap(IO.println)
      .collect:
        case Some(x) => x
      .parEvalMapUnordered(50)((player, federation) => db.upsert(player, federation))
      .compile
      .drain

  // shamelessly copied (with some minor modificaton) from: https://github.com/lichess-org/lila/blob/8033c4c5a15cf9bb2b36377c3480f3b64074a30f/modules/fide/src/main/FidePlayerSync.scala#L131
  private def parseLine(line: String): Option[(NewPlayer, Option[NewFederation])] =
    def string(start: Int, end: Int) = line.substring(start, end).trim.some.filter(_.nonEmpty)
    def number(start: Int, end: Int) = string(start, end).flatMap(_.toIntOption)
    for
      id   <- number(0, 15)
      name <- string(15, 76).map(_.filterNot(_.isDigit).trim)
      if name.sizeIs > 2
      title        = string(84, 89).flatMap(Title.apply)
      wTitle       = string(89, 105).flatMap(Title.apply)
      year         = number(152, 156).filter(_ > 1000)
      flags        = string(158, 159)
      federationId = string(76, 79)
    yield NewPlayer(
      id = id,
      name = name,
      title = Title.mostValuable(title, wTitle),
      standard = number(113, 117),
      rapid = number(126, 132),
      blitz = number(139, 145),
      year = year,
      active = !flags.contains("i")
    ) -> federationId.map(id => NewFederation(id, Federation.nameById(id)))

object Decompressor:

  import de.lhns.fs2.compress.*
  import fs2.Pipe
  val defaultChunkSize = 1024 * 4

  def decompress: Pipe[IO, Byte, Byte] =
    _.through(ArchiveSingleFileDecompressor(ZipUnarchiver.make[IO](defaultChunkSize)).decompress)
