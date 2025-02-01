package fide
package test

import cats.Show
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.comcast.ip4s.*
import fide.db.Db
import fide.db.test.Containers
import fide.domain.Federation
import fide.domain.Models.*
import fide.types.PlayerId
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*
import weaver.scalacheck.Checkers

import Arbitraries.given

object DbIntegrationSuite extends IOSuite with Checkers:

  given Logger[IO] = NoOpLogger[IO]

  override type Res = Db

  override def sharedResource: Resource[IO, Db] =
    for
      dbConfig <- Containers.start
      config = testAppConfig(dbConfig)
      resources <- AppResources.instance(config)
      _         <- IO.println("Starting crawling").toResource
      _         <- resources.crawler.crawl.toResource
      _         <- IO.println("finished crawling").toResource
    yield resources.db

  def testAppConfig(postgres: db.PostgresConfig) = AppConfig(
    server = HttpServerConfig(ip"0.0.0.0", port"9999", shutdownTimeout = 1),
    crawler = crawler.CrawlerConfig(100, 40),
    crawlerJob = CrawlerJobConfig(0, 1000),
    postgres = postgres
  )

  given Show[Sorting]      = Show.fromToString
  given Show[Pagination]   = Show.fromToString
  given Show[PlayerFilter] = Show.fromToString
  given Show[PlayerId]     = Show.fromToString

  test("allPlayers & playerById & playersByIds"): db =>
    forall: (sorting: Sorting, paging: Pagination, filter: PlayerFilter) =>
      for
        players  <- db.allPlayers(sorting, paging, filter)
        players1 <- players.map(_.id).traverse(db.playerById).map(_.flatten)
        players2 <- players.nonEmpty.pure[IO].ifM(db.playersByIds(players.map(_.id).toSet), Nil.pure[IO])
      yield expect(
        players1.toSet == players.toSet && players2.toSet == players.toSet && players.size <= paging.size.value
      )

  test("allFederationsSummary & federationSummaryById"): db =>
    forall: (paging: Pagination) =>
      for
        federations  <- db.allFederationsSummary(paging)
        federations1 <- federations.map(_.id).traverse(db.federationSummaryById).map(_.flatten)
      yield expect(
        federations1 == federations && federations.size <= paging.size.value
      )

  test("allFederations"): db =>
    db.allFederations
      .map(_.map(_.id).toSet)
      .map(x => expect(Federation.all.keySet.diff(x) == Set.empty))
