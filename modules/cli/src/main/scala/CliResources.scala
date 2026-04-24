package fide.cli

import cats.effect.*
import cats.syntax.all.*
import fide.db.{ Db, DbResource, HistoryDb, LiveRatingDb, LockService }
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

object CliResources:

  def makeDb(config: IngestConfig)(using Logger[IO]): Resource[IO, (HistoryDb, Db)] =
    DbResource
      .instance(config.postgres)
      .map: res =>
        (HistoryDb(res.postgres, config.historyChunkSize.self), Db(res.postgres))

  def makeForBackfill(
      config: BackfillConfig
  )(using Logger[IO]): Resource[IO, (Db, LiveRatingDb, LockService, Client[IO])] =
    (DbResource.instance(config.postgres), makeHttpClient).tupled.map: (dbRes, client) =>
      (Db(dbRes.postgres), LiveRatingDb(dbRes.postgres), LockService(dbRes.postgres), client)

  private def makeHttpClient: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
