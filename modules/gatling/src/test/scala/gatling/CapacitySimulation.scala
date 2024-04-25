package fide
package gatling

import io.gatling.core.Predef.*
import io.gatling.core.controller.inject.open.OpenInjectionStep

import scala.concurrent.duration.DurationInt

class CapacitySimulation extends Simulation:

  import SimulationHelper.*

  val user = 1000
  val capcity = makeScenario(2, "capacity").inject(
    // generate an open workload injection profile
    // with levels of 10, 15, 20, 25 and 30 arriving users per second
    // each level lasting 10 seconds
    // separated by linear ramps lasting 10 seconds
    incrementUsersPerSec(5.0)
      .times(5)
      .eachLevelLasting(10)
      .separatedByRampsLasting(10)
      .startingFrom(20) // Double
  )

  setUp(capcity).protocols(httpProtocol)
