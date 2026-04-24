package fide
package broadcast

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** Opaque IDs for Lichess broadcast entities. All 8-char alphanumeric strings. */
object Ids:
  type LichessId = Match["[A-Za-z0-9]{8}"]

  type BroadcastTourId = BroadcastTourId.T
  object BroadcastTourId extends RefinedType[String, LichessId]

  type BroadcastRoundId = BroadcastRoundId.T
  object BroadcastRoundId extends RefinedType[String, LichessId]

  type BroadcastGameId = BroadcastGameId.T
  object BroadcastGameId extends RefinedType[String, LichessId]
