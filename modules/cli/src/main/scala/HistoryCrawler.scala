package fide.cli

import cats.effect.IO
import cats.syntax.all.*
import fide.crawler.Decompressor
import fide.domain.NewPlayer
import fide.types.YearMonth
import fs2.Stream
import fs2.io.file.{ Files, Path }
import org.http4s.{ Method, Request, Uri }
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait HistoryCrawler:
  def crawl: IO[Unit]

object HistoryCrawler:

  def apply(client: Client[IO], config: CrawlConfig)(using Logger[IO]): HistoryCrawler = new:

    def crawl: IO[Unit] =
      for
        _ <- info"Starting historical crawl from ${config.startMonth.format} to ${config.endMonth.format}"
        _ <- Files[IO].createDirectories(Path.fromNioPath(config.outputDir))
        months = generateMonths(config.startMonth, config.endMonth)
        _ <- info"${months.size} months to process"
        _ <- months.traverse_(processMonth)
        _ <- info"Historical crawl complete"
      yield ()

    private def processMonth(ym: YearMonth): IO[Unit] =
      val yearDir = Path.fromNioPath(config.outputDir.resolve(ym.year.toString))
      val csvPath = yearDir / s"${ym.format}.csv"
      Files[IO].exists(csvPath).flatMap:
        case true  => info"Skipping ${ym.format} (already exists)"
        case false =>
          downloadAndSave(ym, csvPath)
            .handleErrorWith(e => error"Failed to process ${ym.format}: $e")
            *> IO.sleep(config.delayBetweenDownloads)

    private def downloadAndSave(ym: YearMonth, csvPath: Path): IO[Unit] =
      for
        _       <- Files[IO].createDirectories(csvPath.parent.get)
        _       <- info"Downloading ${ym.format}..."
        players <- downloadAllCategories(ym)
        merged   = mergePlayers(players)
        _       <- info"${ym.format}: ${merged.size} players after merge"
        _ <- Stream
          .emits(merged)
          .through(CsvWriter.pipe)
          .intersperse("\n")
          .append(Stream.emit("\n"))
          .through(fs2.text.utf8.encode)
          .through(Files[IO].writeAll(csvPath))
          .compile
          .drain
        _ <- info"Wrote ${csvPath.fileName}"
      yield ()

    private def downloadAllCategories(ym: YearMonth): IO[Map[String, List[NewPlayer]]] =
      FideArchiveUrl.allForMonth(ym).traverse: (category, url) =>
        downloadCategory(category, url).map(category -> _)
      .map(_.toMap)

    private def downloadCategory(category: String, url: Uri): IO[List[NewPlayer]] =
      info"  Fetching $category from $url" *>
        fetchAndParse(url, category).compile.toList

    private def fetchAndParse(url: Uri, category: String): Stream[IO, NewPlayer] =
      val request = Request[IO](method = Method.GET, uri = url)
      val lines = client
        .stream(request)
        .switchMap(_.body)
        .through(Decompressor.decompress)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)

      // Read header to detect format, then parse remaining lines
      lines.pull.uncons1.flatMap:
        case None => fs2.Pull.done
        case Some((header, rest)) =>
          val offset = ArchiveParser.detectOffset(header)
          rest.evalMapFilter(line => ArchiveParser.parseLine(line, category, offset)).pull.echo
      .stream

    /** Merge players from all 3 categories by player ID.
      * Each category file contains all fields but the "primary" rating for that category
      * is most reliable. We take the union of all players and merge non-None fields,
      * preferring the category-specific value for its own rating type.
      */
    private def mergePlayers(byCategory: Map[String, List[NewPlayer]]): List[NewPlayer] =
      val allPlayers = scala.collection.mutable.Map.empty[Int, NewPlayer]

      // Process in order: standard first, then rapid, then blitz
      // Later categories overlay their specific rating onto the merged player
      for category <- List("standard", "rapid", "blitz") do
        byCategory.getOrElse(category, Nil).foreach: player =>
          val id = player.id.value
          allPlayers.get(id) match
            case None => allPlayers(id) = player
            case Some(existing) =>
              allPlayers(id) = merge(existing, player, category)

      allPlayers.values.toList.sortBy(_.id.value)

    private def merge(existing: NewPlayer, incoming: NewPlayer, category: String): NewPlayer =
      existing.copy(
        // Non-rating fields: prefer existing (from earlier category), fall back to incoming
        title = existing.title.orElse(incoming.title),
        womenTitle = existing.womenTitle.orElse(incoming.womenTitle),
        otherTitles = if existing.otherTitles.nonEmpty then existing.otherTitles else incoming.otherTitles,
        gender = existing.gender.orElse(incoming.gender),
        birthYear = existing.birthYear.orElse(incoming.birthYear),
        federationId = existing.federationId.orElse(incoming.federationId),
        // Ratings: prefer the value from the category's own file
        standard = if category == "standard" then incoming.standard.orElse(existing.standard)
                   else existing.standard.orElse(incoming.standard),
        standardK = if category == "standard" then incoming.standardK.orElse(existing.standardK)
                    else existing.standardK.orElse(incoming.standardK),
        rapid = if category == "rapid" then incoming.rapid.orElse(existing.rapid)
                else existing.rapid.orElse(incoming.rapid),
        rapidK = if category == "rapid" then incoming.rapidK.orElse(existing.rapidK)
                 else existing.rapidK.orElse(incoming.rapidK),
        blitz = if category == "blitz" then incoming.blitz.orElse(existing.blitz)
                else existing.blitz.orElse(incoming.blitz),
        blitzK = if category == "blitz" then incoming.blitzK.orElse(existing.blitzK)
                 else existing.blitzK.orElse(incoming.blitzK)
      )

  def generateMonths(start: YearMonth, end: YearMonth): List[YearMonth] =
    LazyList
      .iterate(start.toLocalDate)(_.plusMonths(1))
      .takeWhile(!_.isAfter(end.toLocalDate))
      .map(YearMonth(_))
      .toList
