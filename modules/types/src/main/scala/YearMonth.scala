package fide.types

import java.time.LocalDate
import java.time.format.DateTimeFormatter

opaque type YearMonth = LocalDate

object YearMonth:

  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

  def apply(date: LocalDate): YearMonth =
    date.withDayOfMonth(1)

  def apply(year: Int, month: Int): YearMonth =
    LocalDate.of(year, month, 1)

  def fromString(value: String): Either[String, YearMonth] =
    try Right(LocalDate.parse(s"$value-01"))
    catch case _: Exception => Left(s"Invalid year-month: $value")

  def fromLastModified(lastModified: String): Option[YearMonth] =
    try
      val parsed = java.time.ZonedDateTime.parse(
        lastModified,
        DateTimeFormatter.RFC_1123_DATE_TIME
      )
      Some(apply(parsed.toLocalDate))
    catch case _: Exception => None

  given Ordering[YearMonth] = new Ordering[YearMonth]:
    override def compare(x: YearMonth, y: YearMonth): Int =
      if x.year == y.year then x.month - y.month
      else x.year - y.year

  extension (self: YearMonth)
    inline def value: LocalDate       = self
    inline def toLocalDate: LocalDate = self
    def format: String                = self.format(formatter)
    inline def year: Int              = self.getYear
    inline def month: Int             = self.getMonthValue
