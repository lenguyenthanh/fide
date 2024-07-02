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
      startCrawler.background.void

    def startCrawler =
      info"Start crawler job in ${config.delayInSeconds} seconds" *>
        IO.sleep(config.delayInSeconds.seconds) *>
        crawlWithSleep.foreverM

    def crawlWithSleep =
      crawler.crawl *> IO.sleep(config.intervalInMinutes.minutes)
