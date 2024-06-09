package fide.spec

import cats.syntax.all.*
import fide.types.{ NonEmptySet, PageNumber, PageSize }
import smithy4s.*

object providers:
  given RefinementProvider[PageFormat, String, PageNumber] =
    Refinement.drivenBy(PageNumber.fromString, _.toString)

  given RefinementProvider.Simple[smithy.api.Range, fide.types.PageSize] =
    RefinementProvider.rangeConstraint(_.toInt)

  given RefinementProvider[PageSizeFormat, Int, fide.types.PageSize] =
    Refinement.drivenBy(PageSize.either, _.toInt)

  given RefinementProvider[RatingFormat, Int, fide.types.Rating] =
    Refinement.drivenBy(fide.types.Rating.either, _.value)

  given RefinementProvider[FederationIdFormat, String, fide.types.FederationId] =
    Refinement.drivenBy(fide.types.FederationId.either, _.value)

  given [A]: RefinementProvider[NonEmptySetFormat, Set[A], NonEmptySet[A]] =
    Refinement.drivenBy[NonEmptySetFormat](
      NonEmptySet.either,
      (b: NonEmptySet[A]) => b.value
    )

  given RefinementProvider.Simple[smithy.api.Length, fide.types.NonEmptySet[fide.spec.PlayerId]] =
    RefinementProvider.lengthConstraint(x => x.value.size)
