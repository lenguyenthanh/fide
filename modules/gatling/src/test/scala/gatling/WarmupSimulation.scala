package fide
package gatling

import io.gatling.core.Predef.*

import scala.concurrent.duration.DurationInt

class WarmupSimulation extends Simulation:

  import SimulationHelper.*

  val nbUsers = 2000
  val warmup  = makeScenario(10, "warmup").inject(rampUsers(nbUsers).during(30.seconds))

  setUp(warmup).protocols(httpProtocol)
