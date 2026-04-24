package fide
package broadcast

import cats.effect.*
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.*

/** Background job that drives the live-rating ingest loop. Sibling to CrawlerJob
  * (same resource-wrapped background-fiber pattern), see design §9 / decisions #5/#30.
  *
  * One tick every `pollInterval`. Tracks consecutive failures against
  * `circuitBreakerThreshold` — exceeds it → raise and stop the loop (decision #30).
  * "Skipped due to lock contention" does NOT increment the failure counter.
  */
trait LiveRatingJob:
  def run(): Resource[IO, Unit]

object LiveRatingJob:

  val CircuitBreakerThreshold: Int = 10

  def apply(
      syncer: LiveRatingSyncer,
      delay: FiniteDuration,
      pollInterval: FiniteDuration
  )(using Logger[IO]): LiveRatingJob = new:

    def run(): Resource[IO, Unit] =
      start.background.void

    private def start: IO[Nothing] =
      info"live-rating job starting in ${delay.toSeconds}s (poll ${pollInterval.toMinutes}min)" *>
        IO.sleep(delay) *>
        loop(consecutiveFailures = 0)

    private def loop(consecutiveFailures: Int): IO[Nothing] =
      syncer.tick.flatMap: outcome =>
        val nextFailures = outcome match
          case LiveRatingSyncer.Outcome.Done(_) => 0
          case LiveRatingSyncer.Outcome.Skipped => consecutiveFailures // unchanged
          case LiveRatingSyncer.Outcome.Failed(_) => consecutiveFailures + 1
        tripOrSleep(nextFailures)

    private def tripOrSleep(failures: Int): IO[Nothing] =
      if failures >= CircuitBreakerThreshold then
        error"live-rating job: $failures consecutive failed ticks >= threshold $CircuitBreakerThreshold; stopping loop" *>
          IO.raiseError(CircuitBreakerTrippedException(failures))
      else
        IO.sleep(pollInterval) *> loop(failures)

  final case class CircuitBreakerTrippedException(consecutiveFailures: Int)
      extends RuntimeException(
        s"live-rating job circuit breaker tripped after $consecutiveFailures consecutive failed ticks",
        null,
        true,
        false
      )
