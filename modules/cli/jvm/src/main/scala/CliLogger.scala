package fide.cli

import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object CliLogger:
  def create: IO[Logger[IO]] = Slf4jLogger.create[IO]
