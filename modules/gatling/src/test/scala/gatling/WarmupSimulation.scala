package fide
package gatling

import io.gatling.core.Predef.*
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.http.Predef.*

import scala.concurrent.duration.DurationInt

class WarmupSimulation extends Simulation:

  import SimulationHelper.*

  val nbUsers  = 100
  val warmup = makeScenario(1, "warmup").inject(rampUsers(nbUsers).during(30.seconds))
  setUp(warmup).protocols(httpProtocol)

