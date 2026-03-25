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
      val historyDb = HistoryDb(x.postgres)
      val db        = Db(x.postgres)
      for
        playerHashCache     <- HashCache(db.allPlayerHashes)
        playerInfoHashCache <- HashCache(historyDb.allPlayerInfoHashes)
        ingestor = Ingestor(eventDb, historyDb, db, playerHashCache, playerInfoHashCache)
      yield (eventDb, historyDb, ingestor, db)

  val now     = OffsetDateTime.now()
  val fedId   = FederationId("USA")
  val lastMod = "Mon, 01 Jan 2024 00:00:00 GMT"
  val jan2024 = YearMonth(2024, 1)

  def mkEvent(playerId: Int, name: String, lastModified: Option[String] = lastMod.some) =
    NewPlayerEvent(
      playerId = PlayerId.applyUnsafe(playerId),
      name = name,
      title = Title.GM.some,
      standard = Rating(2700).some,
      rapid = Rating(2600).some,
      blitz = Rating(2500).some,
      gender = Gender.Male.some,
      birthYear = 1990.some,
      active = true,
      federationId = fedId.some,
      hash = playerId.toLong,
      crawledAt = now,
      sourceLastModified = lastModified
    )

  test("ingest populates player_info and player_history"):
    resource.use: (eventDb, historyDb, ingestor, db) =>
      val fed = NewFederation(fedId, "United States")
      for
        // Need federation in DB for joins
        _ <- db.upsert(NewPlayer(PlayerId(1), "Alice", active = true, federationId = fedId.some), fed.some)
        _ <- db.upsert(NewPlayer(PlayerId(2), "Bob", active = true, federationId = fedId.some), fed.some)
        // Append events
        _ <- eventDb.append(List(mkEvent(1, "Alice"), mkEvent(2, "Bob")))
        // Run ingestion
        _ <- ingestor.ingest
        // Verify player_info populated
        players <- historyDb.allPlayers(
          jan2024,
          Sorting(SortBy.Name, Order.Asc),
          Pagination(PageNumber(1), PageSize(30)),
          PlayerFilter.default
        )
        // Verify events marked as ingested
        uningested <- eventDb.ungestedStream(10_000).compile.toList
      yield expect(players.size == 2) and
        expect(players.head.name == "Alice") and
        expect(players.head.standard.contains(Rating(2700))) and
        expect(uningested.isEmpty)

  test("ingest is idempotent"):
    resource.use: (eventDb, historyDb, ingestor, db) =>
      val fed = NewFederation(fedId, "United States")
      for
        _ <- db.upsert(NewPlayer(PlayerId(1), "Alice", active = true, federationId = fedId.some), fed.some)
        _ <- eventDb.append(List(mkEvent(1, "Alice")))
        _ <- ingestor.ingest
        _ <- ingestor.ingest // second call should be no-op (no uningested events)
        players <- historyDb.allPlayers(
          jan2024,
          Sorting(SortBy.Name, Order.Asc),
          Pagination(PageNumber(1), PageSize(30)),
          PlayerFilter.default
        )
      yield expect(players.size == 1)

  test("ingest skips history for events without sourceLastModified"):
    resource.use: (eventDb, historyDb, ingestor, db) =>
      val fed = NewFederation(fedId, "United States")
      for
        _ <- db.upsert(NewPlayer(PlayerId(1), "Alice", active = true, federationId = fedId.some), fed.some)
        _ <- eventDb.append(List(mkEvent(1, "Alice", lastModified = none)))
        _ <- ingestor.ingest
        months <- historyDb.availableMonths
      yield expect(months.isEmpty)
