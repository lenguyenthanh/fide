package fide
package db

import cats.effect.*
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import skunk.*

trait KVStore:
  def put(key: String, value: String): IO[Unit]
  def get(key: String): IO[Option[String]]

object KVStore:
  def apply(postgres: Resource[IO, Session[IO]])(using Logger[IO]): KVStore = new:
    def put(key: String, value: String): IO[Unit] =
      postgres.use(_.execute(StoreSql.upsert)((key, value)).void)

    def get(key: String): IO[Option[String]] =
      postgres.use(_.option(StoreSql.get)(key))

private object StoreSql:

  import skunk.codec.all.*
  import skunk.implicits.*

  val upsert: Command[(String, String)] =
    sql"""
        INSERT INTO cache (key, value)
        VALUES ($text, $text)
        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
       """.command

  val get: Query[String, String] =
    sql"""
        SELECT value
        FROM cache
        WHERE key = $text
       """.query(text)
