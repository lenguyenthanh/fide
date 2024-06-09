package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

opaque type PositiveInt <: Int = Int :| Positive

object PositiveInt extends RefinedTypeOps[Int, Positive, PositiveInt]:
  def fromString(value: String): Either[String, PositiveInt] =
    value.toIntOption.toRight(s"$value is not an int") >>= PositiveInt.either

  val firstNumber: PositiveInt = 1

  extension (self: PositiveInt)
    inline def succ: PositiveInt = PositiveInt.applyUnsafe(self + 1)
    inline def toInt: Int        = self
    inline def max(other: PositiveInt): PositiveInt =
      if self > other then self else other
