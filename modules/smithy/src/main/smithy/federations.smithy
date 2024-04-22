$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service FederationService {
  version: "0.0.1",
  operations: [GetFederationsSummary, GetFederationSummaryWithTop10, GetFederationPlayers],
}

//@readonly
// @http(method: "GET", uri: "/api/federations", code: 200)
// operation GetFederations {
  // input: GetFederationsInput,
  // output: GetFederationsOutput
// }

@readonly
@http(method: "GET", uri: "/api/federations", code: 200)
operation GetFederationsSummary {
  input: GetFederationsInput,
  output: GetFederationsSummaryOutput
}

@readonly
@http(method: "GET", uri: "/api/federations/{id}/summary/top10", code: 200)
/// Get a federation summary by its id
operation GetFederationSummaryWithTop10 {
  input: GetFederationByIdInput,
  output: GetFederationSummaryTop10Output,
  errors: [FederationNotFound]
}

@readonly
@http(method: "GET", uri: "/api/federations/{id}/players", code: 200)
operation GetFederationPlayers {
  input: GetFederationPlayersInput,
  output: GetFederationPlayersOutput,
  errors: [FederationNotFound]
}

structure GetFederationPlayersInput {
  @httpLabel
  @required
  id: FederationId
}

structure GetFederationPlayersOutput {
  items: Players
}

structure GetFederationsSummaryOutput {
  items: FederationsSummary
}

list FederationsSummary {
  member: FederationSummary
}

structure FederationSummary {
  @required
  id: FederationId,

  @required
  name: String,

  @required
  nbPlayers: Integer,

  avg_top10_standard: Integer,

  avg_top10_rapid: Integer,

  avg_top10_blitz: Integer
}

structure GetFederationByIdInput {
  @httpLabel
  @required
  id: FederationId
}

structure GetFederationByIdOutput {
  federation: Federation
}

structure GetFederationSummaryTop10Output {
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


structure GetFederationsInput {
  @httpQuery("query")
  query: String
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
