package fide
package broadcast

import cats.effect.*
import fide.db.LiveRatingDb
import fide.db.LockService
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

/** Hook fired after a successful monthly FIDE ingest, to reset the live-rating
  * projection (design §9 / decision #17/#52).
  *
  * Runs in a separate transaction (not co-transactional with the monthly ingest):
  *   1. Acquire the live-rating ingest lock (blocking, via LockService). Backend
  *      tick is already gated on the same lock, so ticks pause while we reset.
  *   2. `TRUNCATE live_rating_games, live_ratings, ingested_rounds`.
  *   3. Release lock via Resource finalizer.
  *
  * Short gap (~seconds) where stale live_ratings could coexist with fresh
  * players rows. Acceptable v1 cost per design #17.
  */
trait MonthlyResetHook:
  def run: IO[Unit]

object MonthlyResetHook:

  /** A no-op implementation — useful for tests or when the live-rating subsystem is
    * deliberately disabled.
    */
  val noop: MonthlyResetHook = new:
    def run: IO[Unit] = IO.unit

  def apply(db: LiveRatingDb, lockService: LockService, holder: String)(using Logger[IO]): MonthlyResetHook =
    new:
      def run: IO[Unit] =
        info"monthly-reset: acquiring live-rating ingest lock as $holder" *>
          lockService
            .acquireBlocking(
              holder,
              onWaiting = current => info"monthly-reset: waiting for live-rating lock (held by $current)"
            )
            .use: _ =>
              info"monthly-reset: lock acquired; truncating live-rating tables" *>
                db.truncateAll(holder) *>
                info"monthly-reset: truncate complete"
