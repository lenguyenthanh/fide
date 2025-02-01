package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type PositiveInt = Int :| Positive

opaque type PageSize <: Int = PositiveInt
object PageSize extends RefinedTypeOps[Int, Positive, PageSize]

opaque type PageNumber <: Int = PositiveInt
object PageNumber extends RefinedTypeOps[Int, Positive, PageNumber]:

  def fromString(value: String): Either[String, PageNumber] =
    value.toIntOption.toRight(s"$value is not an int") >>= PageNumber.either

  extension (self: PageNumber)
    inline def succ: PageNumber = PageNumber.applyUnsafe(self + 1)
    inline def toInt: Int       = self
    inline def max(other: PageNumber): PageNumber =
      if self > other then self else other

opaque type PlayerId <: Int = PositiveInt
object PlayerId extends RefinedTypeOps[Int, Positive, PlayerId]

opaque type BirthYear <: Int = PositiveInt
object BirthYear extends RefinedTypeOps[Int, Positive, BirthYear]
