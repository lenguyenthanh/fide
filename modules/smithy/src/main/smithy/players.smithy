$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service PlayerService {
  version: "0.0.1",
  operations: [GetPlayers, GetPlayerById, GetPlayerByIds],
}

// tood add search operations

// todo add pagination
@readonly
@http(method: "GET", uri: "/api/players", code: 200)
operation GetPlayers {
  input: GetPlayersInput,
  output: GetPlayersOutput
}

@readonly
@http(method: "GET", uri: "/api/players/{id}", code: 200)
operation GetPlayerById {
  input: GetPlayerByIdInput,
  output: Player
  errors: [PlayerNotFound]
}

// todo limit the number of ids
@readonly
@http(method: "POST", uri: "/api/players", code: 200)
operation GetPlayerByIds {
  input: GetPlayerByIdsInput,
  output: GetPlayersOutput
}

structure GetPlayerByIdInput {
  @httpLabel
  @required
  id: PlayerId
}

structure GetPlayerByIdsInput {
  @required
  ids: PlayerIds
}

structure GetPlayersInput {
  @httpQuery("query")
  query: String
}

structure GetPlayersOutput {
  @required
  players: Players
}

list PlayerIds {
  member: PlayerId
}

@error("client")
@httpError(404)
structure PlayerNotFound {}
