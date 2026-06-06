package fide.cli

import cats.effect.*
import com.monovore.decline.*
import com.monovore.decline.effect.*
import org.typelevel.log4cats.Logger

object CliApp
    extends CommandIOApp(
      name = "fide-cli",
      header = "CLI tools for FIDE data",
      version = "1.0.0"
    ):

  override def main: Opts[IO[ExitCode]] =
    ingestCommand orElse testCommand

  private def ingestCommand: Opts[IO[ExitCode]] =
    Opts
      .subcommand("ingest", "Read CSV files and upsert into the database")(IngestConfig.opts)
      .map: cliOpts =>
        CliLogger.create.flatMap: logger =>
          given Logger[IO] = logger
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
        CliLogger.create.flatMap: logger =>
          given Logger[IO] = logger
          TestResources
            .make(config)
            .use: historyService =>
              BackendTester(historyService, config).run
                .map(report => if report.isSuccess then ExitCode.Success else ExitCode.Error)
            .handleErrorWith: e =>
              Logger[IO].error(e)("Test failed").as(ExitCode.Error)
