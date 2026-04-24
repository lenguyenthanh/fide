package fide
package broadcast

import scala.concurrent.duration.FiniteDuration

/** Failures surfaced from Lichess HTTP calls. Captured via `Raise[F, LichessError]` and
  * handled at the `LiveRatingJob`/CLI boundary — never swallowed (see design §3 / #6).
  */
enum LichessError:
  /** Lichess returned 429. Caller should back off and retry after `retryAfter`. */
  case RateLimited(retryAfter: FiniteDuration)

  /** Lichess returned 401/403 — invalid or missing API token. */
  case Unauthorized

  /** Lichess returned 404 for the given request path. */
  case NotFound(path: String)

  /** Non-retryable HTTP failure (4xx other than 401/403/404/429, or 5xx after retry budget). */
  case Http(status: Int, body: String)

  /** Response body could not be decoded. */
  case DecodeFailure(path: String, cause: String)
