$version: "2"

namespace fide.spec

use alloy#simpleRestJson

@simpleRestJson
service HealthService {
  version: "0.0.1"
  operations: [HealthCheck]
}

@readonly
@http(method: "GET", uri: "/api/health", code: 200)
operation HealthCheck {
  output: HealthStatusOutput
}

enum PostgresStatus {
  Ok = "ok"
  Unreachable = "unreachable"
}

structure HealthStatusOutput {
  @required
  postgres: PostgresStatus
}
