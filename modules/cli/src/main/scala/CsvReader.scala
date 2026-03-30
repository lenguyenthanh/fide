package fide.cli

import cats.effect.IO
import cats.syntax.all.*
import fide.domain.*
import fide.types.*
import fs2.Stream
import fs2.data.csv.*
import fs2.io.file.{ Files, Path }
import org.typelevel.log4cats.Logger

object CsvReader:

  given CsvRowDecoder[NewPlayer, String] = new:
    def apply(row: CsvRow[String]): DecoderResult[NewPlayer] =
      for
        id     <- row.as[Int]("id")
        name   <- row.as[String]("name")
        active <- row.as[Boolean]("active")
      yield NewPlayer(
        id = PlayerId.applyUnsafe(id),
        name = name,
        title = optStr(row, "title") >>= Title.apply,
        womenTitle = optStr(row, "womenTitle") >>= Title.apply,
        otherTitles =
          optStr(row, "otherTitles").fold(Nil)(s => s.split(";").toList.mapFilter(OtherTitle.apply)),
        standard = optStr(row, "standard") >>= Rating.fromString,
        standardK = optInt(row, "standardK"),
        rapid = optStr(row, "rapid") >>= Rating.fromString,
        rapidK = optInt(row, "rapidK"),
        blitz = optStr(row, "blitz") >>= Rating.fromString,
        blitzK = optInt(row, "blitzK"),
        gender = optStr(row, "gender") >>= Gender.apply,
        birthYear = optInt(row, "birthYear"),
        active = active,
        federationId = optStr(row, "federationId") >>= FederationId.option
      )

  def readFile(path: Path)(using Logger[IO]): Stream[IO, NewPlayer] =
    Files[IO]
      .readAll(path)
      .through(fs2.text.utf8.decode)
      .through(lenient.attemptDecodeUsingHeaders[NewPlayer]())
      .evalMapFilter:
        case Left(err)     => Logger[IO].warn(s"Failed to parse CSV row: $err").as(none)
        case Right(player) => player.some.pure[IO]

  private def optStr(row: CsvRow[String], col: String): Option[String] =
    row.as[String](col).toOption.filter(_.nonEmpty)

  private def optInt(row: CsvRow[String], col: String): Option[Int] =
    optStr(row, col).flatMap(_.toIntOption)
