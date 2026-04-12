$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports
use smithy4s.meta#unwrap

@simpleRestJson
service PlayerService {
  version: "0.0.1"
  operations: [GetPlayers, GetPlayerByFideId, GetPlayerByFideIds, GetPlayerHistoryByFideId, GetPlayerByInternalId, GetPlayerByInternalIds, GetPlayerHistoryByInternalId],
}

@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/players", code: 200)
operation GetPlayers {

  input :=
    @scalaImports(["fide.spec.providers.given"])
    with [SortingMixin, FilterMixin] {

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
    items: Players
    @required
    totalResults: Long
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

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

map PlayerFideMap {
  key: String
  value: GetPlayerByIdOutput
}

@error("client")
@httpError(404)
structure PlayerNotFound {
  @required
  id: PlayerId
}

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
    page: PageNumber = "1"

    @httpQuery("page_size")
    @range(min: 1, max: 100)
    pageSize: PageSize = 30
  }

  output := {
    @required
    items: RatingHistory
    nextPage: PageNumber
  }

  errors: [PlayerFideIdNotFound, InternalServerError]
}

structure RatingHistoryEntry {
  @required
  month: YearMonthString

  standard: Rating
  rapid: Rating
  blitz: Rating
}

list RatingHistory {
  member: RatingHistoryEntry
}

@error("client")
@httpError(400)
structure TooManyIds {
  @required
  message: String
}

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

map PlayerMap {
  key: String
  value: GetPlayerByIdOutput
}

@uniqueItems
@length(min: 1, max: 100)
@nonEmptySetFormat
@unwrap
list SetPlayerIds {
  member: PlayerId
}

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
    page: PageNumber = "1"

    @httpQuery("page_size")
    @range(min: 1, max: 100)
    pageSize: PageSize = 30
  }

  output := {
    @required
    items: RatingHistory
    nextPage: PageNumber
  }

  errors: [PlayerNotFound, InternalServerError]
}
