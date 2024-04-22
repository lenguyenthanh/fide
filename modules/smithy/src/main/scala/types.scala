package fide
package types

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import smithy4s.*

type Numeric = Match["^[1-9][0-9]*$"] DescribedAs "Should be natural number"

type NumericString = String :| Numeric
object NumericString:
  def apply(value: String): Either[String, NumericString] =
    value.refineEither[Numeric]

  def apply(value: Int): NumericString =
    value.toString.refineUnsafe

  given RefinementProvider[fide.spec.PageFormat, String, NumericString] =
    Refinement.drivenBy[fide.spec.PageFormat](
      NumericString.apply,
      identity
    )

extension (value: NumericString) inline def intValue: Int = value.toInt
