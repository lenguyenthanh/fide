$version: "2"

namespace fide

use alloy#simpleRestJson

@simpleRestJson
service FideService {
  version: "1.0.0",
  operations: [GetPlayer]
}

@http(method: "GET", uri: "/players/{id}", code: 200)
operation GetPlayer {
  input: Player,
  output: Greeting
}

structure Player {
  @httpLabel
  @required
  id: String
}

structure Greeting {
  @required
  message: String
}
