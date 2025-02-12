package fide
package crawler

import cats.effect.IO
import cats.syntax.all.*
import fide.db.KVStore
import org.http4s.*
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

trait Syncer:
  def fetchStatus: IO[Syncer.Status]
  def saveLastUpdate(value: String): IO[Unit]

object Syncer:
  val fideLastUpdateKey = "fide_last_update_key"

  enum Status:
    case UpToDate
    case OutDated(timestamp: Option[String])

  object Status:
    def apply(local: Option[String], remote: Option[String]): Status =
      (local, remote).match
        case (Some(l), Some(r)) if l == r => Status.UpToDate
        case _                            => Status.OutDated(remote)

  def instance(store: KVStore, client: Client[IO])(using Logger[IO]): Syncer =
    Syncer(store, UpdateChecker(client))

  def apply(store: KVStore, scraper: UpdateChecker)(using Logger[IO]): Syncer = new:
    def fetchStatus: IO[Status] =
      (lastLocalUpdate, scraper.lastUpdate).flatMapN: (local, remote) =>
        info"last local update: $local, last remote update: $remote" *>
          IO(Status(local, remote))

    private def lastLocalUpdate: IO[Option[String]] =
      store.get(fideLastUpdateKey)

    def saveLastUpdate(value: String): IO[Unit] =
      store.put(fideLastUpdateKey, value) *> info"Saved last update date: $value"
