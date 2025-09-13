package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type RatingConstraint = GreaterEqual[1400] & LessEqual[4000]
type Rating           = Rating.T
object Rating extends RefinedSubtype[Int, RatingConstraint]:
  def fromString(value: String): Option[Rating] =
    value.toIntOption >>= Rating.option

  extension (self: Rating) inline def toInt: Int = self.value
