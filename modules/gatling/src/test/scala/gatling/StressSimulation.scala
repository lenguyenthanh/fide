package fide
package gatling

import io.gatling.core.Predef.*
import io.gatling.core.controller.inject.open.OpenInjectionStep

import scala.concurrent.duration.DurationInt

class StressSimulation extends Simulation:

  import SimulationHelper.*

  val nbUsers = 10
  val stress  = makeScenario(config.maxPages, "stress").inject(rampUsers(nbUsers).during(10.seconds))

  setUp(stress).protocols(httpProtocol)
