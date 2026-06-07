package fide.cli

import cats.effect.IO
import org.typelevel.log4cats.Logger

// woof has no Scala Native 0.5 artifact, so the native CLI uses a minimal console
// logger that writes to stdout/stderr. (The JVM build keeps the slf4j/logback setup.)
object CliLogger:

  def create: IO[Logger[IO]] = IO.pure(ConsoleLogger)

  private object ConsoleLogger extends Logger[IO]:
    private def out(level: String, msg: => String): IO[Unit] =
      IO.delay(println(s"[$level] $msg"))
    private def err(level: String, t: Throwable, msg: => String): IO[Unit] =
      IO.delay(Console.err.println(s"[$level] $msg")) *> IO.delay(t.printStackTrace())

    def error(message: => String): IO[Unit] = err("ERROR", new RuntimeException(message), message)
    def warn(message: => String): IO[Unit]  = out("WARN", message)
    def info(message: => String): IO[Unit]  = out("INFO", message)
    def debug(message: => String): IO[Unit] = out("DEBUG", message)
    def trace(message: => String): IO[Unit] = out("TRACE", message)

    def error(t: Throwable)(message: => String): IO[Unit] = err("ERROR", t, message)
    def warn(t: Throwable)(message: => String): IO[Unit]  = err("WARN", t, message)
    def info(t: Throwable)(message: => String): IO[Unit]  = err("INFO", t, message)
    def debug(t: Throwable)(message: => String): IO[Unit] = err("DEBUG", t, message)
    def trace(t: Throwable)(message: => String): IO[Unit] = err("TRACE", t, message)
