$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports

@simpleRestJson
service PlayerService {
  version: "0.0.1"
  operations: [GetPlayers, GetPlayerById, GetPlayerByIds],
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
  }

  output := {
    @required
    items: Players
    nextPage: PageNumber
  }

  errors: [InternalServerError]
}

@readonly
@http(method: "GET", uri: "/api/players/{id}", code: 200)
operation GetPlayerById {
  input := {
    @httpLabel
    @required
    id: PlayerId
  }

  output: GetPlayerByIdOutput
  errors: [PlayerNotFound, InternalServerError]
}

@readonly
@http(method: "POST", uri: "/api/players", code: 200)
operation GetPlayerByIds {
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
list SetPlayerIds {
  member: PlayerId
}

@error("client")
@httpError(404)
structure PlayerNotFound {
  @required
  id: PlayerId
}

@error("client")
@httpError(400)
structure TooManyIds {
  @required
  message: String
}
