package fide
package gatling

import io.gatling.core.Predef.*
import io.gatling.core.controller.inject.open.OpenInjectionStep

import scala.concurrent.duration.DurationInt

class StressSimulation extends Simulation:

  import SimulationHelper.*

  val nbUsers = 1000
  val stress  = makeScenario(nbUsers, "stress").inject(rampUsers(nbUsers).during(30.seconds))

  setUp(stress).protocols(httpProtocol)
