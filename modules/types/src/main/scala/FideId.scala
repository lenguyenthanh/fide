package fide.types

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type FideId = FideId.T
object FideId extends RefinedType[String, NonEmpty]
