$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports

/// Live (unofficial next-period) rating projections computed from Lichess broadcast games
/// since the last official FIDE monthly release. See issue #418.
@simpleRestJson
service LiveRatingService {
  version: "0.0.1"
  operations: [
    GetLiveRatingLeaderboard,
    GetLiveRatingByFideId,
    GetLiveRatingDetailsByFideId,
    GetLiveRatingStatus
  ]
}

/// Paginated leaderboard of players ranked by projected rating for a given time control.
/// Only players with at least one contributing game in the requested time control are returned.
/// Inactive players (no rated games in the last 12 months, per FIDE) are excluded by default.
///
/// Defaults when omitted:
/// - time_control = "standard"
/// - page = "1", page_size = 100 (leaderboards default to the cap; see decision #34)
/// - order is always descending by projected rating (non-configurable per decision #20)
/// - is_active defaults to true (inactive players excluded — mixin default)
/// - has_title, has_women_title, has_other_title, title, other_title, gender, federation_id: unset => no filter
/// - birth_year[gte], birth_year[lte]: unset => no bound
@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/live-ratings/leaderboard", code: 200)
operation GetLiveRatingLeaderboard {
  input :=
    @scalaImports(["fide.spec.providers.given"])
    with [FilterMixin] {

    @httpQuery("time_control")
    @default("standard")
    timeControl: TimeControl

    @httpQuery("page")
    @default("1")
    page: PageNumber

    @httpQuery("page_size")
    @range(min: 1, max: 100)
    @default(100)
    pageSize: PageSize

    @httpQuery("federation_id")
    federationId: FederationId
  }

  output := {
    @required
    items: LeaderboardEntries
    @required
    totalResults: Long
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

/// Fetch a player's live-rating summary by FIDE id.
/// Returns 200 with empty per-TC entries if the player is known but has no contributions
/// this cycle. Returns 404 when the FIDE id is unknown to this service.
@readonly
@http(method: "GET", uri: "/api/live-ratings/fide/{fide_id}", code: 200)
operation GetLiveRatingByFideId {
  input := {
    @httpLabel
    @required
    fide_id: FideId
  }

  output := {
    @required
    liveRating: LiveRating
  }

  errors: [PlayerFideIdNotFound, InternalServerError]
}

/// Fetch a player's full live-rating breakdown by FIDE id: per-TC aggregation plus the
/// list of contributing games. `contributingGames` is capped at 100 most-recent
/// (ordered by `round_finished_at` DESC) per decision #41.
@readonly
@http(method: "GET", uri: "/api/live-ratings/fide/{fide_id}/details", code: 200)
operation GetLiveRatingDetailsByFideId {
  input := {
    @httpLabel
    @required
    fide_id: FideId
  }

  output := {
    @required
    details: LiveRatingDetails
  }

  errors: [PlayerFideIdNotFound, InternalServerError]
}

/// System-level live-rating ingest status. Consumers use this for staleness detection
/// beyond per-player updatedAt (e.g., distinguishing "CLI backfill running" from
/// "system stuck"). See decision #35.
@readonly
@http(method: "GET", uri: "/api/live-ratings/status", code: 200)
operation GetLiveRatingStatus {
  output := {
    @required
    status: LiveRatingStatus
  }

  errors: [InternalServerError]
}

// ============================================================
// Shapes
// ============================================================

/// FIDE time control classification used for live ratings.
enum TimeControl {
  Standard = "standard"
  Rapid    = "rapid"
  Blitz    = "blitz"
}

/// Game outcome (decisive only — unfinished games are filtered at ingest per decision #37).
enum GameResult {
  WhiteWin = "1-0"
  BlackWin = "0-1"
  Draw     = "1/2-1/2"
}

/// Side a player occupied in a given game.
enum PlayerColor {
  White = "white"
  Black = "black"
}

/// Per-TC projection detail.
structure LiveRatingEntry {
  /// Official rating + sum of Elo deltas from contributing games.
  @required
  projected: Rating

  /// Sum of Elo deltas since the last official FIDE release (may be negative).
  @required
  diff: Integer

  /// Number of rated games contributing to this projection in the requested time control.
  @required
  gamesPlayed: Integer
}

/// Live-rating summary for a player across all three FIDE time controls.
/// Each TC is absent when no contributing games exist. `updatedAt` is null for a
/// known player with zero contributions (design decision #36).
structure LiveRating {
  @required
  playerId: PlayerId

  standard: LiveRatingEntry
  rapid:    LiveRatingEntry
  blitz:    LiveRatingEntry

  /// Timestamp of the most recent contributing game ingested for this player.
  /// Null when the player has no contributions.
  updatedAt: Timestamp
}

/// Compact player info used in live-rating leaderboard rows. Excludes fields not
/// needed for leaderboard rendering. Decision #22.
structure LeaderboardPlayer {
  @required
  id: PlayerId

  fideId: FideId

  @required
  name: String

  title: Title
  womenTitle: Title
  otherTitles: OtherTitles

  /// Official ratings at the time of the last monthly FIDE release.
  standard: Rating
  rapid: Rating
  blitz: Rating

  federationId: FederationId
  federationName: String

  @required
  active: Boolean

  gender: Gender
  birthYear: Integer
}

/// One leaderboard row: trimmed player plus live projection for the requested TC.
structure LeaderboardEntry {
  @required
  player: LeaderboardPlayer

  @required
  liveRating: LiveRatingEntry
}

list LeaderboardEntries {
  member: LeaderboardEntry
}

/// Detail endpoint response: summary + up to 100 most-recent contributing games.
structure LiveRatingDetails {
  @required
  summary: LiveRating

  @required
  contributingGames: ContributingGames
}

list ContributingGames {
  member: ContributingGame
}

/// A single contributing game. `playerRatingDiff` is the *requested player's* delta
/// (positive = gained rating); `playerColor` tells the consumer which side they played.
/// Design decision #40.
structure ContributingGame {
  @required
  gameId: String

  @required
  broadcastTourId: String

  @required
  broadcastRoundId: String

  @required
  timeControl: TimeControl

  /// Opponent's FIDE id, if known to Lichess.
  opponentFideId: FideId

  @required
  opponentName: String

  @required
  result: GameResult

  /// This player's Elo delta for this game.
  @required
  playerRatingDiff: Integer

  /// Which side the requested player was on.
  @required
  playerColor: PlayerColor

  /// Round finish time as reported by Lichess, if available.
  roundFinishedAt: Timestamp
}

/// System-level ingest status for external observability.
structure LiveRatingStatus {
  /// Timestamp of the most recent backend tick start.
  lastTickStartedAt: Timestamp
  /// Timestamp of the most recent completed tick.
  lastTickCompletedAt: Timestamp
  /// Timestamp of the most recent monthly-reset.
  lastMonthlyResetAt: Timestamp
  /// Timestamp of the most recent CLI backfill run.
  lastBackfillAt: Timestamp
  /// If set, indicates which subsystem currently holds the live-rating ingest lock
  /// ("tick", "backfill", "monthly_reset").
  lockHeldBy: String
  /// Timestamp at which the current lock holder acquired the lock.
  lockHeldSince: Timestamp
}
