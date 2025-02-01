package fide
package crawler
package test

import cats.effect.IO
import fide.domain.Federation
import fide.types.FederationId
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger
import weaver.*

object DownloaderTest extends SimpleIOSuite:

  given Logger[IO] = org.typelevel.log4cats.noop.NoOpLogger[IO]

  test("No new federations after downloads".ignore):
    EmberClientBuilder
      .default[IO]
      .build
      .use: client =>
        Downloader(client).fetch
          .map(x => x._2.map(_.id))
          .unNone
          .compile
          .to(Set)
          .map(Federation.all.keySet.diff)
          .map(expect.same(_, Set.empty))
