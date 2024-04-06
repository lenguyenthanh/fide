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
  inactive: Boolean
  @required
  updatedAt: Timestamp

  federation: Federation
}

list Players {
  member: Player
}

