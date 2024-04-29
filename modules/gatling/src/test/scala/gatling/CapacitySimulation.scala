package fide
package gatling

import io.gatling.core.Predef.*

class CapacitySimulation extends Simulation:

  import SimulationHelper.*

  val capcity = makeScenario(2, "capacity").inject(
    incrementUsersPerSec(100.0)
      .times(10)
      .eachLevelLasting(10)
      .separatedByRampsLasting(10)
      .startingFrom(50) // Double
  )

  setUp(capcity).protocols(httpProtocol)
