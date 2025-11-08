package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type PositiveInt = Int :| Positive

type PageSize = PageSize.T
object PageSize extends RefinedSubtype[Int, Positive]

type PageNumber = PageNumber.T
object PageNumber extends RefinedSubtype[Int, Positive]:

  def fromString(value: String): Either[String, PageNumber] =
    value.toIntOption.toRight(s"$value is not an int") >>= PageNumber.either

  extension (self: PageNumber)
    inline def succ: PageNumber                   = PageNumber.applyUnsafe(self.value + 1)
    inline def toInt: Int                         = self.value
    inline def max(other: PageNumber): PageNumber =
      if self.value > other.value then self else other

type PlayerId = PlayerId.T
object PlayerId extends RefinedType[Int, Positive]

type BirthYear = BirthYear.T
object BirthYear extends RefinedSubtype[Int, Positive]
