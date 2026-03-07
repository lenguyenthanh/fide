package fide
package crawler
package test

import cats.effect.IO
import cats.syntax.all.*
import fide.domain.{ Month, NewPlayerHistory, OtherTitle }
import org.typelevel.log4cats.Logger
import weaver.*

object ParserTest extends SimpleIOSuite:

  given Logger[IO] = org.typelevel.log4cats.noop.NoOpLogger[IO]

  val testMonth: Short = Month.current

  test("player with other titles"):
    parseHistory(
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
      .traverseFilter(parseHistory)
      .map(_.map(_.active))
      .map(expect.same(_, List(false, false, true, true)))

  test("rating constraints"):
    val lines = List(
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             900  0   20 1932  0   20 1926  0   20 1974      ",
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             4002  0   20 1932  0   20 1926  0   20 1974      ",
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             2700  0   20 1932  0   20 1926  0   20 1974      "
    )
    lines
      .traverseFilter(parseHistory)
      .map(_.mapFilter(_.standard))
      .map(expect.same(_, List(2700)))

  test("sanitize name"):
    IO:
      expect.same(Downloader.sanitizeName("Aafrin, S F Aja"), "Aafrin S F Aja".some) &&
      expect.same(Downloader.sanitizeName("Aafrin, S F Aja"), "Aafrin S F Aja".some) &&
      expect.same(Downloader.sanitizeName(",alfaifi,Wael ali qasim"), "alfaifi Wael ali qasim".some) &&
      expect.same(Downloader.sanitizeName("Amar,, Kahtan"), "Amar Kahtan".some) &&
      expect.same(Downloader.sanitizeName("-,-"), none)

  // Parse a line and extract just the history (snapshot) part
  private def parseHistory(s: String): IO[Option[NewPlayerHistory]] =
    Downloader.parseLine(s, testMonth).map(_.map(_._2))
