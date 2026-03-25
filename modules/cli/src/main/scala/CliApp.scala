package fide.cli

import cats.effect.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object CliApp extends IOApp:

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    args match
      case "crawl" :: rest  => runCrawl(rest)
      case "ingest" :: rest => runIngest(rest)
      case _                => printUsage.as(ExitCode.Error)

  private def runCrawl(args: List[String]): IO[ExitCode] =
    CrawlConfig.parse(args) match
      case Left(err) =>
        IO.consoleForIO.errorln(s"Error: $err\n") *> printUsage.as(ExitCode.Error)
      case Right(config) =>
        CliResources.makeClient
          .use(client => HistoryCrawler(client, config).crawl)
          .as(ExitCode.Success)
          .handleErrorWith: e =>
            Logger[IO].error(e)("Crawl failed").as(ExitCode.Error)

  private def runIngest(args: List[String]): IO[ExitCode] =
    IngestConfig.parse(args).flatMap: config =>
      CliResources
        .makeDb(config.postgres)
        .use: (historyDb, db) =>
          HistoryIngestor(historyDb, db, config).ingest
        .as(ExitCode.Success)
    .handleErrorWith: e =>
      Logger[IO].error(e)("Ingest failed").as(ExitCode.Error)

  private def printUsage: IO[Unit] =
    IO.consoleForIO.errorln:
      """Usage:
        |  cli crawl <outputDir> [--start yyyy-MM] [--end yyyy-MM] [--delay <seconds>]
        |  cli ingest <csvDir> [--start yyyy-MM] [--end yyyy-MM]
        |
        |Commands:
        |  crawl   Download historical FIDE rating archives and save as monthly CSV files
        |  ingest  Read CSV files and upsert into the database
        |
        |Crawl options:
        |  <outputDir>          Directory to write CSV files (required)
        |  --start yyyy-MM      Start month (default: 2016-01)
        |  --end yyyy-MM        End month (default: current month)
        |  --delay <seconds>    Delay between downloads in seconds (default: 2)
        |
        |Ingest options:
        |  <csvDir>             Directory containing CSV files (required)
        |  --start yyyy-MM      Only ingest files from this month onwards
        |  --end yyyy-MM        Only ingest files up to this month
        |  Postgres config via env vars: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DATABASE, etc.
        |""".stripMargin
