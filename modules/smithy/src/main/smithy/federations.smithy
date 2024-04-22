$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service FederationService {
  version: "0.0.1",
  operations: [GetFederationsSummary, GetFederationSummaryById, GetFederationPlayersById],
}

@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/federations/summary", code: 200)
operation GetFederationsSummary {
  input: GetFederationsSummaryInput,
  output: GetFederationsSummaryOutput
}

/// Get a federation summary by its id
@readonly
@http(method: "GET", uri: "/api/federations/summary/{id}", code: 200)
operation GetFederationSummaryById {
  input: GetFederationByIdInput,
  output: FederationSummary,
  errors: [FederationNotFound]
}

@readonly
@paginated(inputToken: "page", outputToken: "nextPage", pageSize: "pageSize")
@http(method: "GET", uri: "/api/federations/summary/{id}/players", code: 200)
operation GetFederationPlayersById {
  input: GetFederationPlayersInput,
  output: GetFederationPlayersByIdOutput,
  errors: [FederationNotFound]
}

structure GetFederationPlayersInput {
  @httpLabel
  @required
  id: FederationId
  @httpQuery("page")
  page: PageNumber
  @httpQuery("page_size")
  @range(min: 1, max: 100)
  pageSize: Integer
}

structure GetFederationPlayersByIdOutput {
  @required
  items: Players
  nextPage: PageNumber
}

structure GetFederationsSummaryInput {
  @httpQuery("page")
  page: PageNumber
  @httpQuery("page_size")
  @range(min: 1, max: 100)
  pageSize: Integer
}

structure GetFederationsSummaryOutput {
  @required
  items: FederationsSummary
  nextPage: PageNumber
}

list FederationsSummary {
  member: FederationSummary
}

structure GetFederationByIdInput {
  @httpLabel
  @required
  id: FederationId
}

structure GetFederationByIdOutput {
  federation: Federation
}

structure FederationSummary {
  @required
  id: FederationId,

  @required
  name: String,

  @required
  nbPlayers: Integer,

  @required
  standard: Stats,

  @required
  rapid: Stats,

  @required
  blitz: Stats
}


structure GetFederationsOutput {
  items: Federations
}

list Federations {
  member: Federation
}

structure Stats {
  @required
  rank: Integer,

  @required
  nbPlayers: Integer,

  @required
  top10Rating: Integer,
}


@error("client")
@httpError(404)
structure FederationNotFound {}
