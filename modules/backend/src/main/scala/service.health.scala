package fide

import cats.effect.*
import fide.db.Health
import fide.spec.*
import io.github.arainko.ducktape.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

class HealthServiceImpl(health: Health)(using Logger[IO]) extends HealthService[IO]:

  override def healthCheck(): IO[HealthStatusOutput] =
    health.status
      .map(_.to[PostgresStatus])
      .map(HealthStatusOutput(_))
      .handleErrorWith: e =>
        error"Error in health check: $e" *>
          IO.raiseError(InternalServerError("Internal server error"))
