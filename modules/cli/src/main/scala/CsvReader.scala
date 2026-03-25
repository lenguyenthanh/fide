package fide.cli

import cats.effect.IO
import cats.syntax.all.*
import fide.domain.*
import fide.types.*
import fs2.io.file.{ Files, Path }
import fs2.{ Pipe, Stream }
import org.typelevel.log4cats.Logger

object CsvReader:

  def pipe(using Logger[IO]): Pipe[IO, String, NewPlayer] =
    _.drop(1) // skip header
      .evalMapFilter: line =>
        IO(parseLine(line)).handleErrorWith: e =>
          Logger[IO].warn(s"Failed to parse CSV line: $e").as(none)

  def readFile(path: Path)(using Logger[IO]): Stream[IO, NewPlayer] =
    Files[IO]
      .readUtf8Lines(path)
      .filter(_.nonEmpty)
      .through(pipe)

  private def parseLine(line: String): Option[NewPlayer] =
    val fields = parseCsvFields(line)
    if fields.length < 15 then none
    else
      val id   = fields(0).toIntOption >>= PlayerId.option
      val name = fields(1).some.filter(_.nonEmpty)
      (id, name).mapN: (pid, pname) =>
        NewPlayer(
          id = pid,
          name = pname,
          title = fields(2).some.filter(_.nonEmpty) >>= Title.apply,
          womenTitle = fields(3).some.filter(_.nonEmpty) >>= Title.apply,
          otherTitles =
            fields(4).some.filter(_.nonEmpty).fold(Nil)(s => s.split(";").toList.mapFilter(OtherTitle.apply)),
          standard = fields(5).some.filter(_.nonEmpty) >>= Rating.fromString,
          standardK = fields(6).toIntOption,
          rapid = fields(7).some.filter(_.nonEmpty) >>= Rating.fromString,
          rapidK = fields(8).toIntOption,
          blitz = fields(9).some.filter(_.nonEmpty) >>= Rating.fromString,
          blitzK = fields(10).toIntOption,
          gender = fields(11).some.filter(_.nonEmpty) >>= Gender.apply,
          birthYear = fields(12).toIntOption,
          active = fields(13).toBooleanOption.getOrElse(true),
          federationId = fields(14).some.filter(_.nonEmpty) >>= FederationId.option
        )

  /** Parse CSV fields handling quoted values with embedded commas and escaped quotes. */
  private def parseCsvFields(line: String): Array[String] =
    val fields   = scala.collection.mutable.ArrayBuffer.empty[String]
    val current  = new StringBuilder
    var inQuotes = false
    var i        = 0
    while i < line.length do
      val c = line.charAt(i)
      if inQuotes then
        if c == '"' then
          if i + 1 < line.length && line.charAt(i + 1) == '"' then
            current.append('"')
            i += 1
          else inQuotes = false
        else current.append(c)
      else if c == '"' then inQuotes = true
      else if c == ',' then
        fields += current.toString
        current.clear()
      else current.append(c)
      i += 1
    fields += current.toString
    fields.toArray
