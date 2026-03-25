$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports
use smithy4s.meta#unwrap

@simpleRestJson
service HistoryService {
  version: "0.0.1"
  operations: [GetHistoricalPlayers, GetHistoricalPlayerById, GetHistoricalFederationsSummary, GetHistoricalFederationSummaryById, GetAvailableMonths],
}

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
    page: PageNumber = "1"

    @httpQuery("page_size")
    @range(min: 1, max: 100)
    pageSize: PageSize = 30

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

@readonly
@http(method: "GET", uri: "/api/history/players/{id}", code: 200)
operation GetHistoricalPlayerById {
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
    page: PageNumber = "1"
    @httpQuery("page_size")
    @range(min: 1, max: 100)
    pageSize: PageSize = 30
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

@readonly
@http(method: "GET", uri: "/api/history/months", code: 200)
operation GetAvailableMonths {
  output := {
    @required
    months: YearMonthList
  }
}

@YearMonthFormat
@unwrap
string YearMonthString

list YearMonthList {
  member: YearMonthString
}

structure GetHistoricalPlayerByIdOutput {
  @required
  id: PlayerId

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

list HistoricalPlayers {
  member: GetHistoricalPlayerByIdOutput
}
