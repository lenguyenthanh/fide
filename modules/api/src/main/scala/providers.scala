package fide.spec

import fide.types.{ NonEmptySet, PageNumber, PageSize }
import smithy4s.*

object providers:
  given RefinementProvider[PageFormat, String, PageNumber] =
    Refinement.drivenBy(PageNumber.fromString, _.toString)

  given RefinementProvider.Simple[smithy.api.Range, PageSize] =
    RefinementProvider.rangeConstraint(_.toInt)

  given RefinementProvider[PageSizeFormat, Int, PageSize] =
    Refinement.drivenBy(PageSize.either, _.toInt)

  given RefinementProvider[PlayerIdFormat, Int, fide.types.PlayerId] =
    Refinement.drivenBy(fide.types.PlayerId.either, _.value)

  given RefinementProvider[BirthYearFormat, Int, fide.types.BirthYear] =
    Refinement.drivenBy(fide.types.BirthYear.either, _.value)

  given RefinementProvider[RatingFormat, Int, fide.types.Rating] =
    Refinement.drivenBy(fide.types.Rating.either, _.value)

  given RefinementProvider[FederationIdFormat, String, fide.types.FederationId] =
    Refinement.drivenBy(fide.types.FederationId.either, _.value)

  given [A]: RefinementProvider[NonEmptySetFormat, Set[A], NonEmptySet[A]] =
    Refinement.drivenBy[NonEmptySetFormat](
      NonEmptySet.either,
      (b: NonEmptySet[A]) => b.value
    )

  given [A]: RefinementProvider.Simple[smithy.api.Length, NonEmptySet[A]] =
    RefinementProvider.lengthConstraint(_.value.size)
