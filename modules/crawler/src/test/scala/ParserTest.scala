package fide
package crawler
package test

import cats.effect.IO
import cats.syntax.all.*
import fide.domain.{ Gender, OtherTitle, Title }
import org.typelevel.log4cats.Logger
import weaver.*

object ParserTest extends SimpleIOSuite:

  given Logger[IO] = org.typelevel.log4cats.noop.NoOpLogger[IO]

  test("player with other titles"):
    parse(
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             1638  0   20 1932  0   20 1926  0   20 1974      "
    ).map(_.get.otherTitles)
      .map(expect.same(_, List(OtherTitle.FI, OtherTitle.LSI)))

  test("active flag"):
    val lines = List(
      "8605360        A La, Teng Hua                                               CHN F                                1949  0   40                           1993  wi  ",
      "25021044       Aagney L., Narasimhan                                        IND M                                1606  0   20 1565  0   20 1567  0   20 2000  i   ",
      "                                                                       ", // whitespaces only line
      "29976634       Aafrin, S F Aja                                              SRI F                                                                       2012  w   ",
      "1478800        Aagaard, Christian                                           DEN M                                                                       1999      "
    )
    lines
      .traverseFilter(parse)
      .map(_.map(_.active))
      .map(expect.same(_, List(false, false, true, true)))

  test("rating constraints"):
    val lines = List(
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             900  0   20 1932  0   20 1926  0   20 1974      ",
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             4002  0   20 1932  0   20 1926  0   20 1974      ",
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             2700  0   20 1932  0   20 1926  0   20 1974      "
    )
    lines
      .traverseFilter(parse)
      .map(_.mapFilter(_.standard))
      .map(expect.same(_, List(2700)))

  test("sanitize name"):
    IO:
      expect.same(Downloader.sanitizeName("Aafrin, S F Aja"), "Aafrin S F Aja".some) &&
      expect.same(Downloader.sanitizeName("Aafrin, S F Aja"), "Aafrin S F Aja".some) &&
      expect.same(Downloader.sanitizeName(",alfaifi,Wael ali qasim"), "alfaifi Wael ali qasim".some) &&
      expect.same(Downloader.sanitizeName("Amar,, Kahtan"), "Amar Kahtan".some) &&
      expect.same(Downloader.sanitizeName("-,-"), none)

  test("player with no federation"):
    parse(
      "1478800        Aagaard, Christian                                               M                                                                       1999      "
    ).map: result =>
      expect(result.get.federationId.isEmpty)

  test("player with NON federation is excluded"):
    parse(
      "1478800        Aagaard, Christian                                           NON M                                                                       1999      "
    ).map: result =>
      expect(result.get.federationId.isEmpty)

  test("player with title and women title"):
    parse(
      "10001492       Ojok, Patrick                                                UGA F   GM   WGM                     1638      20 1932      20 1926      20 1974          "
    ).map: result =>
      val p = result.get
      expect(p.title.contains(Title.GM)) and
        expect(p.womenTitle.contains(Title.WGM))

  test("empty line is skipped"):
    parse("").map(result => expect(result.isEmpty))

  test("whitespace-only line is skipped"):
    parse("                                                                       ").map(result =>
      expect(result.isEmpty)
    )

  test("birth year below 1000 is excluded"):
    parse(
      "10001492       Ojok, Patrick                                                UGA M                                1638  0   20 1932  0   20 1926  0   20 0999      "
    ).map: result =>
      expect(result.get.birthYear.isEmpty)

  test("birth year above current year is excluded"):
    parse(
      "10001492       Ojok, Patrick                                                UGA M                                1638  0   20 1932  0   20 1926  0   20 9999      "
    ).map: result =>
      expect(result.get.birthYear.isEmpty)

  test("gender is parsed"):
    parse(
      "10001492       Ojok, Patrick                                                UGA F                                1638  0   20 1932  0   20 1926  0   20 1974      "
    ).map: result =>
      expect(result.get.gender.contains(Gender.Female))

  private def parse(s: String) = Downloader.parseLine(s).map(_.map(_._1))
