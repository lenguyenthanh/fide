package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import io.github.iltotore.iron.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import java.time.OffsetDateTime

object IngestorSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resource: Resource[IO, (PlayerEventDb, HistoryDb, Ingestor, Db)] =
    Containers.createResource.evalMap: x =>
      val eventDb   = PlayerEventDb(x.postgres)
      val historyDb = HistoryDb(x.postgres, 100)
      val db        = Db(x.postgres)
      for
        playerHashCache <- HashCache(db.allPlayerHashesByFideId)
        ingestor = Ingestor(eventDb, db, playerHashCache)
      yield (eventDb, historyDb, ingestor, db)

  val now     = OffsetDateTime.now()
  val fedId   = FederationId("USA")
  val lastMod = "Mon, 01 Jan 2024 00:00:00 GMT"
  val jan2024 = YearMonth(2024, 1)

  def mkEvent(fideId: String, name: String, lastModified: Option[String] = lastMod.some) =
    NewPlayerEvent(
      fideId = FideId.applyUnsafe(fideId),
      name = name,
      title = Title.GM.some,
      womenTitle = none,
      otherTitles = Nil,
      standard = Rating(2700).some,
      standardK = none,
      rapid = Rating(2600).some,
      rapidK = none,
      blitz = Rating(2500).some,
      blitzK = none,
      gender = Gender.Male.some,
      birthYear = 1990.some,
      active = true,
      federationId = fedId.some,
      hash = fideId.hashCode.toLong,
      crawledAt = now,
      sourceLastModified = lastModified
    )

  test("ingest populates player_info and player_history"):
    resource.use: (eventDb, historyDb, ingestor, db) =>
      val fed = NewFederation(fedId, "United States")
      for
        _       <- db.upsertFederations(List(fed))
        _       <- eventDb.append(List(mkEvent("100001", "Alice"), mkEvent("100002", "Bob")))
        _       <- ingestor.ingest
        players <- historyDb.allPlayers(
          jan2024,
          Sorting(SortBy.Name, Order.Asc),
          Pagination(PageNumber(1), PageSize(30)),
          PlayerFilter.default
        )
        uningested <- eventDb.ungestedStream(10_000).compile.toList
      yield expect(players.size == 2) and
        expect(players.head.name == "Alice") and
        expect(players.head.standard.contains(Rating(2700))) and
        expect(uningested.isEmpty)

  test("ingest is idempotent"):
    resource.use: (eventDb, historyDb, ingestor, db) =>
      val fed = NewFederation(fedId, "United States")
      for
        _       <- db.upsertFederations(List(fed))
        _       <- eventDb.append(List(mkEvent("100001", "Alice")))
        _       <- ingestor.ingest
        _       <- ingestor.ingest // second call should be no-op (no uningested events)
        players <- historyDb.allPlayers(
          jan2024,
          Sorting(SortBy.Name, Order.Asc),
          Pagination(PageNumber(1), PageSize(30)),
          PlayerFilter.default
        )
      yield expect(players.size == 1)

  test("ingest skips batch when no sourceLastModified"):
    resource.use: (eventDb, historyDb, ingestor, db) =>
      val fed = NewFederation(fedId, "United States")
      for
        _      <- db.upsertFederations(List(fed))
        _      <- eventDb.append(List(mkEvent("100001", "Alice", lastModified = none)))
        _      <- ingestor.ingest
        months <- historyDb.availableMonths
      yield expect(months.isEmpty)

  test("ingest updates player_history when rating changes"):
    resource.use: (eventDb, historyDb, ingestor, db) =>
      val fed = NewFederation(fedId, "United States")
      for
        _ <- db.upsertFederations(List(fed))
        _ <- eventDb.append(List(mkEvent("100001", "Alice")))
        _ <- ingestor.ingest
        event2 = mkEvent("100001", "Alice").copy(standard = Rating(2800).some, hash = 999L)
        _      <- eventDb.append(List(event2))
        _      <- ingestor.ingest
        player <- historyDb.playerById(PlayerId(1), jan2024)
      yield expect(player.isDefined) and
        expect(player.get.name == "Alice") and
        expect(player.get.standard.contains(Rating(2800)))
