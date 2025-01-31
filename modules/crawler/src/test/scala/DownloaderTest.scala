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

  test("No new federations after downloads"):
    EmberClientBuilder
      .default[IO]
      .build
      .use: client =>
        Downloader(client).fetch
          .map(x => x._2)
          .fold[Set[FederationId]](Set.empty)((acc, x) => acc ++ x.map(_.id))
          .compile
          .last
          .map(x => Federation.names.keySet.diff(x.get))
          .map(x => expect(x == Set.empty))
