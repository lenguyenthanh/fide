$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports
use smithy4s.meta#unwrap

/// Historical (monthly-snapshot) variants of the player and federation endpoints, plus a months index.
/// All historical lookups require a `month` query parameter in "YYYY-MM" form.
@simpleRestJson
service HistoryService {
  version: "0.0.1"
  operations: [GetHistoricalPlayers, GetHistoricalPlayerByFideId, GetHistoricalPlayerByInternalId, GetHistoricalFederationsSummary, GetHistoricalFederationSummaryById, GetAvailableMonths],
}

/// Paginated list of players as of a given month, with the same filters/sorting as GetPlayers.
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
@http(method: "GET", uri: "/api/history/players", code: 200)
operation GetHistoricalPlayers {
  input :=
    @scalaImports(["fide.spec.providers.given"])
    with [SortingMixin, FilterMixin] {

    @required
    @httpQuery("month")
    month: YearMonthString

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
    items: HistoricalPlayers
    @required
    totalResults: Long
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

/// Snapshot of a single player (by FIDE id) for a given month.
@readonly
@http(method: "GET", uri: "/api/history/players/fide/{fide_id}", code: 200)
operation GetHistoricalPlayerByFideId {
  input :=
    @scalaImports(["fide.spec.providers.given"]) {
    @httpLabel
    @required
    fide_id: FideId

    @required
    @httpQuery("month")
    month: YearMonthString
  }

  output: GetHistoricalPlayerByIdOutput
  errors: [PlayerFideIdNotFound, InternalServerError]
}

/// Paginated federation summaries as of a given month.
///
/// Defaults when omitted:
/// - page = "1", page_size = 30
@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/history/federations/summary", code: 200)
operation GetHistoricalFederationsSummary {
  input :=
    @scalaImports(["fide.spec.providers.given"]) {

    @required
    @httpQuery("month")
    month: YearMonthString

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
    items: FederationsSummary
    @required
    totalResults: Long
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

/// Snapshot of a federation summary for a given month.
@readonly
@http(method: "GET", uri: "/api/history/federations/summary/{id}", code: 200)
operation GetHistoricalFederationSummaryById {
  input :=
    @scalaImports(["fide.spec.providers.given"]) {
    @httpLabel
    @required
    id: FederationId

    @required
    @httpQuery("month")
    month: YearMonthString
  }

  output: GetFederationSummaryByIdOutput
  errors: [FederationNotFound, InternalServerError]
}

/// Lists all months for which a historical snapshot is available.
@readonly
@http(method: "GET", uri: "/api/history/months", code: 200)
operation GetAvailableMonths {
  output := {
    @required
    months: YearMonthList
  }
}

/// Month identifier in "YYYY-MM" form (e.g. "2024-11").
@YearMonthFormat
@unwrap
string YearMonthString

/// Ordered list of months.
list YearMonthList {
  member: YearMonthString
}

/// Historical player record: like GetPlayerByIdOutput, pinned to a specific month and including K-factor fields.
structure GetHistoricalPlayerByIdOutput {
  @required
  id: PlayerId

  fideId: FideId

  @required
  name: String

  @required
  month: YearMonthString

  title: Title
  womenTitle: Title
  otherTitles: OtherTitles

  standard: Rating
  standardK: Integer
  rapid: Rating
  rapidK: Integer
  blitz: Rating
  blitzK: Integer

  gender: Gender
  birthYear: Integer
  @required
  active: Boolean

  federation: Federation
}

/// A page of historical player records.
list HistoricalPlayers {
  member: GetHistoricalPlayerByIdOutput
}

/// Snapshot of a single player (by internal id) for a given month.
@readonly
@http(method: "GET", uri: "/api/history/players/internal/{id}", code: 200)
operation GetHistoricalPlayerByInternalId {
  input :=
    @scalaImports(["fide.spec.providers.given"]) {
    @httpLabel
    @required
    id: PlayerId

    @required
    @httpQuery("month")
    month: YearMonthString
  }

  output: GetHistoricalPlayerByIdOutput
  errors: [PlayerNotFound, InternalServerError]
}
