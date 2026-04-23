$version: "2"

namespace fide.spec

use alloy#simpleRestJson

/// Liveness/readiness probe for the service and its dependencies.
@simpleRestJson
service HealthService {
  version: "0.0.1"
  operations: [HealthCheck]
}

/// Reports the current status of each backing dependency.
@readonly
@http(method: "GET", uri: "/api/health", code: 200)
operation HealthCheck {
  output: HealthStatusOutput
}

/// Status of the Postgres connection used by the service.
enum PostgresStatus {
  Ok = "ok"
  Unreachable = "unreachable"
}

/// Health-check response body.
structure HealthStatusOutput {
  @required
  postgres: PostgresStatus
}
