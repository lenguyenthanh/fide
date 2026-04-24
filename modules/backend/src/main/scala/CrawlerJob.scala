package fide

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.broadcast.MonthlyResetHook
import fide.crawler.Crawler
import fide.crawler.Crawler.CrawlStatus
import fide.db.Ingestor
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.*

trait CrawlerJob:
  def run(): Resource[IO, Unit]

object CrawlerJob:
  def apply(
      crawler: Crawler,
      ingestor: Ingestor,
      config: CrawlerJobConfig,
      monthlyResetHook: MonthlyResetHook
  )(using Logger[IO]): CrawlerJob =
    new:
      def run(): Resource[IO, Unit] =
        startCrawler.background.void

      def startCrawler =
        info"Start crawler job in ${config.delayInSeconds} seconds" *>
          IO.sleep(config.delayInSeconds.seconds) *>
          crawlWithSleep.foreverM

      def crawlWithSleep =
        crawler.crawl.flatMap:
          case CrawlStatus.Done    =>
            ingestor.ingest
              .flatMap: _ =>
                monthlyResetHook.run.handleErrorWith: e =>
                  error"Error during monthly reset: $e"
              .handleErrorWith(e => error"Error during ingestion: $e")
          case CrawlStatus.Skipped => IO.unit
        *> IO.sleep(config.intervalInMinutes.minutes)
