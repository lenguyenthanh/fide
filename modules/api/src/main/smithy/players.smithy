$version: "2"

namespace fide.spec

use alloy#simpleRestJson
use smithy4s.meta#scalaImports
use smithy4s.meta#unwrap

@simpleRestJson
service PlayerService {
  version: "0.0.1"
  operations: [GetPlayers, GetPlayerById, GetPlayerByIds, GetPlayerRatingHistory, GetPlayersRatingsByMonth],
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
@length(min: 1, max: 100)
@nonEmptySetFormat
@unwrap
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

@readonly
@http(method: "GET", uri: "/api/players/{id}/rating-history", code: 200)
operation GetPlayerRatingHistory {
  input := {
    @httpLabel
    @required
    id: PlayerId
    
    @httpQuery("limit")
    @range(min: 1, max: 1000)
    limit: Integer
    
    @httpQuery("page")
    @range(min: 1, max: 10000)
    page: Integer
  }

  output := {
    @required
    playerId: PlayerId
    @required
    history: RatingHistoryEntries
    @required
    totalCount: Long
    nextPage: Integer
  }

  errors: [PlayerNotFound, InternalServerError]
}

list RatingHistoryEntries {
  member: RatingHistoryEntryOutput
}

structure RatingHistoryEntryOutput {
  standard: Rating
  standardK: Integer
  rapid: Rating
  rapidK: Integer
  blitz: Rating
  blitzK: Integer
  
  year: Integer      // Derived from epoch-based month index
  @required
  month: Integer     // Epoch-based month index: (year - 1970) * 12 + (month - 1)
  @required
  recordedAt: Timestamp
  @required
  createdAt: Timestamp
}

@readonly
@http(method: "GET", uri: "/api/players/ratings/{year}/{month}", code: 200)
operation GetPlayersRatingsByMonth {
  input := {
    @httpLabel
    @required
    year: Integer
    
    @httpLabel
    @required
    @range(min: 1, max: 12)
    month: Integer
    
    @httpQuery("limit")
    @range(min: 1, max: 1000)
    limit: Integer = 100
    
    @httpQuery("page")
    @range(min: 1, max: 10000)
    page: Integer = 1
  }

  output := {
    @required
    year: Integer
    @required
    month: Integer
    @required
    ratings: PlayerMonthlyRatings
    @required
    totalCount: Long
    nextPage: Integer
  }

  errors: [InternalServerError]
}

list PlayerMonthlyRatings {
  member: PlayerMonthlyRating
}

structure PlayerMonthlyRating {
  @required
  playerId: PlayerId
  @required
  playerName: String
  
  standard: Rating
  standardK: Integer
  rapid: Rating
  rapidK: Integer
  blitz: Rating
  blitzK: Integer
}
