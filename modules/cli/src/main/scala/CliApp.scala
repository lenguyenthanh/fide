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
    ingestCommand

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
