package fide.cli

import cats.effect.*
import fide.spec.HistoryService
import org.http4s.ember.client.EmberClientBuilder
import smithy4s.http4s.SimpleRestJsonBuilder

object TestResources:

  def make(config: TestConfig): Resource[IO, HistoryService[IO]] =
    EmberClientBuilder
      .default[IO]
      .build
      .flatMap: client =>
        SimpleRestJsonBuilder(HistoryService).client(client).uri(config.baseUrl).resource
