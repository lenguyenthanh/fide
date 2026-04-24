package fide
package broadcast

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

/** Minimal DTOs mirroring the subset of the Lichess broadcast API we consume.
  *
  * Only the fields used by the live-ratings pipeline are included (YAGNI).
  * Field names match the Lichess JSON schema exactly so jsoniter can auto-decode.
  *
  * Upstream reference: `github.com/lichess-org/api/doc/specs/schemas/Broadcast*.yaml`.
  */
object LichessDtos:

  // ============================================================
  // Core building blocks
  // ============================================================

  /** Round metadata — shared between `/api/broadcast` (inline rounds), `/top` (last round),
    * and the per-round endpoint.
    */
  case class BroadcastRoundInfo(
      id: String,
      slug: String,
      rated: Boolean,
      ongoing: Option[Boolean],
      finished: Option[Boolean],
      finishedAt: Option[Long], // int64 millis since epoch
      startsAt: Option[Long]
  )

  /** Tour metadata. */
  case class BroadcastTour(
      id: String,
      slug: String,
      info: Option[BroadcastTourInfo],
      dates: Option[List[Long]] // 1-2 int64 millis: [start, end?]
  )

  /** Nested info block on `BroadcastTour`. `fideTC` is our time-control signal. */
  case class BroadcastTourInfo(
      tc: Option[String],     // free-text, e.g. "90+30"
      fideTC: Option[String]  // "standard" | "rapid" | "blitz"
  )

  // ============================================================
  // `/api/broadcast` — NDJSON of official broadcasts, each with full rounds list
  // ============================================================

  case class BroadcastWithRounds(
      tour: BroadcastTour,
      rounds: List[BroadcastRoundInfo]
  )

  // ============================================================
  // `/api/broadcast/top` — paginated past broadcasts, last round only
  // ============================================================

  case class BroadcastWithLastRound(
      tour: BroadcastTour,
      round: BroadcastRoundInfo
  )

  case class BroadcastTopPast(
      currentPage: Int,
      maxPerPage: Int,
      currentPageResults: List[BroadcastWithLastRound],
      previousPage: Option[Int],
      nextPage: Option[Int]
  )

  case class BroadcastTopResponse(
      active: List[BroadcastWithLastRound],
      past: BroadcastTopPast
  )

  // ============================================================
  // `/api/broadcast/{tourSlug}/{roundSlug}/{roundId}` — one round with games
  // ============================================================

  case class BroadcastRoundGamePlayer(
      name: Option[String],
      title: Option[String],
      rating: Option[Int],
      fideId: Option[Int], // Lichess exposes as int; we stringify for our DB
      fed: Option[String]
  )

  case class BroadcastRoundGame(
      id: String,
      name: Option[String],
      players: Option[List[BroadcastRoundGamePlayer]],
      status: Option[String] // "*" | "1-0" | "0-1" | "½-½"
  )

  case class BroadcastRoundResponse(
      round: BroadcastRoundInfo,
      tour: BroadcastTour,
      games: List[BroadcastRoundGame]
  )

  // ============================================================
  // jsoniter codecs (macro-generated, compile-internal).
  // The config must be inlined at each call-site — jsoniter's macro
  // requires compile-time constant args (can't reference a `val`).
  // ============================================================

  given JsonValueCodec[BroadcastRoundInfo]       =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastTourInfo]        =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastTour]            =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastWithRounds]      =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastWithLastRound]   =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastTopPast]         =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastTopResponse]     =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastRoundGamePlayer] =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastRoundGame]       =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
  given JsonValueCodec[BroadcastRoundResponse]   =
    JsonCodecMaker.make(CodecMakerConfig.withSkipUnexpectedFields(true).withTransientEmpty(false))
