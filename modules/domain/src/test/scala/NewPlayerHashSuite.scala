package fide
package domain
package test

import cats.effect.IO
import fide.types.*
import io.github.iltotore.iron.*
import weaver.*

object CrawlPlayerHashSuite extends SimpleIOSuite:

  val base = CrawlPlayer(
    fideId = FideId("100001"),
    name = "Alice",
    title = Some(Title.GM),
    womenTitle = None,
    otherTitles = Nil,
    standard = Some(Rating(2700)),
    standardK = None,
    rapid = Some(Rating(2600)),
    rapidK = None,
    blitz = Some(Rating(2500)),
    blitzK = None,
    gender = Some(Gender.Female),
    birthYear = Some(1990),
    active = true,
    federationId = Some(FederationId("USA"))
  )

  test("computeHash: same player produces same hash"):
    IO:
      val h1 = CrawlPlayer.computeHash(base)
      val h2 = CrawlPlayer.computeHash(base)
      expect(h1 == h2)

  test("computeHash: changing name changes hash"):
    IO:
      val h1 = CrawlPlayer.computeHash(base)
      val h2 = CrawlPlayer.computeHash(base.copy(name = "Bob"))
      expect(h1 != h2)

  test("computeHash: changing title changes hash"):
    IO:
      val h1 = CrawlPlayer.computeHash(base)
      val h2 = CrawlPlayer.computeHash(base.copy(title = Some(Title.IM)))
      expect(h1 != h2)

  test("computeHash: changing rating changes hash"):
    IO:
      val h1 = CrawlPlayer.computeHash(base)
      val h2 = CrawlPlayer.computeHash(base.copy(standard = Some(Rating(2500))))
      expect(h1 != h2)

  test("computeHash: changing active changes hash"):
    IO:
      val h1 = CrawlPlayer.computeHash(base)
      val h2 = CrawlPlayer.computeHash(base.copy(active = false))
      expect(h1 != h2)

  test("computeHash: changing federationId changes hash"):
    IO:
      val h1 = CrawlPlayer.computeHash(base)
      val h2 = CrawlPlayer.computeHash(base.copy(federationId = Some(FederationId("RUS"))))
      expect(h1 != h2)

  test("computeInfoHash: same player produces same hash"):
    IO:
      val h1 = CrawlPlayer.computeInfoHash(base)
      val h2 = CrawlPlayer.computeInfoHash(base)
      expect(h1 == h2)

  test("computeInfoHash: changing name changes hash"):
    IO:
      val h1 = CrawlPlayer.computeInfoHash(base)
      val h2 = CrawlPlayer.computeInfoHash(base.copy(name = "Bob"))
      expect(h1 != h2)

  test("computeInfoHash: changing gender changes hash"):
    IO:
      val h1 = CrawlPlayer.computeInfoHash(base)
      val h2 = CrawlPlayer.computeInfoHash(base.copy(gender = Some(Gender.Male)))
      expect(h1 != h2)

  test("computeInfoHash: changing birthYear changes hash"):
    IO:
      val h1 = CrawlPlayer.computeInfoHash(base)
      val h2 = CrawlPlayer.computeInfoHash(base.copy(birthYear = Some(2000)))
      expect(h1 != h2)

  test("computeInfoHash: changing rating does NOT change hash"):
    IO:
      val h1 = CrawlPlayer.computeInfoHash(base)
      val h2 = CrawlPlayer.computeInfoHash(base.copy(standard = Some(Rating(2500))))
      expect(h1 == h2)

  test("computeInfoHash: changing title does NOT change hash"):
    IO:
      val h1 = CrawlPlayer.computeInfoHash(base)
      val h2 = CrawlPlayer.computeInfoHash(base.copy(title = Some(Title.IM)))
      expect(h1 == h2)

  test("computeInfoHash: changing federationId does NOT change hash"):
    IO:
      val h1 = CrawlPlayer.computeInfoHash(base)
      val h2 = CrawlPlayer.computeInfoHash(base.copy(federationId = None))
      expect(h1 == h2)
