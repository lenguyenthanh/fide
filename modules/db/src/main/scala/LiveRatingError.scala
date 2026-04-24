package fide
package db

/** Errors surfaced by the live-rating persistence layer.
  *
  * `LockLost` is the most important one: it means a writing transaction's guard predicate
  * (see design #52) observed that this holder no longer owns the live-rating ingest lock.
  * The write is rolled back; the caller must abort cleanly.
  */
enum LiveRatingError:
  case LockLost(expectedHolder: String)
