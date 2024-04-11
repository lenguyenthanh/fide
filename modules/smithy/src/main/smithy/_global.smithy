$version: "2.0"

namespace fide.spec

@error("client")
@httpError(400)
structure ValidationError {
  @required
  message: String
}

@error("server")
@httpError(501)
structure NotImplementedYetError {
  @required
  message: String
}

@error("server")
@httpError(500)
structure InternalServerError {
  @required
  message: String
}

integer PlayerId
string FederationId
integer Rating

enum Title {
  GM = "GM"
  WGM = "WGM"
  IM = "IM"
  WIM = "WIM"
  FM = "FM"
  WFM = "WFM"
  NM = "NM"
  CM = "CM"
  WCM = "WCM"
  WNM = "WNM"
}

structure Federation {
  @required
  id: FederationId,

  @required
  name: String,

}

structure Player {
  @required
  id: PlayerId

  @required
  name: String

  title: Title
  womenTitle: Title

  standard: Rating
  rapid: Rating
  blitz: Rating

  year: Integer
  @required
  active: Boolean
  @required
  updatedAt: Timestamp

  federation: Federation
}

list Players {
  member: Player
}

enum Order {
  Asc = "asc"
  Desc = "desc"
}

enum SortBy {
  Name = "name"
  Standard = "standard"
  Rapid = "rapid"
  Blitz = "blitz"
  Year = "year"
}

@mixin
structure SortingMixin {
  @httpQuery("sort_by")
  sortBy: SortBy
  @httpQuery("order_by")
  order: Order
}

structure StandardFilterRange {
  @httpQuery("std[gte]")
  min: Rating
  @httpQuery("std[lte]")
  max: Rating
}

@mixin
structure FilterMixin {
  @httpQuery("isActive")
  isActive: Boolean
  @httpQuery("std[gte]")
  standardMin: Rating
  @httpQuery("std[lte]")
  standardMax: Rating
  @httpQuery("rapid[gte]")
  rapidMin: Rating
  @httpQuery("rapid[lte]")
  rapidMax: Rating
  @httpQuery("blitz[gte]")
  blitzMin: Rating
  @httpQuery("blitz[lte]")
  blitzMax: Rating
  @httpQuery("name")
  name: String
}
