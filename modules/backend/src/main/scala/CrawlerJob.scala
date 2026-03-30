package fide

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.crawler.Crawler
import fide.db.Ingestor
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.*

trait CrawlerJob:
  def run(): Resource[IO, Unit]

object CrawlerJob:
  def apply(crawler: Crawler, ingestor: Ingestor, config: CrawlerJobConfig)(using Logger[IO]): CrawlerJob =
    new:
      def run(): Resource[IO, Unit] =
        startCrawler.background.void

      def startCrawler =
        info"Start crawler job in ${config.delayInSeconds} seconds" *>
          IO.sleep(config.delayInSeconds.seconds) *>
          crawlWithSleep.foreverM

      def crawlWithSleep =
        crawler.crawl.flatMap: crawled =>
          IO.whenA(crawled)(ingestor.ingest.handleErrorWith(e => error"Error during ingestion: $e"))
        *> IO.sleep(config.intervalInMinutes.minutes)
