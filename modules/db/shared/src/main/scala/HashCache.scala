package fide
package db

import cats.effect.*
import cats.effect.std.AtomicCell

/** Lazy-loaded, in-memory cache of K -> hash (Long). Empty at boot. Populated on first access from DB.
  * Updated during ingestion.
  */
trait HashCache[K]:
  /** Get the cache, loading from DB if empty. */
  def get: IO[Map[K, Long]]

  /** Update entries in the cache. */
  def update(entries: Map[K, Long]): IO[Unit]

object HashCache:

  def apply[K](loadFromDb: IO[Map[K, Long]]): IO[HashCache[K]] =
    AtomicCell[IO]
      .of(Option.empty[Map[K, Long]])
      .map: cell =>
        new HashCache[K]:
          def get: IO[Map[K, Long]] =
            cell.evalModify:
              case Some(m) => IO.pure((Some(m), m))
              case None    => loadFromDb.map(m => (Some(m), m))

          def update(entries: Map[K, Long]): IO[Unit] =
            cell.evalUpdate:
              case Some(m) => IO.pure(Some(m ++ entries))
              case None    => IO.pure(Some(entries))
