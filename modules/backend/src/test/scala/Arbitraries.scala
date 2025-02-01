package fide
package test

import cats.syntax.all.*
import faker.ResourceLoader.Implicits.*
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

  val genRandomPagination: Gen[Pagination] =
    for
      page <- Gen.choose(1, 20).map(PageNumber.applyUnsafe(_))
      size <- Gen.choose(1, 40).map(PageSize.applyUnsafe(_))
    yield Pagination(page, size)

  val genSmallPagination: Gen[Pagination] =
    for
      page <- Gen.choose(1, 2).map(PageNumber.applyUnsafe(_))
      size <- Gen.choose(3, 20).map(PageSize.applyUnsafe(_))
    yield Pagination(page, size)

  given Arbitrary[Pagination] = Arbitrary:
    Gen.frequency(
      1  -> genRandomPagination,
      10 -> genSmallPagination
    )

  given Arbitrary[PlayerId] = Arbitrary:
    Gen.posNum[Int].map(PlayerId.applyUnsafe(_))

  given Arbitrary[Title] = Arbitrary:
    Gen.oneOf(Title.values.toSeq)

  def genMinRating: Gen[Option[Rating]] =
    Gen.frequency(
      500 -> Gen.const(none),
      10  -> Gen.choose(1400, 2000).map(Rating.option(_)),
      1   -> Gen.choose(2000, 4000).map(Rating.option(_))
    )

  def genMaxRating: Gen[Option[Rating]] =
    Gen.frequency(
      500 -> Gen.const(none),
      10  -> Gen.choose(2700, 4000).map(Rating.option(_)),
      1   -> Gen.choose(1400, 2700).map(Rating.option(_))
    )

  given Arbitrary[RatingRange] = Arbitrary:
    for
      min <- genMinRating
      max <- genMaxRating
    yield RatingRange(min, max)

  given Arbitrary[FederationId] = Arbitrary:
    Gen.oneOf(Federation.all.keys.toSeq)

  lazy val genFederationId: Gen[Option[FederationId]] =
    Gen.frequency(
      1  -> Arbitrary.arbitrary[FederationId].map(_.some),
      10 -> Gen.const(none)
    )

  lazy val genName: Gen[String] =
    Gen.oneOf(
      Arbitrary.arbitrary[faker.name.FirstName].map(_.value),
      Arbitrary.arbitrary[faker.name.LastName].map(_.value)
    )

  def makeTitles(titles: List[Title]): Option[List[Title]] =
    val setOfTitles = titles.toSet
    if setOfTitles.isEmpty then None
    else setOfTitles.toList.some

  val genTitles: Gen[Option[List[Title]]] =
    Gen.frequency(
      30 -> Gen.const(none),
      1  -> Gen.listOfN(3, Arbitrary.arbitrary[Title]).map(makeTitles)
    )

  given Arbitrary[(RatingRange, RatingRange, RatingRange)] = Arbitrary:
    for
      standard <- Arbitrary.arbitrary[RatingRange]
      rapid    <- Arbitrary.arbitrary[RatingRange]
      blitz    <- Arbitrary.arbitrary[RatingRange]
    yield (standard, rapid, blitz)

  val genIsActive: Gen[Option[Boolean]] =
    Gen.frequency(
      10 -> Gen.const(none),
      1  -> Gen.oneOf(true, false).map(_.some)
    )

  given Arbitrary[PlayerFilter] = Arbitrary:
    for
      name         <- Gen.option(genName) // real name
      isActive     <- genIsActive
      triple       <- Arbitrary.arbitrary[(RatingRange, RatingRange, RatingRange)]
      federationId <- genFederationId
      titles       <- genTitles
    yield PlayerFilter(
      name,
      isActive,
      triple._1,
      triple._2,
      triple._3,
      federationId,
      titles
    )
