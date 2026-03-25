package fide.cli

import fide.types.YearMonth
import org.http4s.Uri

object FideArchiveUrl:

  private val monthAbbrevs = Array(
    "jan",
    "feb",
    "mar",
    "apr",
    "may",
    "jun",
    "jul",
    "aug",
    "sep",
    "oct",
    "nov",
    "dec"
  )

  val categories = List("standard", "rapid", "blitz")

  def forMonth(category: String, ym: YearMonth): Uri =
    val mon  = monthAbbrevs(ym.month - 1)
    val year = f"${ym.year % 100}%02d"
    Uri.unsafeFromString(
      s"https://ratings.fide.com/download/${category}_${mon}${year}frl.zip"
    )

  def allForMonth(ym: YearMonth): List[(String, Uri)] =
    categories.map(cat => cat -> forMonth(cat, ym))
