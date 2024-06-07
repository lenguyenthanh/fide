package fide.spec

import cats.syntax.all.*
import fide.spec.*
import fide.types.*
import smithy4s.*

object providers:
  given RefinementProvider[PageFormat, String, Natural] =
    Refinement.drivenBy(Natural.fromString, _.toString)

  given RefinementProvider.Simple[smithy.api.Range, Natural] =
    RefinementProvider.rangeConstraint(x => x: Int)

  given RefinementProvider[PageSizeFormat, Int, Natural] =
    Refinement.drivenBy(Natural.either, identity)
