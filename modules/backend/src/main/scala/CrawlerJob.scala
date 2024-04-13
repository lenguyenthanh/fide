package fide

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fide.crawler.Crawler
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

import scala.concurrent.duration.*

trait CrawlerJob:
  def run(): Resource[IO, Unit]

object CrawlerJob:
  def apply(crawler: Crawler, config: CrawlerJobConfig)(using Logger[IO]): CrawlerJob = new:
    def run(): Resource[IO, Unit] =
      info"Start crawler job in ${config.delayInSeconds} seconds".*>(crawlWithSleep.foreverM).background.void

    def crawlWithSleep =
      IO.sleep(config.delayInSeconds.seconds) *>
        crawler.crawl *>
        IO.sleep(config.intervalInMinutes.minutes)
