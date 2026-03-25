package fide
package domain
package test

import fide.types.*
import weaver.*

object YearMonthSuite extends SimpleIOSuite:

  pureTest("fromString parses valid yyyy-MM"):
    val result = YearMonth.fromString("2024-01")
    expect(result.isRight) and
      expect(result.toOption.get.year == 2024) and
      expect(result.toOption.get.month == 1)

  pureTest("fromString rejects invalid input"):
    expect(YearMonth.fromString("invalid").isLeft) and
      expect(YearMonth.fromString("2024").isLeft) and
      expect(YearMonth.fromString("").isLeft)

  pureTest("apply normalizes to 1st of month"):
    val ym = YearMonth(java.time.LocalDate.of(2024, 3, 15))
    expect(ym.toLocalDate.getDayOfMonth == 1) and
      expect(ym.month == 3) and
      expect(ym.year == 2024)

  pureTest("apply from year and month"):
    val ym = YearMonth(2024, 6)
    expect(ym.year == 2024) and expect(ym.month == 6)

  pureTest("format produces yyyy-MM"):
    val ym = YearMonth(2024, 1)
    expect(ym.format == "2024-01")

  pureTest("fromLastModified parses RFC 1123 date"):
    val result = YearMonth.fromLastModified("Mon, 01 Jan 2024 00:00:00 GMT")
    expect(result.isDefined) and
      expect(result.get.year == 2024) and
      expect(result.get.month == 1)

  pureTest("fromLastModified returns None for garbage"):
    expect(YearMonth.fromLastModified("garbage").isEmpty) and
      expect(YearMonth.fromLastModified("").isEmpty)
