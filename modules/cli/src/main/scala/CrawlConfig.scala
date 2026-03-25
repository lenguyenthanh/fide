package fide.cli

import fide.types.YearMonth

import java.nio.file.Path
import scala.concurrent.duration.*

case class CrawlConfig(
    outputDir: Path,
    startMonth: YearMonth,
    endMonth: YearMonth,
    delayBetweenDownloads: FiniteDuration
)

object CrawlConfig:

  val defaultStart = YearMonth(2016, 1)
  val defaultEnd   = YearMonth(java.time.LocalDate.now)
  val defaultDelay = 2.seconds

  def parse(args: List[String]): Either[String, CrawlConfig] =
    args match
      case Nil               => Left("Missing required argument: <outputDir>")
      case outputDir :: rest =>
        parseFlags(rest).map: (start, end, delay) =>
          CrawlConfig(
            outputDir = Path.of(outputDir),
            startMonth = start.getOrElse(defaultStart),
            endMonth = end.getOrElse(defaultEnd),
            delayBetweenDownloads = delay.getOrElse(defaultDelay)
          )

  private def parseFlags(
      args: List[String]
  ): Either[String, (Option[YearMonth], Option[YearMonth], Option[FiniteDuration])] =
    def loop(
        remaining: List[String],
        start: Option[YearMonth],
        end: Option[YearMonth],
        delay: Option[FiniteDuration]
    ): Either[String, (Option[YearMonth], Option[YearMonth], Option[FiniteDuration])] =
      remaining match
        case Nil                        => Right((start, end, delay))
        case "--start" :: value :: tail =>
          YearMonth.fromString(value).flatMap(ym => loop(tail, Some(ym), end, delay))
        case "--end" :: value :: tail =>
          YearMonth.fromString(value).flatMap(ym => loop(tail, start, Some(ym), delay))
        case "--delay" :: value :: tail =>
          value.toIntOption match
            case Some(secs) => loop(tail, start, end, Some(secs.seconds))
            case None       => Left(s"Invalid delay value: $value")
        case unknown :: _ => Left(s"Unknown flag: $unknown")
    loop(args, None, None, None)
