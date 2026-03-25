package fide.cli

import cats.effect.*
import fide.db.{ Db, DbResource, HistoryDb }
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

object CliResources:

  def makeClient: Resource[IO, Client[IO]] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(120.seconds)
      .withIdleConnectionTime(60.seconds)
      .build

  def makeDb(config: IngestConfig)(using Logger[IO]): Resource[IO, (HistoryDb, Db)] =
    DbResource
      .instance(config.postgres)
      .map: res =>
        (HistoryDb(res.postgres, config.historyChunkSize.self), Db(res.postgres))
