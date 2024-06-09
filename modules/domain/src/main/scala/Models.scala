package fide
package domain

import fide.types.*
import io.github.iltotore.iron.*

object Models:
  enum Order(val value: String):
    case Asc  extends Order("ASC")
    case Desc extends Order("DESC")

  enum SortBy(val value: String):
    case Name      extends SortBy("name")
    case Standard  extends SortBy("standard")
    case Rapid     extends SortBy("rapid")
    case Blitz     extends SortBy("blitz")
    case BirthYear extends SortBy("birth_year")

  case class Sorting(
      sortBy: SortBy,
      orderBy: Order
  )

  object Sorting:
    def default = Sorting(SortBy.Name, Order.Desc)
    def fromOption(sortBy: Option[SortBy], order: Option[Order]): Sorting =
      val _sortBy = sortBy.getOrElse(Models.SortBy.Name)
      val defaultOrder =
        _sortBy match
          case Models.SortBy.Name => Models.Order.Asc
          case _                  => Models.Order.Desc
      val _order = order.getOrElse(defaultOrder)
      Models.Sorting(_sortBy, _order)

  // start at 1
  case class Pagination(limit: PositiveInt, offset: Int):
    def next     = copy(offset = offset + limit.toInt)
    def nextPage = (offset / limit.toInt) + 2

  object Pagination:
    val defaultLimit: PositiveInt = PositiveInt(30)
    val firstPage: PositiveInt    = PositiveInt(1)
    val defaultOffset         = 0
    val default               = Pagination(defaultLimit, defaultOffset)

    def fromPageAndSize(page: PositiveInt, size: PositiveInt): Pagination =
      val offset = page.toInt * size.toInt
      Pagination(size, offset)

  case class RatingRange(min: Option[Rating], max: Option[Rating])
  object RatingRange:
    def empty = RatingRange(None, None)

  case class PlayerFilter(
      isActive: Option[Boolean],
      standard: RatingRange,
      rapid: RatingRange,
      blitz: RatingRange,
      federationId: Option[FederationId]
  )

  object PlayerFilter:
    val default = PlayerFilter(None, RatingRange.empty, RatingRange.empty, RatingRange.empty, None)

  enum PostgresStatus:
    case Ok
    case Unreachable
