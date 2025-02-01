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

type PlayerInfo = (NewPlayer, Option[NewFederation])
trait Downloader:
  def fetch: fs2.Stream[IO, PlayerInfo]

object Downloader:
  val downloadUrl = uri"http://ratings.fide.com/download/players_list.zip"

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
        .evalMap(parseLine)
        .unNone

  def parseLine(line: String): Logger[IO] ?=> IO[Option[(NewPlayer, Option[NewFederation])]] =

    inline def parse(line: String): IO[Option[(NewPlayer, Option[NewFederation])]] =
      parsePlayer(line).traverse: (player, federationId) =>
        federationId.flatTraverse(findFederation(_, player.id)).map(fed => (player, fed))

    IO(line.trim.nonEmpty)
      .ifM(parse(line), none.pure[IO])
      .handleErrorWith(e => Logger[IO].error(e)(s"Error while parsing line: $line").as(none))

  def findFederation(id: FederationId, playerId: PlayerId): Logger[IO] ?=> IO[Option[NewFederation]] =
    Federation.nameById(id) match
      case None =>
        warn"cannot find federation: $id for player: $playerId" *> NewFederation(id, id.value).some.pure
      case Some(name) => NewFederation(id, name).some.pure

  // shamelessly copied (with some minor modificaton) from: https://github.com/lichess-org/lila/blob/8033c4c5a15cf9bb2b36377c3480f3b64074a30f/modules/fide/src/main/FidePlayerSync.scala#L131
  def parsePlayer(line: String): Option[(NewPlayer, Option[FederationId])] =
    def string(start: Int, end: Int): Option[String] = line.substring(start, end).trim.some.filter(_.nonEmpty)

    def number(start: Int, end: Int): Option[Int]    = string(start, end).flatMap(_.toIntOption)
    def rating(start: Int, end: Int): Option[Rating] = string(start, end) >>= Rating.fromString

    for
      id   <- number(0, 15) >>= PlayerId.option
      name <- string(15, 76).map(_.filterNot(_.isDigit).trim)
      if name.sizeIs > 2
      title        = string(84, 89) >>= Title.apply
      wTitle       = string(89, 94) >>= Title.apply
      otherTitles  = string(94, 109).fold(Nil)(OtherTitle.applyToList)
      sex          = string(79, 82) >>= Sex.apply
      year         = number(152, 156).filter(_ > 1000)
      inactiveFlag = string(158, 160).filter(_.contains("i"))
      federationId = string(76, 79) >>= FederationId.option
    yield NewPlayer(
      id = id,
      name = name,
      title = title,
      womenTitle = wTitle,
      otherTitles = otherTitles,
      standard = rating(113, 117),
      rapid = rating(126, 132),
      blitz = rating(139, 145),
      sex = sex,
      birthYear = year,
      active = inactiveFlag.isEmpty
    ) -> federationId

object Decompressor:

  import de.lhns.fs2.compress.*
  val defaultChunkSize = 1024 * 4

  def decompress: fs2.Pipe[IO, Byte, Byte] =
    _.through(ArchiveSingleFileDecompressor(Zip4JUnarchiver.make[IO](defaultChunkSize)).decompress)
