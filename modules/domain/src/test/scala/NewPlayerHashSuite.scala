package fide
package domain
package test

import cats.effect.IO
import fide.types.*
import io.github.iltotore.iron.*
import weaver.*

object NewPlayerHashSuite extends SimpleIOSuite:

  val base = NewPlayer(
    id = PlayerId(1),
    name = "Alice",
    title = Some(Title.GM),
    womenTitle = None,
    standard = Some(Rating(2700)),
    rapid = Some(Rating(2600)),
    blitz = Some(Rating(2500)),
    gender = Some(Gender.Female),
    birthYear = Some(1990),
    active = true,
    federationId = Some(FederationId("USA"))
  )

  test("computeHash: same player produces same hash"):
    IO:
      val h1 = NewPlayer.computeHash(base)
      val h2 = NewPlayer.computeHash(base)
      expect(h1 == h2)

  test("computeHash: changing name changes hash"):
    IO:
      val h1 = NewPlayer.computeHash(base)
      val h2 = NewPlayer.computeHash(base.copy(name = "Bob"))
      expect(h1 != h2)

  test("computeHash: changing title changes hash"):
    IO:
      val h1 = NewPlayer.computeHash(base)
      val h2 = NewPlayer.computeHash(base.copy(title = Some(Title.IM)))
      expect(h1 != h2)

  test("computeHash: changing rating changes hash"):
    IO:
      val h1 = NewPlayer.computeHash(base)
      val h2 = NewPlayer.computeHash(base.copy(standard = Some(Rating(2500))))
      expect(h1 != h2)

  test("computeHash: changing active changes hash"):
    IO:
      val h1 = NewPlayer.computeHash(base)
      val h2 = NewPlayer.computeHash(base.copy(active = false))
      expect(h1 != h2)

  test("computeHash: changing federationId changes hash"):
    IO:
      val h1 = NewPlayer.computeHash(base)
      val h2 = NewPlayer.computeHash(base.copy(federationId = Some(FederationId("RUS"))))
      expect(h1 != h2)

  test("computeHash: changing id does NOT change hash"):
    IO:
      val h1 = NewPlayer.computeHash(base)
      val h2 = NewPlayer.computeHash(base.copy(id = PlayerId(2)))
      expect(h1 == h2)

  test("computeInfoHash: same player produces same hash"):
    IO:
      val h1 = NewPlayer.computeInfoHash(base)
      val h2 = NewPlayer.computeInfoHash(base)
      expect(h1 == h2)

  test("computeInfoHash: changing name changes hash"):
    IO:
      val h1 = NewPlayer.computeInfoHash(base)
      val h2 = NewPlayer.computeInfoHash(base.copy(name = "Bob"))
      expect(h1 != h2)

  test("computeInfoHash: changing gender changes hash"):
    IO:
      val h1 = NewPlayer.computeInfoHash(base)
      val h2 = NewPlayer.computeInfoHash(base.copy(gender = Some(Gender.Male)))
      expect(h1 != h2)

  test("computeInfoHash: changing birthYear changes hash"):
    IO:
      val h1 = NewPlayer.computeInfoHash(base)
      val h2 = NewPlayer.computeInfoHash(base.copy(birthYear = Some(2000)))
      expect(h1 != h2)

  test("computeInfoHash: changing rating does NOT change hash"):
    IO:
      val h1 = NewPlayer.computeInfoHash(base)
      val h2 = NewPlayer.computeInfoHash(base.copy(standard = Some(Rating(2500))))
      expect(h1 == h2)

  test("computeInfoHash: changing title does NOT change hash"):
    IO:
      val h1 = NewPlayer.computeInfoHash(base)
      val h2 = NewPlayer.computeInfoHash(base.copy(title = Some(Title.IM)))
      expect(h1 == h2)

  test("computeInfoHash: changing federationId does NOT change hash"):
    IO:
      val h1 = NewPlayer.computeInfoHash(base)
      val h2 = NewPlayer.computeInfoHash(base.copy(federationId = None))
      expect(h1 == h2)
