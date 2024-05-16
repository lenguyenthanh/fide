package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

opaque type Natural <: Int :| Positive = Int :| Positive

object Natural extends RefinedTypeOps[Int, Positive, Natural]:
  def fromString(value: String): Either[String, Natural] =
    value.toIntOption.toRight(s"$value is not an int") >>= Natural.either

  extension (self: Natural) def succ: Natural = Natural.applyUnsafe(self + 1)
