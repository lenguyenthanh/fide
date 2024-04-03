$version: "2.0"

namespace fide.spec

@error("client")
@httpError(400)
structure ValidationError { // 1
  @required
  message: String
}

integer FideId
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

