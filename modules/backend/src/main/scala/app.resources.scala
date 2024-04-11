package fide

import cats.effect.*
import fide.crawler.{ Crawler, CrawlerConfig }
import fide.db.{ Db, DbResource, PostgresConfig }
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

class AppResources private (val db: Db, val crawler: Crawler)

object AppResources:

  def instance(conf: AppConfig)(using Logger[IO]): Resource[IO, AppResources] =
    makeDb(conf.postgres).flatMap: db =>
      makeCralwer(db, conf.crawler).map(crawler => AppResources(db, crawler))

  def makeDb(conf: PostgresConfig)(using Logger[IO]): Resource[IO, Db] =
    DbResource.instance(conf).map(res => Db(res.postgres))

  def makeCralwer(db: Db, conf: CrawlerConfig)(using Logger[IO]): Resource[IO, Crawler] =
    EmberClientBuilder.default[IO].build.map(Crawler(db, _, conf))
