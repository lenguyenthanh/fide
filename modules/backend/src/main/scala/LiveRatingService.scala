package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.LiveRatingDb
import fide.domain.Models
import fide.spec.{
  FederationId as _,
  FideId as _,
  PageNumber as _,
  PageSize as _,
  PlayerId as _,
  Rating as _,
  BirthYear as _,
  *
}
import fide.types.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*
import smithy4s.time.Timestamp

import java.time.Instant

class LiveRatingServiceImpl(db: LiveRatingDb, mainDb: fide.db.Db)(using Logger[IO])
    extends LiveRatingService[IO]:

  import LiveRatingTransformers.*

  override def getLiveRatingLeaderboard(
      timeControl: TimeControl,
      page: PageNumber,
      pageSize: PageSize,
      isActive: Option[Boolean],
      standardMin: Option[Rating],
      standardMax: Option[Rating],
      rapidMin: Option[Rating],
      rapidMax: Option[Rating],
      blitzMin: Option[Rating],
      blitzMax: Option[Rating],
      name: Option[String],
      titles: Option[List[Title]],
      otherTitles: Option[List[OtherTitle]],
      gender: Option[Gender],
      birthYearMin: Option[BirthYear],
      birthYearMax: Option[BirthYear],
      hasTitle: Option[Boolean],
      hasWomenTitle: Option[Boolean],
      hasOtherTitle: Option[Boolean],
      federationId: Option[FederationId]
  ): IO[GetLiveRatingLeaderboardOutput] =
    val tc = timeControl.toDomain
    val offset = (page - 1) * pageSize
    (
      db.getLeaderboard(tc, pageSize, offset, Models.PlayerFilter.default),
      db.countLeaderboard(tc, Models.PlayerFilter.default)
    ).parMapN: (items, total) =>
      val entries = items.map(entry => LeaderboardEntry(entry.player.toLeaderboardPlayer, entry.liveEntry.toSpec))
      GetLiveRatingLeaderboardOutput(
        entries,
        total,
        Option.when(entries.size == pageSize)(page.succ)
      )
    .handleErrorWith: e =>
      error"Error in getLiveRatingLeaderboard: ${e.getMessage}" *>
        IO.raiseError(InternalServerError("Internal server error"))

  override def getLiveRatingByFideId(fideId: FideId): IO[GetLiveRatingByFideIdOutput] =
    mainDb.playerByFideId(fideId).flatMap:
      case None      => IO.raiseError(PlayerFideIdNotFound(fideId))
      case Some(pi)  =>
        db.getLiveRating(pi.id).map:
          case Some(lr) => GetLiveRatingByFideIdOutput(lr.toSpec)
          case None     =>
            GetLiveRatingByFideIdOutput(
              Models.LiveRating(pi.id, None, None, None, None).toSpec
            )
    .handleErrorWith:
      case e: PlayerFideIdNotFound => IO.raiseError(e)
      case e                       =>
        error"Error in getLiveRatingByFideId: ${e.getMessage}" *>
          IO.raiseError(InternalServerError("Internal server error"))

  override def getLiveRatingDetailsByFideId(fideId: FideId): IO[GetLiveRatingDetailsByFideIdOutput] =
    mainDb.playerByFideId(fideId).flatMap:
      case None     => IO.raiseError(PlayerFideIdNotFound(fideId))
      case Some(pi) =>
        db.getLiveRatingDetails(pi.id).map:
          case Some(details) =>
            GetLiveRatingDetailsByFideIdOutput(details.toSpec(pi.id))
          case None =>
            GetLiveRatingDetailsByFideIdOutput(
              LiveRatingDetails(
                Models.LiveRating(pi.id, None, None, None, None).toSpec,
                Nil
              )
            )
    .handleErrorWith:
      case e: PlayerFideIdNotFound => IO.raiseError(e)
      case e                       =>
        error"Error in getLiveRatingDetailsByFideId: ${e.getMessage}" *>
          IO.raiseError(InternalServerError("Internal server error"))

  override def getLiveRatingStatus(): IO[GetLiveRatingStatusOutput] =
    db.getLockState
      .map: lockOpt =>
        GetLiveRatingStatusOutput(
          LiveRatingStatus(
            lastTickStartedAt = None,    // metrics layer TBD per decision #27
            lastTickCompletedAt = None,
            lastMonthlyResetAt = None,
            lastBackfillAt = None,
            lockHeldBy = lockOpt.map(_.holder),
            lockHeldSince = lockOpt.map(l => l.acquiredAt.toTimestamp)
          )
        )
      .handleErrorWith: e =>
        error"Error in getLiveRatingStatus: ${e.getMessage}" *>
          IO.raiseError(InternalServerError("Internal server error"))

object LiveRatingTransformers:

  extension (i: Instant) def toTimestamp: Timestamp = Timestamp.fromEpochSecond(i.getEpochSecond)

  extension (tc: TimeControl)
    def toDomain: Models.TimeControl = tc match
      case TimeControl.Standard => Models.TimeControl.Standard
      case TimeControl.Rapid    => Models.TimeControl.Rapid
      case TimeControl.Blitz    => Models.TimeControl.Blitz

  extension (tc: Models.TimeControl)
    def toSpec: TimeControl = tc match
      case Models.TimeControl.Standard => TimeControl.Standard
      case Models.TimeControl.Rapid    => TimeControl.Rapid
      case Models.TimeControl.Blitz    => TimeControl.Blitz

  extension (r: Models.GameResult)
    def toSpec: GameResult = r match
      case Models.GameResult.WhiteWin => GameResult.WhiteWin
      case Models.GameResult.BlackWin => GameResult.BlackWin
      case Models.GameResult.Draw     => GameResult.Draw

  extension (c: Models.PlayerColor)
    def toSpec: PlayerColor = c match
      case Models.PlayerColor.White => PlayerColor.White
      case Models.PlayerColor.Black => PlayerColor.Black

  extension (e: Models.LiveRatingEntry)
    def toSpec: LiveRatingEntry = LiveRatingEntry(e.projected, e.diff, e.gamesPlayed)

  extension (lr: Models.LiveRating)
    def toSpec: LiveRating =
      LiveRating(
        playerId = lr.playerId,
        standard = lr.standard.map(_.toSpec),
        rapid = lr.rapid.map(_.toSpec),
        blitz = lr.blitz.map(_.toSpec),
        updatedAt = lr.updatedAt.map(_.toTimestamp)
      )

  extension (pi: domain.PlayerInfo)
    def toLeaderboardPlayer: LeaderboardPlayer =
      LeaderboardPlayer(
        id = pi.id,
        fideId = pi.fideId,
        name = pi.name,
        title = pi.title.map:
          case domain.Title.GM  => Title.GM
          case domain.Title.IM  => Title.IM
          case domain.Title.FM  => Title.FM
          case domain.Title.CM  => Title.CM
          case domain.Title.NM  => Title.NM
          case domain.Title.WGM => Title.WGM
          case domain.Title.WIM => Title.WIM
          case domain.Title.WFM => Title.WFM
          case domain.Title.WCM => Title.WCM
          case domain.Title.WNM => Title.WNM
        ,
        womenTitle = pi.womenTitle.map:
          case domain.Title.GM  => Title.GM
          case domain.Title.IM  => Title.IM
          case domain.Title.FM  => Title.FM
          case domain.Title.CM  => Title.CM
          case domain.Title.NM  => Title.NM
          case domain.Title.WGM => Title.WGM
          case domain.Title.WIM => Title.WIM
          case domain.Title.WFM => Title.WFM
          case domain.Title.WCM => Title.WCM
          case domain.Title.WNM => Title.WNM
        ,
        otherTitles = if pi.otherTitles.isEmpty then None
                      else Some(pi.otherTitles.map:
                        case domain.OtherTitle.IA  => OtherTitle.IA
                        case domain.OtherTitle.FA  => OtherTitle.FA
                        case domain.OtherTitle.NA  => OtherTitle.NA
                        case domain.OtherTitle.IO  => OtherTitle.IO
                        case domain.OtherTitle.FST => OtherTitle.FST
                        case domain.OtherTitle.FT  => OtherTitle.FT
                        case domain.OtherTitle.FI  => OtherTitle.FI
                        case domain.OtherTitle.DI  => OtherTitle.DI
                        case domain.OtherTitle.NI  => OtherTitle.NI
                        case domain.OtherTitle.SI  => OtherTitle.SI
                        case domain.OtherTitle.LSI => OtherTitle.LSI
                      ),
        standard = pi.standard,
        rapid = pi.rapid,
        blitz = pi.blitz,
        federationId = pi.federation.map(_.id),
        federationName = pi.federation.map(_.name),
        active = pi.active,
        gender = pi.gender.map:
          case domain.Gender.Female => Gender.Female
          case domain.Gender.Male   => Gender.Male
        ,
        birthYear = pi.birthYear
      )

  extension (details: Models.LiveRatingDetails)
    def toSpec(requestedPlayerId: PlayerId): LiveRatingDetails =
      LiveRatingDetails(
        summary = details.summary.toSpec,
        contributingGames = details.contributingGames.flatMap(_.toSpec(requestedPlayerId))
      )

  extension (row: Models.LiveRatingGameRow)
    def toSpec(requestedPlayerId: PlayerId): Option[ContributingGame] =
      val (color, diff, opponentFideId, opponentName): (
          Models.PlayerColor,
          Option[Int],
          Option[String],
          Option[String]
      ) =
        if row.whitePlayerId.contains(requestedPlayerId) then
          (Models.PlayerColor.White, row.whiteRatingDiff, row.blackFideId, None)
        else if row.blackPlayerId.contains(requestedPlayerId) then
          (Models.PlayerColor.Black, row.blackRatingDiff, row.whiteFideId, None)
        else
          // Requested player is not either side — skip.
          return None

      diff.map: d =>
        ContributingGame(
          gameId = row.gameId,
          broadcastTourId = row.tourId,
          broadcastRoundId = row.roundId,
          timeControl = row.timeControl.toSpec,
          opponentName = opponentName.getOrElse("unknown"),
          result = row.result.toSpec,
          playerRatingDiff = d,
          playerColor = color.toSpec,
          opponentFideId = opponentFideId.flatMap(s => fide.types.FideId.option(s)),
          roundFinishedAt = row.roundFinishedAt.map(_.toTimestamp)
        )
