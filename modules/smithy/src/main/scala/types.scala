package fide
package types

import cats.syntax.all.*
import fide.spec.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import smithy4s.*

type Natural = Int :| Greater[0]

object Natural extends RefinedTypeOps[Int, Positive, Natural]:
  def apply(value: String): Either[String, Natural] =
    value.toIntOption.toRight(s"$value is not an int") >>= Natural.either

  given RefinementProvider[PageFormat, String, Natural] =
    Refinement.drivenBy[PageFormat](Natural.apply, _.toString)
