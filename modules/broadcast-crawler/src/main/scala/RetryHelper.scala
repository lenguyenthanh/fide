package fide
package broadcast

import cats.effect.kernel.Temporal
import cats.effect.std.Random
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

/** Hand-written retry helper (design decision #24, 20 lines beats a new dep for one use-site).
  *
  * Exponential backoff `baseDelay * 2^attempt` + full jitter `Random.nextInt(baseDelay)`.
  * Only retries on errors where `shouldRetry(err)` is true; other errors raise immediately.
  *
  * Retry budget: exactly `1 + maxRetries` total attempts (per design test #7).
  *
  * When `logRetries` is true, emits a `warn` line per retry attempt matching the
  * observability table in design §12 ("HTTP 5xx retry attempt | warn"): attempt
  * number, delay slept, and the underlying error.
  */
object RetryHelper:

  def retryOn[F[_]: {Temporal, Random, Logger}, A](
      maxRetries: Int,
      baseDelay: FiniteDuration,
      logRetries: Boolean
  )(shouldRetry: Throwable => Boolean)(fa: F[A]): F[A] =
    def attempt(remaining: Int, delay: FiniteDuration): F[A] =
      fa.handleErrorWith: err =>
        if remaining <= 0 || !shouldRetry(err) then Temporal[F].raiseError(err)
        else
          val jitterMs   = baseDelay.toMillis.max(1)
          val attemptNum = maxRetries - remaining + 1
          for
            jitter   <- Random[F].betweenLong(0L, jitterMs)
            totalDly  = delay + jitter.millis
            _        <- Logger[F]
              .warn(s"retry attempt $attemptNum/$maxRetries after ${totalDly.toMillis}ms: ${err.getMessage}")
              .whenA(logRetries)
            _        <- Temporal[F].sleep(totalDly)
            a        <- attempt(remaining - 1, delay * 2)
          yield a

    attempt(maxRetries, baseDelay)
