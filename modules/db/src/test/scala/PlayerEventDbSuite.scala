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
import scala.concurrent.duration.*

object PlayerEventDbSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resource: Resource[IO, PlayerEventDb] =
    Containers.createResource.map(x => PlayerEventDb(x.postgres))

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
      rawData = s"raw-line-for-$name",
      crawledAt = now,
      sourceLastModified = lastModified
    )

  test("append and retrieve uningested events"):
    resource.use: eventDb =>
      val events = List(mkEvent(1, "Alice"), mkEvent(2, "Bob"))
      for
        _         <- eventDb.append(events)
        retrieved <- eventDb.uningested()
      yield expect(retrieved.size == 2) and
        expect(retrieved.head.name == "Alice") and
        expect(retrieved.head.rawData == "raw-line-for-Alice") and
        expect(retrieved.last.name == "Bob")

  test("markIngested filters from uningested"):
    resource.use: eventDb =>
      val events = List(mkEvent(1, "Alice"), mkEvent(2, "Bob"))
      for
        _      <- eventDb.append(events)
        before <- eventDb.uningested()
        _      <- eventDb.markIngested(before.map(_.id))
        after  <- eventDb.uningested()
      yield expect(before.size == 2) and expect(after.isEmpty)

  test("purgeOlderThan removes old events"):
    resource.use: eventDb =>
      val events = List(mkEvent(1, "Alice"))
      for
        _     <- eventDb.append(events)
        _     <- eventDb.purgeOlderThan(0.seconds)
        after <- eventDb.uningested()
      yield expect(after.isEmpty)

  test("uningested respects limit"):
    resource.use: eventDb =>
      val events = List(mkEvent(1, "Alice"), mkEvent(2, "Bob"), mkEvent(3, "Charlie"))
      for
        _       <- eventDb.append(events)
        limited <- eventDb.uningested(2)
      yield expect(limited.size == 2)

  test("append empty list is no-op"):
    resource.use: eventDb =>
      eventDb.append(Nil).map(_ => success)
