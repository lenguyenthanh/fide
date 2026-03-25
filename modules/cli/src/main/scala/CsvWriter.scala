package fide.cli

import cats.effect.IO
import fide.domain.NewPlayer
import fs2.{ Pipe, Stream }

object CsvWriter:

  val header =
    "id,name,title,womenTitle,otherTitles,standard,standardK,rapid,rapidK,blitz,blitzK,gender,birthYear,active,federationId"

  def pipe: Pipe[IO, NewPlayer, String] =
    players => Stream.emit(header) ++ players.map(toCsvLine)

  private def toCsvLine(p: NewPlayer): String =
    List(
      p.id.toString,
      escapeCsv(p.name),
      p.title.fold("")(_.value),
      p.womenTitle.fold("")(_.value),
      p.otherTitles.map(_.value).mkString(";"),
      p.standard.fold("")(_.toString),
      p.standardK.fold("")(_.toString),
      p.rapid.fold("")(_.toString),
      p.rapidK.fold("")(_.toString),
      p.blitz.fold("")(_.toString),
      p.blitzK.fold("")(_.toString),
      p.gender.fold("")(_.value),
      p.birthYear.fold("")(_.toString),
      p.active.toString,
      p.federationId.fold("")(_.toString)
    ).mkString(",")

  private def escapeCsv(s: String): String =
    if s.contains(",") || s.contains("\"") || s.contains("\n") then
      "\"" + s.replace("\"", "\"\"") + "\""
    else s
