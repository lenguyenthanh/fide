package fide.types

import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type NonEmpty            = MinLength[1]
opaque type FederationId = String :| NonEmpty

object FederationId:
  private val rtc: RefinedTypeOps[String, NonEmpty, FederationId] =
    new RefinedTypeOps[String, NonEmpty, FederationId]() {}

  inline def apply(value: String :| NonEmpty): FederationId = rtc.applyUnsafe(value.toUpperCase)

  def either(value: String): Either[String, FederationId] =
    rtc.either(value.toUpperCase)

  def option(value: String): Option[FederationId] =
    either(value).toOption

  extension (str: FederationId)
    inline def toStr: String                     = str
    inline def value: IronType[String, NonEmpty] = rtc.value(str)
