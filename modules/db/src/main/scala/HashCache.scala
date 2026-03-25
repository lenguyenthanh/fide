package fide
package db

import cats.effect.*
import cats.effect.std.AtomicCell
import fide.types.*

/** Lazy-loaded, in-memory cache of PlayerId -> hash (Long). Empty at boot. Populated on first access from DB.
  * Updated during ingestion.
  */
trait HashCache:
  /** Get the cache, loading from DB if empty. */
  def get: IO[Map[PlayerId, Long]]

  /** Update entries in the cache. */
  def update(entries: Map[PlayerId, Long]): IO[Unit]

object HashCache:

  def apply(loadFromDb: IO[Map[PlayerId, Long]]): IO[HashCache] =
    AtomicCell[IO]
      .of(Option.empty[Map[PlayerId, Long]])
      .map: cell =>
        new HashCache:
          def get: IO[Map[PlayerId, Long]] =
            cell.evalModify:
              case Some(m) => IO.pure((Some(m), m))
              case None    => loadFromDb.map(m => (Some(m), m))

          def update(entries: Map[PlayerId, Long]): IO[Unit] =
            cell.evalUpdate:
              case Some(m) => IO.pure(Some(m ++ entries))
              case None    => IO.pure(Some(entries))
