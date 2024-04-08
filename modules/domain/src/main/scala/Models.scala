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

  // start at 1
  case class Pagination(limit: Int, offset: Int):
    def next     = copy(offset = offset + limit)
    def nextPage = (offset / limit) + 1

  object Pagination:
    val defaultLimit  = 30
    val defaultPage   = 1
    val defaultOffset = 0
    val default       = Pagination(defaultLimit, defaultOffset)

    def apply(limit: Option[Int], page: Option[Int]): Pagination =
      val _limit  = limit.getOrElse(defaultLimit)
      val _offset = (page.getOrElse(defaultPage) - 1) * _limit
      Pagination(_limit, _offset)

    def fromPageAndSize(page: Int, size: Int): Pagination =
      val offset = (math.max(defaultPage, page) - 1) * size
      Pagination(size, offset)
