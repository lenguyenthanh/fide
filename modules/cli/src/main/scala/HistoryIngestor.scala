package fide.cli

import cats.effect.IO
import cats.syntax.all.*
import fide.db.{ Db, HistoryDb }
import fide.domain.*
import fide.types.YearMonth
import fs2.io.file.{ Files, Path }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait HistoryIngestor:
  def ingest: IO[Unit]

object HistoryIngestor:

  private val ChunkSize = 2000

  def apply(historyDb: HistoryDb, db: Db, config: IngestConfig)(using Logger[IO]): HistoryIngestor = new:

    def ingest: IO[Unit] =
      for
        _        <- info"Starting ingest from ${config.csvDir}"
        csvFiles <- discoverCsvFiles
        _        <- info"Found ${csvFiles.size} CSV files to ingest"
        _        <- csvFiles.traverse_(ingestFile)
        _        <- info"Ingest complete"
      yield ()

    private def discoverCsvFiles: IO[List[(YearMonth, Path)]] =
      Files[IO]
        .walk(Path.fromNioPath(config.csvDir), fs2.io.file.WalkOptions.Default.withMaxDepth(2))
        .filter(_.extName == ".csv")
        .mapFilter: path =>
          val filename = path.fileName.toString.stripSuffix(".csv")
          YearMonth.fromString(filename).toOption.map(_ -> path)
        .compile
        .toList
        .map: files =>
          files
            .filter: (ym, _) =>
              config.startMonth.forall(s => !ym.toLocalDate.isBefore(s.toLocalDate)) &&
                config.endMonth.forall(e => !ym.toLocalDate.isAfter(e.toLocalDate))
            .sortBy(_._1)(using summon[Ordering[YearMonth]].reverse)

    private def ingestFile(ym: YearMonth, path: Path): IO[Unit] =
      for
        _       <- info"Ingesting ${ym.format} from ${path.fileName}..."
        startAt <- IO.monotonic
        count   <- processFile(ym, path)
        elapsed <- IO.monotonic.map(_ - startAt)
        _       <- info"  ${ym.format}: ingested $count players in ${elapsed.toSeconds}s"
      yield ()

    private def processFile(ym: YearMonth, path: Path): IO[Long] =
      CsvReader
        .readFile(path)
        .chunkN(ChunkSize)
        .evalScan(0L): (acc, chunk) =>
          val players     = chunk.toList
          val historyRows = players.map: p =>
            PlayerHistoryRow(
              playerId = p.id,
              yearMonth = ym,
              title = p.title,
              womenTitle = p.womenTitle,
              otherTitles = p.otherTitles,
              standard = p.standard,
              standardK = p.standardK,
              rapid = p.rapid,
              rapidK = p.rapidK,
              blitz = p.blitz,
              blitzK = p.blitzK,
              federationId = p.federationId,
              active = p.active
            )
          val infoRows = players.map: p =>
            PlayerInfoRow(
              id = p.id,
              name = p.name,
              gender = p.gender,
              birthYear = p.birthYear
            )
          val feds = players
            .mapFilter: p =>
              p.federationId.map: fid =>
                NewFederation(fid, Federation.all.getOrElse(fid, fid.value))
            .distinctBy(_.id)
          for
            _ <- if feds.nonEmpty then db.upsertFederations(feds) else IO.unit
            _ <- historyDb.insertPlayerInfo(infoRows)
            _ <- historyDb.upsertPlayerHistory(historyRows)
          yield acc + players.size
        .compile
        .lastOrError
