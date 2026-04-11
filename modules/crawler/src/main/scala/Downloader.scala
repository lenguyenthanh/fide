package fide.crawler

import cats.effect.IO
import cats.syntax.all.*
import fide.domain.*
import fide.types.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.*

trait Downloader:
  def fetch: fs2.Stream[IO, (CrawlPlayer, Option[NewFederation], String)]

object Downloader:
  val downloadUrl     = uri"http://ratings.fide.com/download/players_list.zip"
  val downloadTimeout = 10.minutes
  def currentYear     = java.time.Year.now.getValue

  lazy val request = Request[IO](
    method = Method.GET,
    uri = downloadUrl
  )

  def apply(client: Client[IO])(using Logger[IO]): Downloader = new:

    def fetch =
      client
        .stream(request)
        .switchMap(_.body)
        .through(Decompressor.decompress)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .drop(1) // first line is header
        .evalMapFilter: line =>
          parseLine(line).map(_.map((player, fed) => (player, fed, line)))
        .interruptAfter(downloadTimeout)

  def parseLine(line: String): Logger[IO] ?=> IO[Option[(CrawlPlayer, Option[NewFederation])]] =

    inline def parse(line: String): IO[Option[(CrawlPlayer, Option[NewFederation])]] =
      parsePlayer(line).traverse: player =>
        player.federationId.traverse(findFederation(_, player.fideId.some)).map(player -> _)

    def findFederation(id: FederationId, fideId: Option[FideId]): IO[NewFederation] =
      Federation.all.get(id) match
        case None       => warn"Unknown federation: $id for player: $fideId".as(NewFederation(id, id.value))
        case Some(name) => NewFederation(id, name).pure

    IO(line.trim.nonEmpty)
      .ifM(parse(line), none.pure[IO])
      .handleErrorWith(e => Logger[IO].error(e)(s"Error while parsing line: $line").as(none))

  // shamelessly copied (with some minor modificaton) from: https://github.com/lichess-org/lila/blob/8033c4c5a15cf9bb2b36377c3480f3b64074a30f/modules/fide/src/main/FidePlayerSync.scala#L131
  def parsePlayer(line: String): Option[CrawlPlayer] =
    inline def string(start: Int, end: Int): Option[String] =
      line.substring(start, end).trim.some.filter(_.nonEmpty)
    inline def number(start: Int, end: Int): Option[Int]    = string(start, end).flatMap(_.toIntOption)
    inline def rating(start: Int, end: Int): Option[Rating] = string(start, end) >>= Rating.fromString
    inline def kFactor(start: Int)                          = number(start, start + 2).filter(_ > 0)
    inline def playerName(): Option[String]                 = sanitizeName(line.substring(15, 76))
    inline def fideId(): Option[FideId]                     = string(0, 15) >>= FideId.option
    inline def federationId(): Option[FederationId]         =
      string(76, 79).map(_.toUpperCase).filter(_ != FederationId.NoneFederationId) >>= FederationId.option

    (fideId(), playerName()).mapN: (fid, name) =>
      CrawlPlayer(
        fideId = fid,
        name = name,
        title = string(84, 89) >>= Title.apply,
        womenTitle = string(89, 94) >>= Title.apply,
        otherTitles = string(94, 109).fold(Nil)(OtherTitle.applyToList),
        standard = rating(113, 117),
        standardK = kFactor(123),
        rapid = rating(126, 132),
        rapidK = kFactor(136),
        blitz = rating(139, 145),
        blitzK = kFactor(149),
        gender = string(79, 82) >>= Gender.apply,
        birthYear = number(152, 156).filter(y => y > 1000 && y < currentYear),
        active = string(158, 160).filter(_.contains("i")).isEmpty,
        federationId = federationId()
      )

  def sanitizeName(name: String): Option[String] =
    name
      .split("\\W+")
      .filter(_.nonEmpty)
      .mkString(" ")
      .some
      .filter(_.sizeIs > 2)

object Decompressor:

  import de.lhns.fs2.compress.*
  val defaultChunkSize = 1024 * 4

  def decompress: fs2.Pipe[IO, Byte, Byte] =
    _.through(ArchiveSingleFileDecompressor(Zip4JUnarchiver.make[IO](defaultChunkSize)).decompress)
