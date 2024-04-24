package fide

import cats.effect.*
import cats.syntax.all.*
import fide.crawler.Crawler
import fide.db.{ Db, DbResource, Health, KVStore }
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

class AppResources private (val db: Db, val store: KVStore, val health: Health, val crawler: Crawler)

object AppResources:

  def instance(conf: AppConfig)(using Logger[IO]): Resource[IO, AppResources] =
    (DbResource.instance(conf.postgres), makeClient).mapN: (res, client) =>
      val db     = Db(res.postgres)
      val store  = KVStore(res.postgres)
      val health = Health(res.postgres)
      AppResources(
        db,
        store,
        health,
        Crawler.instance(db, store, client, conf.crawler)
      )

  def makeClient: Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
