package fide.spec

import cats.syntax.all.*
import fide.spec.*
import fide.types.{ NonEmptySet, PositiveInt }
import smithy4s.*

object providers:
  given RefinementProvider[PageFormat, String, PositiveInt] =
    Refinement.drivenBy(PositiveInt.fromString, _.toString)

  given RefinementProvider.Simple[smithy.api.Range, PositiveInt] =
    RefinementProvider.rangeConstraint(_.value.toInt)

  given RefinementProvider[PageSizeFormat, Int, PositiveInt] =
    Refinement.drivenBy(PositiveInt.either, _.value)

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
