package fide

import cats.effect.*
import cats.syntax.all.*
import fide.db.{ Db, DbResource, PostgresConfig }
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger

class AppResources private (val db: Db, val client: Client[IO])

object AppResources:

  def instance(conf: PostgresConfig)(using Logger[IO]): Resource[IO, AppResources] =
    (makeDb(conf), makeClient).mapN(AppResources.apply)

  def makeDb(conf: PostgresConfig)(using Logger[IO]): Resource[IO, Db] =
    DbResource.instance(conf).map(res => Db(res.postgres))

  def makeClient(using Logger[IO]): Resource[IO, Client[IO]] =
    EmberClientBuilder.default[IO].build
