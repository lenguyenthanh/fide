package fide
package domain

object Models:
  enum Order(val value: String):
    case Asc  extends Order("ASC")
    case Desc extends Order("DESC")

  enum SortBy(val value: String):
    case Name     extends SortBy("name")
    case Standard extends SortBy("standard")
    case Rapid    extends SortBy("rapid")
    case Blitz    extends SortBy("blitz")
    case Year     extends SortBy("year")

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
  case class Pagination(limit: Int, offset: Int):
    def next     = copy(offset = offset + limit)
    def nextPage = (offset / limit) + 2

  object Pagination:
    val defaultLimit  = 30
    val firstPage     = 1
    val defaultOffset = 0
    val default       = Pagination(defaultLimit, defaultOffset)

    def apply(limit: Option[Int], page: Option[Int]): Pagination =
      val _limit  = limit.getOrElse(defaultLimit)
      val _offset = (page.getOrElse(firstPage) - 1) * _limit
      Pagination(_limit, _offset)

    def fromPageAndSize(page: Int, size: Int): Pagination =
      val offset = (math.max(firstPage, page) - 1) * size
      Pagination(size, offset)

  case class RatingRange(min: Option[Rating], max: Option[Rating])
  object RatingRange:
    def empty = RatingRange(None, None)

  case class Filter(
      isActive: Option[Boolean],
      standard: RatingRange,
      rapid: RatingRange,
      blitz: RatingRange
  )

  object Filter:
    val default = Filter(None, RatingRange.empty, RatingRange.empty, RatingRange.empty)
