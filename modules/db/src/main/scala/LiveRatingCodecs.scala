package fide
package db

import cats.syntax.all.*
import fide.domain.Models.*
import fide.types.*
import skunk.*
import skunk.codec.all.*
import skunk.data.Type

import java.time.ZoneOffset

/** Skunk codecs for the live-rating tables (V0003). */
private[db] object LiveRatingCodecs:

  import DbCodecs.*

  /** `time_control` enum codec. */
  val timeControl: Codec[TimeControl] =
    `enum`[TimeControl](_.value, TimeControl.apply, Type("time_control"))

  /** `result` column stores the canonical FIDE strings. */
  val gameResult: Codec[GameResult] =
    text.imap[GameResult](s => GameResult(s).getOrElse(sys.error(s"invalid game_result in DB: $s")))(_.value)

  /** Full row codec for `live_rating_games`. */
  val liveRatingGameRow: Codec[LiveRatingGameRow] =
    (text *: text *: text *: timeControl *: playerIdCodec.opt *: playerIdCodec.opt *:
      text.opt *: text.opt *: int4.opt *: int4.opt *: gameResult *: timestamptz.opt)
      .imap { case g *: t *: r *: tc *: wp *: bp *: wf *: bf *: wd *: bd *: res *: fa *: EmptyTuple =>
        LiveRatingGameRow(g, t, r, tc, wp, bp, wf, bf, wd, bd, res, fa.map(_.toInstant))
      } { row =>
        row.gameId *: row.tourId *: row.roundId *: row.timeControl *:
          row.whitePlayerId *: row.blackPlayerId *:
          row.whiteFideId *: row.blackFideId *:
          row.whiteRatingDiff *: row.blackRatingDiff *:
          row.result *: row.roundFinishedAt.map(_.atOffset(ZoneOffset.UTC)) *: EmptyTuple
      }

  /** Decoder for a `live_ratings` row. */
  val liveRatingRow: Decoder[LiveRating] =
    (playerIdCodec *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *: int4.opt *:
      ratingCodec.opt *: ratingCodec.opt *: ratingCodec.opt *: timestamptz).map:
      case pid *: sd *: sg *: rd *: rg *: bd *: bg *: ps *: pr *: pb *: ua *: EmptyTuple =>
        def entry(d: Option[Int], g: Option[Int], p: Option[Rating]): Option[LiveRatingEntry] =
          (d, g, p).mapN(LiveRatingEntry.apply)
        LiveRating(pid, entry(sd, sg, ps), entry(rd, rg, pr), entry(bd, bg, pb), Some(ua.toInstant))

  val ingestedRoundRow: Codec[IngestedRoundRow] =
    (text *: text *: int4 *: timestamptz *: timestamptz.opt *: bool *: int4 *: timestamptz.opt)
      .imap { case rid *: tid *: gc *: ia *: fo *: ro *: fc *: fa *: EmptyTuple =>
        IngestedRoundRow(rid, tid, gc, ia.toInstant, fo.map(_.toInstant), ro, fc, fa.map(_.toInstant))
      } { row =>
        row.roundId *: row.tourId *: row.gameCount *:
          row.ingestedAt.atOffset(ZoneOffset.UTC) *:
          row.finishedAtObserved.map(_.atOffset(ZoneOffset.UTC)) *:
          row.ratedObserved *:
          row.failureCount *:
          row.failedAt.map(_.atOffset(ZoneOffset.UTC)) *: EmptyTuple
      }

