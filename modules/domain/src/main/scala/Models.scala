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

  case class Pagination(page: PageNumber, size: PageSize):
    def next: Pagination     = copy(page = page.succ)
    def nextPage: PageNumber = page.succ
    def offset: Int          = (page - 1) * size

  case class RatingRange(min: Option[Rating], max: Option[Rating])
  object RatingRange:
    def empty = RatingRange(None, None)

  case class PlayerFilter(
      name: Option[String],
      isActive: Option[Boolean],
      standard: RatingRange,
      rapid: RatingRange,
      blitz: RatingRange,
      federationId: Option[FederationId],
      titles: Option[List[Title]],
      otherTitles: Option[List[OtherTitle]],
      gender: Option[Gender],
      birthYearMin: Option[BirthYear],
      birthYearMax: Option[BirthYear],
      hasTitle: Option[Boolean],
      hasWomenTitle: Option[Boolean],
      hasOtherTitle: Option[Boolean]
  )

  object PlayerFilter:
    val default =
      PlayerFilter(
        None,
        None,
        RatingRange.empty,
        RatingRange.empty,
        RatingRange.empty,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None
      )

  enum PostgresStatus:
    case Ok
    case Unreachable
