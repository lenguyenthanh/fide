package fide
package db

import java.time.Instant

/** Cursor row for `ingested_rounds`. Tracks which rounds we've ingested plus the
  * `(finished_at_observed, rated_observed)` tuple used for re-finalization detection
  * (design decision #29).
  */
final case class IngestedRoundRow(
    roundId: String,
    tourId: String,
    gameCount: Int,
    ingestedAt: Instant,
    finishedAtObserved: Option[Instant],
    ratedObserved: Boolean,
    failureCount: Int,
    failedAt: Option[Instant]
)
