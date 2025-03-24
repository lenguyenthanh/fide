package fide.types

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type NonEmpty     = MinLength[1]
type FederationId = FederationId.T
object FederationId extends RefinedType[String, NonEmpty]
