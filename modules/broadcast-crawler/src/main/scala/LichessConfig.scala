package fide
package broadcast

import org.http4s.Uri

import scala.concurrent.duration.FiniteDuration

/** Configuration for the Lichess HTTP client. See design §12 / decision #49.
  *
  * The token is **required** — there is no default and the app fails to start without it.
  * It's wrapped in a `Secret`-style holder at the Ciris-loading layer (backend's `AppConfig`);
  * here we carry the raw string since the value is already scoped to ingestion code paths
  * and never serialized, logged, or echoed.
  */
case class LichessConfig(
    baseUri: Uri,
    apiToken: String,
    requestTimeout: FiniteDuration,
    maxConcurrentRounds: Int,
    retryMaxAttempts: Int,
    retryBaseDelay: FiniteDuration
)
