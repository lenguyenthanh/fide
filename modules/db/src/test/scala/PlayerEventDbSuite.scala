package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.domain.*
import fide.types.*
import io.github.iltotore.iron.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import java.time.OffsetDateTime

object PlayerEventDbSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resource: Resource[IO, PlayerEventDb] =
    Containers.createResource.map(x => PlayerEventDb(x.postgres))

  private def collectUningested(eventDb: PlayerEventDb): IO[List[PlayerEvent]] =
    eventDb.ungestedStream(10_000).compile.toList

  val now = OffsetDateTime.now()

  def mkEvent(
      playerId: Int,
      name: String,
      lastModified: Option[String] = "Mon, 01 Jan 2024 00:00:00 GMT".some
  ) =
    NewPlayerEvent(
      playerId = PlayerId.applyUnsafe(playerId),
      name = name,
      title = Title.GM.some,
      standard = Rating(2700).some,
      active = true,
      federationId = FederationId("USA").some,
      hash = playerId.toLong,
      crawledAt = now,
      sourceLastModified = lastModified
    )

  test("append and retrieve uningested events"):
    resource.use: eventDb =>
      val events = List(mkEvent(1, "Alice"), mkEvent(2, "Bob"))
      for
        _         <- eventDb.append(events)
        retrieved <- collectUningested(eventDb)
      yield expect(retrieved.size == 2) and
        expect(retrieved.head.name == "Alice") and
        expect(retrieved.head.hash == 1L) and
        expect(retrieved.last.name == "Bob")

  test("markIngested filters from uningested"):
    resource.use: eventDb =>
      val events = List(mkEvent(1, "Alice"), mkEvent(2, "Bob"))
      for
        _      <- eventDb.append(events)
        before <- collectUningested(eventDb)
        _      <- eventDb.markIngested(before.map(_.id))
        after  <- collectUningested(eventDb)
      yield expect(before.size == 2) and expect(after.isEmpty)

  test("append empty list is no-op"):
    resource.use: eventDb =>
      eventDb.append(Nil).map(_ => success)
