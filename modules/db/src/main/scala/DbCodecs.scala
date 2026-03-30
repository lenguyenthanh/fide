package fide
package db

import cats.syntax.all.*
import fide.db.iron.*
import fide.domain.*
import fide.types.*
import io.github.iltotore.iron.constraint.all.*
import skunk.*
import skunk.codec.all.*
import skunk.data.{ Arr, Type }

private[db] object DbCodecs:

  val title: Codec[Title]           = `enum`[Title](_.value, Title.apply, Type("title"))
  val otherTitle: Codec[OtherTitle] = `enum`[OtherTitle](_.value, OtherTitle.apply, Type("other_title"))
  val gender: Codec[Gender]         = `enum`[Gender](_.value, Gender.apply, Type("sex"))
  val ratingCodec: Codec[Rating]    = int4.refined[RatingConstraint].imap(Rating.apply)(_.value)
  val federationIdCodec: Codec[FederationId] = text.refined[NonEmpty].imap(FederationId.apply)(_.value)
  val playerIdCodec: Codec[PlayerId]         = int4.refined[Not[StrictEqual[0]]].imap(PlayerId.apply)(_.value)
  val yearMonthCodec: Codec[YearMonth]       = date.imap(YearMonth.apply)(_.toLocalDate)

  val otherTitleArr: Codec[Arr[OtherTitle]] =
    Codec.array(
      _.value,
      OtherTitle(_).toRight("invalid title"),
      Type("_other_title", List(Type("other_title")))
    )

  val otherTitles: Codec[List[OtherTitle]] = otherTitleArr.opt.imap(_.fold(Nil)(_.toList))(Arr(_*).some)

  val newPlayer: Codec[NewPlayer] =
    (playerIdCodec *: text *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: gender.opt *: int4.opt *: bool *: federationIdCodec.opt)
      .to[NewPlayer]

  val newFederation: Codec[NewFederation] =
    (federationIdCodec *: text).to[NewFederation]

  val federationInfo: Codec[FederationInfo] =
    (federationIdCodec *: text).to[FederationInfo]

  val stats: Codec[Stats] =
    (int4 *: int4 *: int4).to[Stats]

  val federationSummary: Codec[FederationSummary] =
    (federationIdCodec *: text *: int4 *: stats *: stats *: stats).to[FederationSummary]

  val playerInfo: Codec[PlayerInfo] =
    (playerIdCodec *: text *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: gender.opt *: int4.opt *: bool *: timestamptz *: timestamptz *: federationInfo.opt)
      .to[PlayerInfo]

  val newPlayerEvent: Codec[NewPlayerEvent] =
    (playerIdCodec *: text *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: gender.opt *: int4.opt *: bool *: federationIdCodec.opt *: int8 *: timestamptz *: text.opt)
      .to[NewPlayerEvent]

  val playerEvent: Codec[PlayerEvent] =
    (int8 *: playerIdCodec *: text *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: gender.opt *: int4.opt *: bool *: federationIdCodec.opt *: int8 *: timestamptz *: text.opt *: bool *: timestamptz)
      .to[PlayerEvent]

  val playerInfoRow: Codec[PlayerInfoRow] =
    (playerIdCodec *: text *: gender.opt *: int4.opt).to[PlayerInfoRow]

  val playerHistoryRow: Codec[PlayerHistoryRow] =
    (playerIdCodec *: yearMonthCodec *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: federationIdCodec.opt *: bool)
      .to[PlayerHistoryRow]

  val ratingHistoryEntry: Codec[RatingHistoryEntry] =
    (yearMonthCodec *: ratingCodec.opt *: ratingCodec.opt *: ratingCodec.opt).to[RatingHistoryEntry]

  val historicalPlayerInfo: Codec[HistoricalPlayerInfo] =
    (playerIdCodec *: text *: yearMonthCodec *: title.opt *: title.opt *: otherTitles *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: ratingCodec.opt *: int4.opt *: gender.opt *: int4.opt *: bool *: federationInfo.opt)
      .to[HistoricalPlayerInfo]
