package fide
package types

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import smithy4s.*

type Numeric = ForAll[Digit] DescribedAs "Should be numeric"

type PageNumber = String :| Numeric
object PageNumber:
  def apply(value: String): Either[String, PageNumber] =
    value.refineEither[Numeric]

  def apply(value: Int): PageNumber =
    value.toString.refineUnsafe

  given RefinementProvider[fide.spec.NumericString, String, PageNumber] =
    Refinement.drivenBy[fide.spec.NumericString](
      PageNumber.apply,
      identity
    )

  extension (value: PageNumber) def int: Int = value.toInt
