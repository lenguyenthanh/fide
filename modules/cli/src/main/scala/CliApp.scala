package fide.cli

import cats.effect.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object CliApp
    extends CommandIOApp(
      name = "fide-cli",
      header = "CLI tools for FIDE data",
      version = "1.0.0"
    ):

  given Logger[IO] = Slf4jLogger.getLogger[IO]

  override def main: Opts[IO[ExitCode]] =
    ingestCommand orElse testCommand orElse liveRatingsCommand

  private def ingestCommand: Opts[IO[ExitCode]] =
    Opts
      .subcommand("ingest", "Read CSV files and upsert into the database")(IngestConfig.opts)
      .map: cliOpts =>
        IngestConfig
          .load(cliOpts)
          .flatMap: config =>
            CliResources
              .makeDb(config)
              .use: (historyDb, db) =>
                HistoryIngestor(historyDb, db, config).ingest
          .as(ExitCode.Success)
          .handleErrorWith: e =>
            Logger[IO].error(e)("Ingest failed").as(ExitCode.Error)

  private def testCommand: Opts[IO[ExitCode]] =
    Opts
      .subcommand("test", "Smoke-test a running backend against CSV data")(TestConfig.opts)
      .map: config =>
        TestResources
          .make(config)
          .use: historyService =>
            BackendTester(historyService, config).run
              .map(report => if report.isSuccess then ExitCode.Success else ExitCode.Error)
          .handleErrorWith: e =>
            Logger[IO].error(e)("Test failed").as(ExitCode.Error)

  /** Nested: `fide-cli live-ratings <sub>` (design CLI decision #5). */
  private def liveRatingsCommand: Opts[IO[ExitCode]] =
    Opts.subcommand("live-ratings", "Live-rating ingestion tools")(backfillSubcommand)

  private def backfillSubcommand: Opts[IO[ExitCode]] =
    Opts
      .subcommand("backfill", "One-shot catch-up of current-cycle finished broadcast rounds")(
        BackfillConfig.opts
      )
      .map: cliOpts =>
        BackfillConfig
          .load(cliOpts)
          .flatMap(LiveRatingsBackfill.run)
          .as(ExitCode.Success)
          .handleErrorWith: e =>
            Logger[IO].error(e)("Live-rating backfill failed").as(ExitCode.Error)
