package fide
package db

import cats.syntax.all.*
import fide.domain.*
import fide.domain.Models.*
import fide.types.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import DbCodecs.*

case class TableAliases(
    data: String,
    identity: String
)

object TableAliases:
  val players = TableAliases("p", "p")
  val history = TableAliases("ph", "pi")

object FilterSql:

  private val and: AppliedFragment = sql" AND ".apply(Void)

  def filterFragment(aliases: TableAliases)(filter: PlayerFilter): Option[AppliedFragment] =
    val d = aliases.data
    val i = aliases.identity
    List.concat(
      filter.name.map(nameLikeFragment(i)),
      between(d, "standard", filter.standard),
      between(d, "rapid", filter.rapid),
      between(d, "blitz", filter.blitz),
      filter.isActive.map(filterActive(d)),
      filter.federationId.map(federationIdFragment(d)),
      filter.titles.map(xs => playersByTitles(d, xs.size)(xs, xs)),
      filter.otherTitles.map(xs => playersByOtherTitles(d, xs.size)(xs)),
      filter.gender.map(filterGender(i)),
      between(i, "birth_year", filter.birthYearMin, filter.birthYearMax),
      filter.hasTitle.map(hasTitle(d)),
      filter.hasWomenTitle.map(hasWomenTitle(d)),
      filter.hasOtherTitle.map(hasOtherTitle(d))
    ) match
      case Nil => none
      case xs  => xs.intercalate(and).some

  private def between(alias: String, column: String, range: RatingRange): Option[AppliedFragment] =
    between(alias, column, range.min, range.max)

  private def between[A <: Int](
      alias: String,
      column: String,
      min: Option[A],
      max: Option[A]
  ): Option[AppliedFragment] =
    val col = s"$alias.$column"
    (min, max) match
      case (Some(min), Some(max)) =>
        sql"#$col BETWEEN ${int4} AND ${int4}".apply(min, max).some
      case (Some(min), None) =>
        sql"#$col >= ${int4}".apply(min).some
      case (None, Some(max)) =>
        sql"#$col <= ${int4}".apply(max).some
      case (None, None) => none

  private def filterActive(alias: String): Fragment[Boolean] =
    sql"#${s"$alias.active"} = $bool"

  private def filterGender(alias: String): Fragment[Gender] =
    sql"#${s"$alias.sex"} = $gender"

  private def playersByTitles(alias: String, n: Int): Fragment[(List[Title], List[Title])] =
    val titles = title.values.list(n)
    sql"(#${s"$alias.title"} IN ($titles) OR #${s"$alias.women_title"} IN ($titles))"

  private def hasTitle(alias: String): Boolean => AppliedFragment =
    case true  => sql"#${s"$alias.title"} IS NOT NULL".apply(Void)
    case false => sql"#${s"$alias.title"} IS NULL".apply(Void)

  private def hasWomenTitle(alias: String): Boolean => AppliedFragment =
    case true  => sql"#${s"$alias.women_title"} IS NOT NULL".apply(Void)
    case false => sql"#${s"$alias.women_title"} IS NULL".apply(Void)

  private def hasOtherTitle(alias: String): Boolean => AppliedFragment =
    case true  => sql"COALESCE(cardinality(#${s"$alias.other_titles"}), 0) != 0".apply(Void)
    case false => sql"COALESCE(cardinality(#${s"$alias.other_titles"}), 0) = 0".apply(Void)

  private def playersByOtherTitles(alias: String, n: Int): Fragment[List[OtherTitle]] =
    val otherTitles = otherTitle.values.list(n)
    sql"#${s"$alias.other_titles"} && array[$otherTitles]::other_title[]"

  private def nameLikeFragment(alias: String)(name: String): AppliedFragment =
    sql"#${s"$alias.name"} % $text".apply(name)

  private def federationIdFragment(alias: String)(id: FederationId): AppliedFragment =
    if FederationId.isNone(id) then sql"#${s"$alias.federation_id"} IS NULL".apply(Void)
    else sql"#${s"$alias.federation_id"} = $federationIdCodec".apply(id)
