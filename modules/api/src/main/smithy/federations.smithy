$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service FederationService {
  version: "0.0.1"
  operations: [GetFederationsSummary, GetFederationSummaryById, GetFederationPlayersById],
}

@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/federations/summary", code: 200)
operation GetFederationsSummary {
  input := {
    @httpQuery("page")
    page: PageNumber = "1"
    @httpQuery("page_size")
    @range(min: 1, max: 100)
    pageSize: PageSize = 30
  }

  output:= {
    @required
    items: FederationsSummary
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

/// Get a federation summary by its id
@readonly
@http(method: "GET", uri: "/api/federations/summary/{id}", code: 200)
operation GetFederationSummaryById {
  input := {
    @httpLabel
    @required
    id: FederationId
  }

  output: GetFederationSummaryByIdOutput
  errors: [FederationNotFound, InternalServerError]
}

@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/federations/summary/{id}/players", code: 200)
operation GetFederationPlayersById {
  input := with [SortingMixin, FilterMixin] {
    @httpLabel
    @required
    id: FederationId
    @httpQuery("page")
    page: PageNumber = "1"
    @httpQuery("page_size")
    @range(min: 1, max: 100)
    pageSize: PageSize = 30
  }

  output := {
    @required
    items: Players
    nextPage: PageNumber
  }

  errors: [FederationNotFound, InternalServerError]
}

list FederationsSummary {
  member: GetFederationSummaryByIdOutput
}

structure GetFederationSummaryByIdOutput {
  @required
  id: FederationId

  @required
  name: String

  @required
  nbPlayers: Integer

  @required
  standard: Stats

  @required
  rapid: Stats

  @required
  blitz: Stats
}


list Federations {
  member: Federation
}

structure Stats {
  @required
  rank: Integer

  @required
  nbPlayers: Integer

  @required
  top10Rating: Integer
}


@error("client")
@httpError(404)
structure FederationNotFound {
  @required
  id: FederationId
}
