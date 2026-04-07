package fide.cli

import cats.effect.*
import cats.syntax.all.*
import fide.domain.NewPlayer
import fide.spec.HistoryService
import fide.types.*
import fs2.Stream
import fs2.io.file.{ Files, Path }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.util.Random

trait BackendTester:
  def run: IO[TestReport]

object BackendTester:

  def apply(
      historyService: HistoryService[IO],
      config: TestConfig
  )(using Logger[IO]): BackendTester = new:

    def run: IO[TestReport] =
      for
        _        <- info"Starting backend test against ${config.baseUrl}"
        csvFiles <- discoverCsvFiles
        _        <- info"Found ${csvFiles.size} CSV files to test"
        report   <- Ref.of[IO, TestReport](TestReport.empty)
        latestMonth = csvFiles.headOption.map(_._1)
        _ <- csvFiles.traverse_ { case (ym, path) =>
          testMonth(ym, path, report)
        }
        result <- report.get
        _      <- IO.println(result.summary)
      yield result

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

    private def testMonth(
        ym: YearMonth,
        path: Path,
        report: Ref[IO, TestReport]
    ): IO[Unit] =
      for
        allPlayers <- CsvReader.readFile(path).compile.toList
        sampled = Random.shuffle(allPlayers).take(config.sampleSize)
        _ <- info"Testing ${ym.format} (${sampled.size} players sampled from ${allPlayers.size})"
        _ <- testHistoricalPlayersList(ym, report)
        _ <- Stream
          .emits(sampled)
          .parEvalMapUnordered(config.concurrency): player =>
            testHistoricalPlayer(ym, player, report)
          .compile
          .drain
      yield ()

    private def testHistoricalPlayersList(ym: YearMonth, report: Ref[IO, TestReport]): IO[Unit] =
      val endpoint = "history/players"
      historyService
        .getHistoricalPlayers(ym, PageNumber(1), PageSize(30))
        .flatMap: output =>
          val result =
            if output.items.nonEmpty then TestResult.Pass(endpoint, none, ym.some)
            else TestResult.Mismatch(endpoint, none, ym.some, List(FieldDiff("items", "non-empty", "empty")))
          report.update(_.add(result)) *>
            logResult(result)
        .handleErrorWith: e =>
          val result = TestResult.HttpError(endpoint, none, ym.some, e.getMessage)
          report.update(_.add(result)) *> logResult(result)

    private def testHistoricalPlayer(
        ym: YearMonth,
        csv: NewPlayer,
        report: Ref[IO, TestReport]
    ): IO[Unit] =
      val endpoint = "history/players"
      historyService
        .getHistoricalPlayerById(csv.id, ym)
        .flatMap: api =>
          val diffs  = FieldComparer.compareHistorical(csv, api)
          val result =
            if diffs.isEmpty then TestResult.Pass(endpoint, csv.id.some, ym.some)
            else TestResult.Mismatch(endpoint, csv.id.some, ym.some, diffs)
          report.update(_.add(result)) *> logResult(result)
        .handleErrorWith: e =>
          val result = TestResult.HttpError(endpoint, csv.id.some, ym.some, e.getMessage)
          report.update(_.add(result)) *> logResult(result)

    private def logResult(result: TestResult)(using Logger[IO]): IO[Unit] =
      result match
        case TestResult.Pass(ep, pid, month) =>
          val desc = formatDesc(ep, pid, month)
          info"  ok $desc"
        case TestResult.Mismatch(ep, pid, month, diffs) =>
          val desc = formatDesc(ep, pid, month)
          warn"  FAIL $desc - ${diffs.size} field mismatches"
        case TestResult.HttpError(ep, pid, month, err) =>
          val desc = formatDesc(ep, pid, month)
          error"  ERROR $desc - $err"

    private def formatDesc(endpoint: String, playerId: Option[PlayerId], month: Option[YearMonth]): String =
      val monthStr  = month.fold("")(ym => s"[${ym.format}] ")
      val playerStr = playerId.fold("")(id => s"/$id")
      s"$monthStr$endpoint$playerStr"
