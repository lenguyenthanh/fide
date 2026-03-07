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

trait Downloader:
  def fetch: fs2.Stream[IO, (NewPlayerInfo, NewPlayerHistory)]

object Downloader:
  val downloadUrl = uri"http://ratings.fide.com/download/players_list.zip"
  val currentYear = java.time.Year.now.getValue

  lazy val request = Request[IO](
    method = Method.GET,
    uri = downloadUrl
  )

  def apply(client: Client[IO])(using Logger[IO]): Downloader = new:

    def fetch =
      val month = Month.current
      client
        .stream(request)
        .switchMap(_.body)
        .through(Decompressor.decompress)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .drop(1) // first line is header
        .evalMapFilter(parseLine(_, month))

  def parseLine(line: String, month: Short): Logger[IO] ?=> IO[Option[(NewPlayerInfo, NewPlayerHistory)]] =

    inline def parse(line: String): IO[Option[(NewPlayerInfo, NewPlayerHistory)]] =
      parsePlayer(line, month).traverse: (info, history) =>
        history.federationId.traverse_(warnUnknownFederation(_, info.id)).as((info, history))

    def warnUnknownFederation(id: FederationId, playerId: PlayerId): IO[Unit] =
      if Federation.all.contains(id) then IO.unit
      else warn"Unknown federation: $id for player: $playerId"

    IO(line.trim.nonEmpty)
      .ifM(parse(line), none.pure[IO])
      .handleErrorWith(e => Logger[IO].error(e)(s"Error while parsing line: $line").as(none))

  // shamelessly copied (with some minor modificaton) from: https://github.com/lichess-org/lila/blob/8033c4c5a15cf9bb2b36377c3480f3b64074a30f/modules/fide/src/main/FidePlayerSync.scala#L131
  def parsePlayer(line: String, month: Short): Option[(NewPlayerInfo, NewPlayerHistory)] =
    inline def string(start: Int, end: Int): Option[String] =
      line.substring(start, end).trim.some.filter(_.nonEmpty)
    inline def number(start: Int, end: Int): Option[Int]    = string(start, end).flatMap(_.toIntOption)
    inline def rating(start: Int, end: Int): Option[Rating] = string(start, end) >>= Rating.fromString
    inline def kFactor(start: Int)                          = number(start, start + 2).filter(_ > 0)
    inline def playerName(): Option[String]                 = sanitizeName(line.substring(15, 76))
    inline def playerId(): Option[PlayerId]                 = number(0, 15) >>= PlayerId.option
    inline def federationId(): Option[FederationId]         =
      string(76, 79).map(_.toUpperCase).filter(_ != "NON") >>= FederationId.option

    (playerId(), playerName()).mapN: (id, name) =>
      val info = NewPlayerInfo(
        id = id,
        name = name,
        gender = string(79, 82) >>= Gender.apply,
        birthYear = number(152, 156).filter(y => y > 1000 && y < currentYear)
      )
      val history = NewPlayerHistory(
        playerId = id,
        month = month,
        title = string(84, 89) >>= Title.apply,
        womenTitle = string(89, 94) >>= Title.apply,
        otherTitles = string(94, 109).fold(Nil)(OtherTitle.applyToList),
        federationId = federationId(),
        active = string(158, 160).filter(_.contains("i")).isEmpty,
        standard = rating(113, 117),
        standardK = kFactor(123),
        rapid = rating(126, 132),
        rapidK = kFactor(136),
        blitz = rating(139, 145),
        blitzK = kFactor(149)
      )
      (info, history)

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
