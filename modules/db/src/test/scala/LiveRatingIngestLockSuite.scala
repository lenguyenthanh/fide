package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

/** Design #52 required tests for the lock-table mechanism:
  *   1. Crash recovery: acquire lock, simulate crash by forcing expires_at into the past,
  *      assert a new acquirer succeeds after TTL expiry.
  *   2. Heartbeat-stall split-brain: holder A acquires, we simulate a stalled heartbeat
  *      by aging the row, acquirer B takes over, holder A's next guarded write aborts
  *      with LockLost.
  */
object LiveRatingIngestLockSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  // Helper: directly age the lock row so it appears expired without sleeping 120s.
  private def expireLock(postgres: Resource[IO, skunk.Session[IO]]): IO[Unit] =
    postgres.use(_.execute(ageLockSql).void)

  private val ageLockSql: skunk.Command[skunk.Void] =
    import skunk.implicits.*
    sql"""UPDATE live_rating_ingest_lock SET expires_at = NOW() - interval '1 minute'""".command

  test("crash recovery: new acquirer wins after TTL expires"):
    Containers.createResource.use: res =>
      val lock = LockService(res.postgres)
      lock.tryAcquire("alice@host").use:
        case None    => IO.pure(failure("alice should have acquired the lock"))
        case Some(_) =>
          for
            // Simulate alice crashing by aging her lock row.
            _      <- expireLock(res.postgres)
            // Now bob should be able to acquire even though alice's resource is still "held".
            bobGot <- lock
              .tryAcquire("bob@host")
              .use(r => IO.pure(r.isDefined))
          yield expect(bobGot)

  test("heartbeat-stall split-brain: guarded write after lock takeover raises LockLost"):
    // Simulate alice's heartbeat stalling so bob takes over the lock,
    // then alice tries to ingest: the guard predicate must abort with LockLost.
    Containers.createResource.use: res =>
      val lock = LockService(res.postgres)
      val liveDb = LiveRatingDb(res.postgres)
      lock.tryAcquire("alice@host").use:
        case None    => IO.pure(failure("alice should have acquired the lock"))
        case Some(_) =>
          for
            _ <- expireLock(res.postgres) // age alice's lock row
            // bob takes over; now the live row is bob's.
            bobTaken <- lock.tryAcquire("bob@host").use(r => IO.pure(r.isDefined))
            // alice's attempt to ingest must now fail with LockLostException.
            aliceGuardResult <- liveDb
              .truncateAll("alice@host")
              .attempt
          yield expect(bobTaken) and
            expect(aliceGuardResult.left.toOption.exists(_.isInstanceOf[LiveRatingDb.LockLostException]))

  test("concurrent tryAcquire: only one caller wins"):
    Containers.createResource.use: res =>
      val lock = LockService(res.postgres)
      // First holder grabs it.
      lock.tryAcquire("first@host").use:
        case None    => IO.pure(failure("first should acquire"))
        case Some(_) =>
          lock
            .tryAcquire("second@host")
            .use(r => IO.pure(r.isEmpty))
            .map(empty => expect(empty))
