package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type RatingConstraint     = GreaterEqual[0] & LessEqual[4000]
opaque type Rating <: Int = Int :| RatingConstraint

object Rating extends RefinedTypeOps[Int, RatingConstraint, Rating]:
  def fromString(value: String): Option[Rating] =
    value.toIntOption >>= Rating.option

  extension (self: Rating) inline def toInt: Int = self
