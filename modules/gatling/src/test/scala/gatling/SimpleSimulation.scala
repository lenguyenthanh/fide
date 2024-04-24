package fide
package gatling

import io.gatling.core.Predef.*
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.http.Predef.*
import org.HdrHistogram.ConcurrentHistogram

import scala.concurrent.duration.DurationInt

class SimpleSimulation extends Simulation:

  import SimpleSimulation.*

  private val httpProtocol = http.baseUrl(config.serverUri)

// Add the ScenarioBuilder:
  val one = scenario("Simple Scenario")
    .exec(http("players").get("/players").check(status.is(200)))
    .exec(http("player-standard").get("/players?sort_by=standard&order=desc&size=10").check(status.is(200)))
    .exec(
      http("player-blitz-active")
        .get("/players?sort_by=blitz&order=desc&page=5&is_active=true")
        .check(status.is(200))
    )
    .exec(http("federation_summary").get("/federations/summary").check(status.is(200)))

  setUp(one.inject(config.injectionPolicy)).protocols(httpProtocol)

object SimpleSimulation:
  private val hist = new ConcurrentHistogram(1L, 10000L, 3)

  Runtime.getRuntime.addShutdownHook(
    new Thread:
      override def run(): Unit =
        hist.outputPercentileDistribution(System.out, 1.0)
  )

  object config:
    val numberOfUsers = 1000

    val serverUri = "http://localhost:9669/api"

    val injectionPolicy: OpenInjectionStep = rampUsers(numberOfUsers).during(30.seconds)
