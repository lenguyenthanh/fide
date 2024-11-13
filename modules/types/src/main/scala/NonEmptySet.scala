package fide.types

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

opaque type NonEmptySet = [A] =>> Set[A] :| MinLength[1]

object NonEmptySet:
  def either[A](set: Set[A]): Either[String, NonEmptySet[A]] =
    set.refineEither[MinLength[1]]

  extension [A](set: NonEmptySet[A]) inline def value: Set[A] = set
