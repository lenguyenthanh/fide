package fide
package db
package test

import fide.domain.*
import weaver.*

object RepositorySuite extends SimpleIOSuite:

  private def resource = Containers.createDb

  val newPlayer = NewPlayer(
    1,
    "John",
    None,
    None,
    None,
    None,
    None,
    None
  )

  val newFederation = NewFederation(
    "FIDE",
    "fide"
  )

  test("create player success"):
    resource
      .use(_.upsert(newPlayer, newFederation).map(_ => expect(true)))
