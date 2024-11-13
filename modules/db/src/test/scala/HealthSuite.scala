package fide
package db
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import fide.domain.Models.PostgresStatus
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

object HealthSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private def resource: Resource[IO, Health] = Containers.createResource.map(x => Health(x.postgres))

  test("status success"):
    resource
      .use(_.status.map(x => assert(x == PostgresStatus.Ok)))
