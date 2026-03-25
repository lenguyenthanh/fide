package fide.cli

import cats.effect.IO
import cats.syntax.all.*
import fide.crawler.Downloader
import fide.domain.*
import fide.types.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

/** Parser for per-category FIDE archive files (standard_*frl.zip, rapid_*frl.zip, blitz_*frl.zip).
  *
  * These have a shorter fixed-width format than the combined players_list.zip:
  *   - Only one rating column (the category's own)
  *   - Two format variants: with FOA column (Sep 2016+) and without (Jan-Aug 2016)
  *   - The FOA column shifts rating and subsequent fields by 4 positions
  */
object ArchiveParser:

  private val currentYear = java.time.Year.now.getValue

  /** Detect format offset from the header line. Returns 0 if FOA present, -4 if absent. */
  def detectOffset(header: String): Int =
    if header.contains("FOA") then 0 else -4

  /** Parse a per-category archive line with federation resolution. Returns None for blank/unparseable lines
    * (errors are logged).
    */
  def parseLine(line: String, category: String, offset: Int)(using Logger[IO]): IO[Option[NewPlayer]] =
    IO(line.trim.nonEmpty)
      .ifM(
        IO(parsePlayer(line, category, offset)).flatMap:
          case None         => none.pure[IO]
          case Some(player) =>
            player.federationId
              .traverse(resolveFederation(_, player.id))
              .as(Some(player)),
        none.pure[IO]
      )
      .handleErrorWith(e => Logger[IO].error(e)(s"Error while parsing line: $line").as(none))

  private def resolveFederation(id: FederationId, playerId: PlayerId)(using Logger[IO]): IO[Unit] =
    Federation.all.get(id) match
      case None    => warn"Unknown federation: $id for player: $playerId"
      case Some(_) => IO.unit

  /** Parse a single fixed-width line from a per-category archive.
    *
    * Shared columns (all variants): 0-14: player ID 15-75: name 76-78: federation 80-81: gender 84-88: title
    * 89-93: women's title 94-108: other titles
    *
    * Offset-dependent columns (offset=0 with FOA, offset=-4 without): 113+offset: rating (5 chars)
    * 123+offset: K-factor (3 chars) 126+offset: birth year (4 chars) 132+offset: active flag
    */
  def parsePlayer(line: String, category: String, offset: Int): Option[NewPlayer] =
    inline def string(start: Int, end: Int): Option[String] =
      if start >= line.length then None
      else line.substring(start, math.min(end, line.length)).trim.some.filter(_.nonEmpty)
    inline def number(start: Int, end: Int): Option[Int] =
      string(start, end).flatMap(_.toIntOption)
    inline def rating(start: Int, end: Int): Option[Rating] =
      string(start, end) >>= Rating.fromString
    inline def kFactor(start: Int): Option[Int] =
      number(start, start + 3).filter(_ > 0)

    val playerId     = number(0, 15) >>= PlayerId.option
    val playerName   = Downloader.sanitizeName(line.substring(15, math.min(76, line.length)))
    val federationId = string(76, 79).map(_.toUpperCase).filter(_ != FederationId.NoneFederationId) >>= FederationId.option
    val gender       = string(80, 82) >>= Gender.apply
    val title        = string(84, 89) >>= Title.apply
    val womenTitle   = string(89, 94) >>= Title.apply
    val otherTitles  = string(94, 109).fold(Nil)(OtherTitle.applyToList)

    // Offset-dependent columns
    val ratingBase = 113 + offset
    val kBase      = 123 + offset
    val bdayBase   = 126 + offset
    val flagBase   = 132 + offset

    val theRating  = rating(ratingBase, ratingBase + 5)
    val theKFactor = kFactor(kBase)
    val birthYear  = number(bdayBase, bdayBase + 4).filter(y => y > 1000 && y < currentYear)
    val active     = string(flagBase, flagBase + 4).filter(_.contains("i")).isEmpty

    (playerId, playerName).mapN: (id, name) =>
      val base = NewPlayer(
        id = id,
        name = name,
        title = title,
        womenTitle = womenTitle,
        otherTitles = otherTitles,
        gender = gender,
        birthYear = birthYear,
        active = active,
        federationId = federationId
      )
      // Populate only the matching category's rating
      category match
        case "standard" => base.copy(standard = theRating, standardK = theKFactor)
        case "rapid"    => base.copy(rapid = theRating, rapidK = theKFactor)
        case "blitz"    => base.copy(blitz = theRating, blitzK = theKFactor)
        case _          => base
