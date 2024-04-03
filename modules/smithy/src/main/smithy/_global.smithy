$version: "2.0"

namespace fide.spec

@error("client")
@httpError(400)
structure ValidationError {
  @required
  message: String
}

integer PlayerId
string FederationId
integer Rating

enum FideTitle {
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


list Players {
  member: Player
}

structure Player {
  @required
  id: PlayerId

  @required
  name: String

  title: FideTitle

  standard: Rating
  rapid: Rating
  blitz: Rating

  year: Integer
  inactive: Boolean
  fetchedAt: Timestamp

  federationId: FederationId
}

