$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service FederationService {
  version: "0.0.1",
  operations: [GetFederations, GetFederationById],
}

@readonly
@http(method: "GET", uri: "/api/federations", code: 200)
operation GetFederations {
  input: GetFederationsInput,
  output: GetFederationsOutput
}

@readonly
@http(method: "GET", uri: "/api/federations/{id}", code: 200)
operation GetFederationById {
  input: GetFederationByIdInput,
  output: GetFederationByIdOutput,
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
  players: Players
}

structure GetFederationByIdInput {
  @httpLabel
  @required
  id: FederationId
}

structure GetFederationByIdOutput {
  federation: Federation
}

structure GetFederationsInput {
  @httpQuery("query")
  query: String
}

structure GetFederationsOutput {
  federations: Federations
}

list Federations {
  member: Federation
}

structure Federation {
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
  blitz: Stats,

  @required
  updatedAt: Timestamp
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
