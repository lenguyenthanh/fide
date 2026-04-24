package fide
package broadcast

import cats.effect.kernel.Temporal
import cats.effect.std.Random
import cats.syntax.all.*

import scala.concurrent.duration.*

/** Hand-written retry helper (design decision #24, 20 lines beats a new dep for one use-site).
  *
  * Exponential backoff `baseDelay * 2^attempt` + full jitter `Random.nextInt(baseDelay)`.
  * Only retries on errors where `shouldRetry(err)` is true; other errors raise immediately.
  *
  * Retry budget: exactly `1 + maxRetries` total attempts (per design test #7).
  */
object RetryHelper:

  def retryOn[F[_]: {Temporal, Random}, A](
      maxRetries: Int,
      baseDelay: FiniteDuration
  )(shouldRetry: Throwable => Boolean)(fa: F[A]): F[A] =
    def attempt(remaining: Int, delay: FiniteDuration): F[A] =
      fa.handleErrorWith: err =>
        if remaining <= 0 || !shouldRetry(err) then Temporal[F].raiseError(err)
        else
          val jitterMs = baseDelay.toMillis.max(1)
          for
            jitter <- Random[F].betweenLong(0L, jitterMs)
            _      <- Temporal[F].sleep(delay + jitter.millis)
            a      <- attempt(remaining - 1, delay * 2)
          yield a

    attempt(maxRetries, baseDelay)
