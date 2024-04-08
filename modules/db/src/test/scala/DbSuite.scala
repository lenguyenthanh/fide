package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import io.github.arainko.ducktape.*
import weaver.*

object RepositorySuite extends SimpleIOSuite:

  private def resource: Resource[IO, Db] = Containers.createDb

  val newPlayer = NewPlayer(
    1,
    "John",
    Option(Title.GM),
    Option(Title.GM),
    2700.some,
    2700.some,
    2700.some,
    1990.some,
    true
  )

  val newFederation = NewFederation(
    "fide",
    "FIDE"
  )

  test("create player success"):
    resource
      .use(_.upsert(newPlayer, newFederation.some).map(_ => expect(true)))

  test("create players success"):
    val player2 = newPlayer.copy(name = "Jane", id = 2)
    resource
      .use(
        _.upsert(List(newPlayer -> newFederation.some, player2 -> newFederation.some)).map(_ => expect(true))
      )

  test("create and query player success"):
    resource.use: db =>
      for
        _      <- db.upsert(newPlayer, newFederation.some)
        result <- db.playerById(1)
        found = result.get
      yield expect(
        found.to[NewPlayer] == newPlayer && found.federation.get.to[NewFederation] == newFederation
      )

  test("overwriting player success"):
    val player2     = newPlayer.copy(name = "Jane")
    val federation2 = NewFederation("Lichess", "lichess")
    resource.use: db =>
      for
        _      <- db.upsert(newPlayer, newFederation.some)
        _      <- db.upsert(player2, federation2.some)
        result <- db.playerById(1)
        found = result.get
      yield expect(
        found.to[NewPlayer] == player2 && found.federation.get.to[NewFederation] == federation2
      )

  val defaultSorting = Sorting(SortBy.Name, Order.Asc)
  val defaultPage    = Pagination(10, 0)
  test("search playersByName success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer, newFederation.some)
        players <- db.playersByName("Jo", defaultSorting, defaultPage)
      yield expect(
        players.length == 1 && players.head.to[NewPlayer] == newPlayer && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("allPlayers sortBy name success"):
    val player2 = newPlayer.copy(id = 2, name = "A")
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer, newFederation.some)
        _       <- db.upsert(player2, newFederation.some)
        players <- db.allPlayers(defaultSorting, defaultPage)
      yield expect(players.length == 2 && players.head.name == "A")

  test("search playersByFederationId success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer, newFederation.some)
        players <- db.playersByFederationId("fide")
      yield expect(
        players.length == 1 && players.head.to[NewPlayer] == newPlayer && players.head.federation.get
          .to[NewFederation] == newFederation
      )

  test("search playersByIds success"):
    resource.use: db =>
      for
        _       <- db.upsert(newPlayer, newFederation.some)
        players <- db.playersByIds(Set(1, 2))
      yield expect(
        players.length == 1 && players.head.to[NewPlayer] == newPlayer && players.head.federation.get
          .to[NewFederation] == newFederation
      )
