package fide
package gatling

import io.gatling.core.Predef.*
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.http.Predef.*
import org.HdrHistogram.ConcurrentHistogram

class SimpleSimulation extends Simulation:

  import SimpleSimulation.*

  private val httpProtocol = http.baseUrl(config.serverUri)

// Add the ScenarioBuilder:
  val one = scenario("My Scenario")
    .exec(http("players").get("/players/").check(status.is(200)))

  setUp(one.inject(config.injectionPolicy)).protocols(httpProtocol)

object SimpleSimulation:
  private val hist = new ConcurrentHistogram(1L, 10000L, 3)

  Runtime.getRuntime.addShutdownHook(
    new Thread:
      override def run(): Unit =
        hist.outputPercentileDistribution(System.out, 1.0)
  )

  object config:
    val numberOfUsers = 25

    val serverUri = "localhost:9669/api"

    val injectionPolicy: OpenInjectionStep = constantUsersPerSec(25).during(10)
