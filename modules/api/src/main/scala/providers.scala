package fide.spec

import cats.syntax.all.*
import fide.spec.*
import fide.types.*
import smithy4s.*

object providers:
  given RefinementProvider[PageFormat, String, Natural] =
    Refinement.drivenBy(Natural.fromString, _.toString)

  given RefinementProvider.Simple[smithy.api.Range, Natural] =
    RefinementProvider.rangeConstraint(x => x: Int)

  given RefinementProvider[PageSizeFormat, Int, Natural] =
    Refinement.drivenBy(Natural.either, identity)

  given [A]: RefinementProvider[NonEmptySetFormat, Set[A], NonEmptySet[A]] =
    Refinement.drivenBy[NonEmptySetFormat](
      NonEmptySet.either,
      (b: NonEmptySet[A]) => b.value
    )

  given RefinementProvider.Simple[smithy.api.Length, fide.types.NonEmptySet[fide.spec.PlayerId]] =
    RefinementProvider.lengthConstraint(x => x.value.size)
