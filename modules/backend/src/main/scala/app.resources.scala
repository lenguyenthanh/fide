package fide

import cats.effect.*
import cats.syntax.all.*
import fide.crawler.Crawler
import fide.db.{ Db, DbResource, Store }
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

class AppResources private (val db: Db, val store: Store, val crawler: Crawler)

object AppResources:

  def instance(conf: AppConfig)(using Logger[IO]): Resource[IO, AppResources] =
    (DbResource.instance(conf.postgres), makeClient).mapN: (res, client) =>
      val db    = Db(res.postgres)
      val store = Store(res.postgres)
      AppResources(
        db,
        store,
        Crawler.instance(db, store, client, conf.crawler)
      )

  def makeClient: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
