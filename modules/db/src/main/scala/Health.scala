package fide
package db

import cats.effect.*
import fide.domain.Models.PostgresStatus
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.*

trait Health:
  def status: IO[PostgresStatus]

object Health:
  import skunk.*
  import skunk.implicits.*
  import skunk.codec.all.*

  def apply(postgres: Resource[IO, Session[IO]])(using Logger[IO]): Health = new:
    def status: IO[PostgresStatus] =
      val q = sql"SELECT pid FROM pg_stat_activity".query(int4)

      postgres
        .use(_.option(q))
        .timeout(1.second)
        .map(_.fold(PostgresStatus.Ok)(_ => PostgresStatus.Unreachable))
        .handleErrorWith: e =>
          error"Error in health check: $e" *> IO.pure(PostgresStatus.Unreachable)
