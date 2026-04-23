$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports
use smithy4s.meta#unwrap

/// Player-related lookups: list/search, by-id fetches (FIDE or internal id), and per-player rating history.
@simpleRestJson
service PlayerService {
  version: "0.0.1"
  operations: [GetPlayers, GetPlayerByFideId, GetPlayerByFideIds, GetPlayerHistoryByFideId, GetPlayerByInternalId, GetPlayerByInternalIds, GetPlayerHistoryByInternalId],
}

/// Paginated list of players, filterable by rating ranges, titles, gender, birth year, federation, etc.
/// `nextPage` is set when a full page was returned; absent on the final page.
///
/// Defaults when omitted:
/// - page = "1", page_size = 30
/// - sort_by = "name"; order_by = "asc" if sort_by is "name", otherwise "desc"
/// - is_active, has_title, has_women_title, has_other_title: unset => no filter
/// - name, title, other_title, gender, federation_id: unset => no filter
/// - std[gte]/std[lte], rapid[gte]/rapid[lte], blitz[gte]/blitz[lte]: unset => no bound
/// - birth_year[gte], birth_year[lte]: unset => no bound
@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/players", code: 200)
operation GetPlayers {

  input :=
    @scalaImports(["fide.spec.providers.given"])
    with [SortingMixin, FilterMixin] {

    @httpQuery("page")
    @default("1")
    page: PageNumber

    @httpQuery("page_size")
    @range(min: 1, max: 100)
    @default(30)
    pageSize: PageSize

    @httpQuery("federation_id")
    federationId: FederationId
  }

  output := {
    @required
    items: Players
    @required
    totalResults: Long
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

/// Fetch a single player by their FIDE id.
@readonly
@http(method: "GET", uri: "/api/players/fide/{fide_id}", code: 200)
operation GetPlayerByFideId {
  input := {
    @httpLabel
    @required
    fide_id: FideId
  }

  output: GetPlayerByIdOutput
  errors: [PlayerFideIdNotFound, InternalServerError]
}

/// Batch-fetch players by a set of FIDE ids. Returns a map keyed by FIDE id (or internal id fallback for rows without one).
@readonly
@http(method: "POST", uri: "/api/players/fide", code: 200)
operation GetPlayerByFideIds {
  input := {
    @required
    ids: SetFideIds
  }

  output := {
    @required
    players: PlayerFideMap
  }

  errors: [InternalServerError, TooManyIds]
}

/// Map of FIDE id (as string) to player record, returned by batch lookups.
map PlayerFideMap {
  key: String
  value: GetPlayerByIdOutput
}

/// Returned when no player exists for the given internal id.
@error("client")
@httpError(404)
structure PlayerNotFound {
  @required
  id: PlayerId
}

/// Paginated monthly rating history for a player identified by FIDE id, optionally bounded by [since, until].
///
/// Defaults when omitted:
/// - page = "1", page_size = 30
/// - since, until: unset => no bound
@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/players/fide/{fide_id}/history", code: 200)
operation GetPlayerHistoryByFideId {
  input :=
    @scalaImports(["fide.spec.providers.given"]) {

    @httpLabel
    @required
    fide_id: FideId

    @httpQuery("since")
    since: YearMonthString

    @httpQuery("until")
    until: YearMonthString

    @httpQuery("page")
    @default("1")
    page: PageNumber

    @httpQuery("page_size")
    @range(min: 1, max: 100)
    @default(30)
    pageSize: PageSize
  }

  output := {
    @required
    items: RatingHistory
    nextPage: PageNumber
  }

  errors: [PlayerFideIdNotFound, InternalServerError]
}

/// One month of a player's ratings across classical/rapid/blitz. Any rating may be absent if the player wasn't rated that month.
structure RatingHistoryEntry {
  @required
  month: YearMonthString

  standard: Rating
  rapid: Rating
  blitz: Rating
}

/// Ordered list of monthly rating entries.
list RatingHistory {
  member: RatingHistoryEntry
}

/// Returned when a batch id lookup exceeds the allowed size.
@error("client")
@httpError(400)
structure TooManyIds {
  @required
  message: String
}

/// Fetch a single player by the service's internal id.
@readonly
@http(method: "GET", uri: "/api/players/internal/{id}", code: 200)
operation GetPlayerByInternalId {
  input := {
    @httpLabel
    @required
    id: PlayerId
  }

  output: GetPlayerByIdOutput
  errors: [PlayerNotFound, InternalServerError]
}

/// Batch-fetch players by a set of internal ids.
@readonly
@http(method: "POST", uri: "/api/players/internal", code: 200)
operation GetPlayerByInternalIds {
  input := {
    @required
    ids: SetPlayerIds
  }

  output := {
    @required
    players: PlayerMap
  }

  errors: [InternalServerError, TooManyIds]
}

/// Map of internal player id (as string) to player record, returned by batch lookups.
map PlayerMap {
  key: String
  value: GetPlayerByIdOutput
}

/// Non-empty set of internal player ids. Length 1 to 100.
@uniqueItems
@length(min: 1, max: 100)
@nonEmptySetFormat
@unwrap
list SetPlayerIds {
  member: PlayerId
}

/// Paginated monthly rating history for a player identified by internal id.
///
/// Defaults when omitted:
/// - page = "1", page_size = 30
/// - since, until: unset => no bound
@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/players/internal/{id}/history", code: 200)
operation GetPlayerHistoryByInternalId {
  input :=
    @scalaImports(["fide.spec.providers.given"]) {

    @httpLabel
    @required
    id: PlayerId

    @httpQuery("since")
    since: YearMonthString

    @httpQuery("until")
    until: YearMonthString

    @httpQuery("page")
    @default("1")
    page: PageNumber

    @httpQuery("page_size")
    @range(min: 1, max: 100)
    @default(30)
    pageSize: PageSize
  }

  output := {
    @required
    items: RatingHistory
    nextPage: PageNumber
  }

  errors: [PlayerNotFound, InternalServerError]
}
