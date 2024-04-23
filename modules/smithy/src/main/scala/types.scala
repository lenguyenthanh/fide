package fide
package types

import cats.syntax.all.*
import fide.spec.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import smithy4s.*

opaque type Natural <: Int :| Positive = Int :| Positive

object Natural extends RefinedTypeOps[Int, Positive, Natural]:
  def fromString(value: String): Either[String, Natural] =
    value.toIntOption.toRight(s"$value is not an int") >>= Natural.either

  extension (self: Natural) def succ: Natural = Natural.applyUnsafe(self + 1)

  given RefinementProvider[PageFormat, String, Natural] =
    Refinement.drivenBy(Natural.fromString, _.toString)

  given RefinementProvider.Simple[smithy.api.Range, Natural] =
    RefinementProvider.rangeConstraint(x => x: Int)

  given RefinementProvider[PageSizeFormat, Int, Natural] =
    Refinement.drivenBy(Natural.either, identity)
