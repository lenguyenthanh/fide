$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service HealthService {
  version: "0.0.1",
  operations: [HealthCheck]
}

@http(method: "GET", uri: "/api/health", code: 200)
operation HealthCheck {
  output := {
    service: String
  }
}
