package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type PositiveInt = Int :| Positive

type PageSize = PageSize.T
object PageSize extends RefinedType[Int, Positive]

type PageNumber = PageNumber.T
object PageNumber extends RefinedType[Int, Positive]:

  def fromString(value: String): Either[String, PageNumber] =
    value.toIntOption.toRight(s"$value is not an int") >>= PageNumber.either

  extension (self: PageNumber)
    inline def succ: PageNumber = PageNumber.applyUnsafe(self + 1)
    inline def toInt: Int       = self
    inline def max(other: PageNumber): PageNumber =
      if self > other then self else other

type PlayerId = PlayerId.T
object PlayerId extends RefinedType[Int, Positive]

type BirthYear = BirthYear.T
object BirthYear extends RefinedType[Int, Positive]
