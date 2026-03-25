package fide
package db

import cats.effect.*
import cats.syntax.all.*
import fide.domain.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait PlayerEventDb:
  def append(events: List[NewPlayerEvent]): IO[Unit]
  def uningested(limit: Int = 1_000_000): IO[List[PlayerEvent]]
  def ungestedStream(batchSize: Int): fs2.Stream[IO, PlayerEvent]
  def markIngested(ids: List[Long]): IO[Unit]

object PlayerEventDb:

  // 32767 max params / 18 params per event row ≈ 1820; use 1500 for safety
  private val ChunkSize = 1500

  def apply(postgres: Resource[IO, Session[IO]]): PlayerEventDb = new:

    def append(events: List[NewPlayerEvent]): IO[Unit] =
      events.grouped(ChunkSize).toList.traverse_ { chunk =>
        val codec = DbCodecs.newPlayerEvent.values.list(chunk.size)
        val cmd   = sql"""
          INSERT INTO player_events (player_id, name, title, women_title, other_titles, standard, standard_kfactor,
            rapid, rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id, hash,
            crawled_at, source_last_modified)
          VALUES $codec""".command
        postgres.use(_.execute(cmd)(chunk)).void
      }

    def uningested(limit: Int): IO[List[PlayerEvent]] =
      val q = sql"""
        SELECT id, player_id, name, title, women_title, other_titles, standard, standard_kfactor,
          rapid, rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id, hash,
          crawled_at, source_last_modified, ingested, created_at
        FROM player_events
        WHERE ingested = FALSE
        ORDER BY id
        LIMIT $int4""".query(DbCodecs.playerEvent)
      postgres.use(_.execute(q)(limit))

    def ungestedStream(batchSize: Int): fs2.Stream[IO, PlayerEvent] =
      val q = sql"""
        SELECT id, player_id, name, title, women_title, other_titles, standard, standard_kfactor,
          rapid, rapid_kfactor, blitz, blitz_kfactor, sex, birth_year, active, federation_id, hash,
          crawled_at, source_last_modified, ingested, created_at
        FROM player_events
        WHERE ingested = FALSE
        ORDER BY id""".query(DbCodecs.playerEvent)
      fs2.Stream
        .resource(postgres)
        .flatMap: session =>
          session.stream(q)(Void, batchSize)

    def markIngested(ids: List[Long]): IO[Unit] =
      ids.grouped(ChunkSize).toList.traverse_ { chunk =>
        val idCodec = int8.values.list(chunk.size)
        val cmd     = sql"UPDATE player_events SET ingested = TRUE WHERE id IN ($idCodec)".command
        postgres.use(_.execute(cmd)(chunk)).void
      }

