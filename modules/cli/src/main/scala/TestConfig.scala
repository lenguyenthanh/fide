package fide.cli

import cats.data.Validated
import cats.syntax.all.*
import com.monovore.decline.*
import fide.types.{ PositiveInt, YearMonth }
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import org.http4s.Uri
import org.http4s.implicits.*

import java.nio.file.Path

case class TestConfig(
    csvDir: Path,
    startMonth: Option[YearMonth],
    endMonth: Option[YearMonth],
    baseUrl: Uri,
    sampleSize: PositiveInt,
    concurrency: PositiveInt
)

object TestConfig:

  given Argument[YearMonth] =
    Argument.from("yyyy-MM"): s =>
      YearMonth.fromString(s).fold(Validated.invalidNel(_), Validated.valid)

  given Argument[Uri] =
    Argument.from("uri"): s =>
      Uri.fromString(s).fold(e => Validated.invalidNel(e.message), Validated.valid)

  private val csvDirOpt =
    Opts.argument[String]("csvDir").map(Path.of(_))

  private val startOpt =
    Opts.option[YearMonth]("start", "Only test files from this month onwards", "s").orNone

  private val endOpt =
    Opts.option[YearMonth]("end", "Only test files up to this month", "e").orNone

  private val urlOpt =
    Opts.option[Uri]("url", "Base URL of the running backend", "u").withDefault(uri"http://localhost:9669")

  private val sampleSizeOpt =
    Opts
      .option[Int]("sample-size", "Number of players to sample per month")
      .withDefault(50)
      .map(_.refineUnsafe[Positive])

  private val concurrencyOpt =
    Opts
      .option[Int]("concurrency", "Number of concurrent HTTP requests")
      .withDefault(10)
      .map(_.refineUnsafe[Positive])

  val opts: Opts[TestConfig] =
    (csvDirOpt, startOpt, endOpt, urlOpt, sampleSizeOpt, concurrencyOpt).mapN(TestConfig.apply)
