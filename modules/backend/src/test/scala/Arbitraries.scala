package fide
package test

import fide.domain.Models.*
import fide.domain.{ Federation, Title }
import fide.types.*
import org.scalacheck.{ Arbitrary, Gen }

object Arbitraries:

  given Arbitrary[Sorting] = Arbitrary:
    for
      sortBy  <- Gen.oneOf(SortBy.values.toSeq)
      orderBy <- Gen.oneOf(Order.values.toSeq)
    yield Sorting(sortBy, orderBy)

  given Arbitrary[Title] = Arbitrary:
    Gen.oneOf(Title.values.toSeq)

  given Arbitrary[RatingRange] = Arbitrary:
    for
      min <- Gen.choose(-6000, 6000).map(Rating.option(_))
      max <- Gen.choose(-6000, 6000).map(Rating.option(_))
    yield RatingRange(min, max)

  given Arbitrary[FederationId] = Arbitrary:
    Gen.oneOf(Federation.all.keys.toSeq)

  given Arbitrary[PlayerFilter] = Arbitrary:
    for
      name         <- Gen.option(Gen.alphaNumStr) // real name
      isActive     <- Gen.option(Gen.oneOf(true, false))
      standard     <- Arbitrary.arbitrary[RatingRange]
      rapid        <- Arbitrary.arbitrary[RatingRange]
      blitz        <- Arbitrary.arbitrary[RatingRange]
      federationId <- Gen.option(Arbitrary.arbitrary[FederationId])
      titles       <- Gen.option(Gen.nonEmptyListOf(Arbitrary.arbitrary[Title]))
    yield PlayerFilter(
      name,
      isActive,
      standard,
      rapid,
      blitz,
      federationId,
      titles
    )

  given Arbitrary[Pagination] = Arbitrary:
    for
      page <- Gen.choose(1, 100).map(PageNumber.applyUnsafe(_))
      size <- Gen.choose(1, 100).map(PageSize.applyUnsafe(_))
    yield Pagination(page, size)

  given Arbitrary[PlayerId] = Arbitrary:
    Gen.posNum[Int].map(PlayerId.applyUnsafe(_))
