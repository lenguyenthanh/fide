package fide
package db

import cats.effect.*
import cats.effect.std.Supervisor
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*
import skunk.*
import skunk.codec.all.*
import skunk.data.Completion
import skunk.implicits.*

import scala.concurrent.duration.*

/** Cross-process lock-table service (design #52).
  *
  * Serializes three mutually-exclusive ingest paths via the `live_rating_ingest_lock` table:
  *   - backend tick (`tryAcquire`, non-blocking, skip if held)
  *   - CLI backfill (`acquireBlocking`, run to completion)
  *   - monthly reset (`acquireBlocking`)
  *
  * Acquisition is an atomic UPSERT predicated on "no live holder". A background heartbeat
  * fiber extends `expires_at` every `heartbeatInterval` (default 30s) to
  * `NOW() + ttl` (default 120s). If the heartbeat update affects zero rows, the fiber
  * reports that the lock was lost — callers see this by having their next writing tx's
  * guard predicate (in `LiveRatingDb`) abort.
  *
  * Robust against: JVM crash (TTL expires eventually), TCP reset (new session reclaims
  * after TTL), pool reconnect (lock is row-scoped, not session-scoped).
  */
trait LockService:

  /** Try to acquire the lock once; returns a `Resource` producing `Some(())` if acquired
    * with a running heartbeat, or `None` if the lock is currently held by someone else.
    */
  def tryAcquire(holder: String): Resource[IO, Option[Unit]]

  /** Block until the lock is acquired; returns a `Resource` producing the holder identity.
    * Polls every `retryInterval` (default 5s) while held by another caller.
    */
  def acquireBlocking(holder: String, onWaiting: String => IO[Unit] = _ => IO.unit): Resource[IO, Unit]

object LockService:

  val LockName: String              = "live_rating_ingest"
  val DefaultTtl: FiniteDuration    = 120.seconds
  val HeartbeatInterval: FiniteDuration = 30.seconds
  val RetryInterval: FiniteDuration = 5.seconds

  def apply(postgres: Resource[IO, Session[IO]])(using Logger[IO]): LockService = new:

    def tryAcquire(holder: String): Resource[IO, Option[Unit]] =
      Resource.eval(tryAcquireOnce(holder)).flatMap:
        case false => Resource.pure(None)
        case true  => heartbeatFiber(holder).as(Some(()))

    def acquireBlocking(holder: String, onWaiting: String => IO[Unit]): Resource[IO, Unit] =
      def acquireLoop: IO[Unit] =
        tryAcquireOnce(holder).flatMap:
          case true  => IO.unit
          case false =>
            for
              current <- currentHolder
              _       <- current.traverse_(h => onWaiting(h))
              _       <- IO.sleep(RetryInterval)
              _       <- acquireLoop
            yield ()

      Resource.eval(acquireLoop) *> heartbeatFiber(holder).void

    // ------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------

    /** Atomic UPSERT: inserts a fresh row, or overwrites an *expired* holder's row.
      * Returns true iff this caller now holds the lock.
      */
    private def tryAcquireOnce(holder: String): IO[Boolean] =
      postgres.use(_.unique(Sql.tryAcquire)(holder)).map(_ == holder)

    private def currentHolder: IO[Option[String]] =
      postgres.use(_.option(Sql.currentHolder))

    private def releaseIfHeld(holder: String): IO[Unit] =
      postgres.use(_.execute(Sql.release)(holder)).void.handleErrorWith: e =>
        warn"failed to release live-rating ingest lock held by $holder: ${e.getMessage}"

    private def heartbeat(holder: String): IO[Boolean] =
      postgres.use(_.execute(Sql.heartbeat)(holder)).map:
        case Completion.Update(1) => true
        case _                    => false

    /** Run the heartbeat as a supervised background fiber; release lock on finalizer. */
    private def heartbeatFiber(holder: String): Resource[IO, Unit] =
      Supervisor[IO](await = false).flatMap: supervisor =>
        def beatLoop: IO[Unit] =
          IO.sleep(HeartbeatInterval) *>
            heartbeat(holder).flatMap:
              case true  => beatLoop
              case false => warn"live-rating ingest lock lost while held by $holder (heartbeat failed)"
        Resource.make(supervisor.supervise(beatLoop).void)(_ => releaseIfHeld(holder))

  // ============================================================
  // SQL
  // ============================================================

  private object Sql:

    /** Insert or overwrite an expired holder; returns the *current* holder after the write.
      * If this caller didn't win, returns the live holder's name.
      */
    val tryAcquire: Query[String, String] =
      sql"""
        INSERT INTO live_rating_ingest_lock (lock_name, holder, acquired_at, expires_at)
        VALUES ('live_rating_ingest', $text, NOW(), NOW() + interval '120 seconds')
        ON CONFLICT (lock_name) DO UPDATE
          SET holder      = EXCLUDED.holder,
              acquired_at = EXCLUDED.acquired_at,
              expires_at  = EXCLUDED.expires_at
          WHERE live_rating_ingest_lock.expires_at < NOW()
        RETURNING holder
      """.query(text)

    val currentHolder: Query[Void, String] =
      sql"""
        SELECT holder FROM live_rating_ingest_lock
        WHERE lock_name = 'live_rating_ingest' AND expires_at > NOW()
      """.query(text)

    val heartbeat: Command[String] =
      sql"""
        UPDATE live_rating_ingest_lock
           SET expires_at = NOW() + interval '120 seconds'
         WHERE lock_name = 'live_rating_ingest'
           AND holder    = $text
           AND expires_at > NOW()
      """.command

    val release: Command[String] =
      sql"""
        DELETE FROM live_rating_ingest_lock
        WHERE lock_name = 'live_rating_ingest' AND holder = $text
      """.command
