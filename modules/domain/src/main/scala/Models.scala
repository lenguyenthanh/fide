package fide
package domain

import fide.types.*

import java.time.Instant

object Models:
  enum Order(val value: String):
    case Asc  extends Order("ASC")
    case Desc extends Order("DESC")

  enum SortBy(val value: String):
    case Name      extends SortBy("name")
    case Standard  extends SortBy("standard")
    case Rapid     extends SortBy("rapid")
    case Blitz     extends SortBy("blitz")
    case BirthYear extends SortBy("birth_year")

  case class Sorting(
      sortBy: SortBy,
      orderBy: Order
  )

  object Sorting:
    def default                                                           = Sorting(SortBy.Name, Order.Desc)
    def fromOption(sortBy: Option[SortBy], order: Option[Order]): Sorting =
      val _sortBy      = sortBy.getOrElse(Models.SortBy.Name)
      val defaultOrder =
        _sortBy match
          case Models.SortBy.Name => Models.Order.Asc
          case _                  => Models.Order.Desc
      val _order = order.getOrElse(defaultOrder)
      Models.Sorting(_sortBy, _order)

  case class Pagination(page: PageNumber, size: PageSize):
    def next: Pagination     = copy(page = page.succ)
    def nextPage: PageNumber = page.succ
    def offset: Int          = (page - 1) * size

  case class RatingRange(min: Option[Rating], max: Option[Rating])
  object RatingRange:
    def empty = RatingRange(None, None)

  case class PlayerFilter(
      name: Option[String],
      isActive: Option[Boolean],
      standard: RatingRange,
      rapid: RatingRange,
      blitz: RatingRange,
      federationId: Option[FederationId],
      titles: Option[List[Title]],
      otherTitles: Option[List[OtherTitle]],
      gender: Option[Gender],
      birthYearMin: Option[BirthYear],
      birthYearMax: Option[BirthYear],
      hasTitle: Option[Boolean],
      hasWomenTitle: Option[Boolean],
      hasOtherTitle: Option[Boolean]
  )

  object PlayerFilter:
    val default =
      PlayerFilter(
        None,
        None,
        RatingRange.empty,
        RatingRange.empty,
        RatingRange.empty,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None
      )

  enum PostgresStatus:
    case Ok
    case Unreachable

  // ============================================================
  // Live ratings (issue #418 — see .claude/docs/live-ratings-design.md)
  // ============================================================

  /** FIDE time control classification. Mirrors the DB enum `time_control` and scalachess's `chess.FideTC`. */
  enum TimeControl(val value: String):
    case Standard extends TimeControl("standard")
    case Rapid    extends TimeControl("rapid")
    case Blitz    extends TimeControl("blitz")

  object TimeControl:
    def apply(value: String): Option[TimeControl] =
      TimeControl.values.find(_.value == value)

  /** Game outcome, restricted to decisive results (unfinished games are filtered at ingest). */
  enum GameResult(val value: String):
    case WhiteWin extends GameResult("1-0")
    case BlackWin extends GameResult("0-1")
    case Draw     extends GameResult("1/2-1/2")

  object GameResult:
    def apply(value: String): Option[GameResult] =
      GameResult.values.find(_.value == value)

  /** Side a player occupied in a given game. */
  enum PlayerColor(val value: String):
    case White extends PlayerColor("white")
    case Black extends PlayerColor("black")

  /** Per-time-control projection for a single player. */
  case class LiveRatingEntry(
      diff: Int,
      gamesPlayed: Int,
      projected: Rating
  )

  /** Per-player live-rating summary across all three FIDE time controls.
    *
    * Each TC entry is absent when no contributing games exist. `updatedAt` is None for a
    * known player with zero contributions (see design §10 / decision #53).
    */
  case class LiveRating(
      playerId: PlayerId,
      standard: Option[LiveRatingEntry],
      rapid: Option[LiveRatingEntry],
      blitz: Option[LiveRatingEntry],
      updatedAt: Option[Instant]
  )

  /** Leaderboard row: the player's official record joined with the live projection
    * for the requested time control.
    */
  case class LeaderboardEntry(
      player: PlayerInfo,
      liveEntry: LiveRatingEntry
  )

  /** Per-game ledger row. One-to-one with `live_rating_games` rows.
    *
    * Either `whitePlayerId` or `blackPlayerId` is non-null (CHECK constraint in schema).
    * Unresolved sides keep the raw `fideId` for audit purposes.
    */
  case class LiveRatingGameRow(
      gameId: String,
      tourId: String,
      roundId: String,
      timeControl: TimeControl,
      whitePlayerId: Option[PlayerId],
      blackPlayerId: Option[PlayerId],
      whiteFideId: Option[String],
      blackFideId: Option[String],
      whiteRatingDiff: Option[Int],
      blackRatingDiff: Option[Int],
      result: GameResult,
      roundFinishedAt: Option[Instant]
  )

  /** Full live-rating breakdown for a player: per-TC aggregation plus individual contributing games.
    *
    * `contributingGames` is capped at 100 most-recent (ORDER BY `round_finished_at` DESC)
    * per decision #41.
    */
  case class LiveRatingDetails(
      summary: LiveRating,
      contributingGames: List[LiveRatingGameRow]
  )

  /** Status of the live-rating ingest subsystem, exposed via `GET /api/live-ratings/status`.
    *
    * `lockHeldBy` distinguishes "backfill in progress" from "system stuck" (decision #35).
    */
  case class LiveRatingStatus(
      lastTickStartedAt: Option[Instant],
      lastTickCompletedAt: Option[Instant],
      lastMonthlyResetAt: Option[Instant],
      lastBackfillAt: Option[Instant],
      lockHeldBy: Option[String],
      lockHeldSince: Option[Instant]
  )
