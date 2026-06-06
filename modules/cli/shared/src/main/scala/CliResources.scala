package fide.cli

import cats.effect.*
import fide.db.{ Db, DbResource, HistoryDb }
import org.typelevel.log4cats.Logger

object CliResources:

  def makeDb(config: IngestConfig)(using Logger[IO]): Resource[IO, (HistoryDb, Db)] =
    DbResource
      .instance(config.postgres)
      .map: res =>
        (HistoryDb(res.postgres, config.historyChunkSize.self), Db(res.postgres))
